package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.EndGatewayFeature;
import rocks.minestom.worldgen.feature.EndIslandFeature;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.VoidStartPlatformFeature;
import rocks.minestom.worldgen.feature.configurations.EndGatewayConfiguration;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the end feature ports (end island, end gateway, void start
 * platform) against real vanilla 26.2 code in process: both sides run over
 * an identical synthetic end world (an end stone island surface with void
 * below and around it) from identical random sequences, and every block
 * write plus the total draw count must match.
 */
final class EndFeaturesABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 250;

    private static Object lookup;
    private static net.minecraft.world.level.chunk.ChunkGenerator generator;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        lookup = registries;

        var biomes = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var biomeSource = net.minecraft.world.level.biome.TheEndBiomeSource.create(biomes);
        var noiseSettings = registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:end")));
        generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseSettings);
    }

    @Test
    void endIsland() throws Exception {
        runConfiguredCase("end_island", RUNS, run -> {
            var x = -1500 + run * 53;
            var z = 2200 - run * 41;
            var y = 40 + (run % 20);
            return new BlockPos(x, y, z);
        });
    }

    @Test
    void endGatewayDelayed() throws Exception {
        runConfiguredCase("end_gateway_delayed", RUNS, run -> {
            var x = 100 + run * 17;
            var z = -300 + run * 23;
            var y = 60 + (run % 10);
            return new BlockPos(x, y, z);
        });
    }

    @Test
    void endGatewayReturn() throws Exception {
        runConfiguredCase("end_gateway_return", RUNS, run -> {
            var x = -800 + run * 31;
            var z = 500 - run * 19;
            var y = 55 + (run % 15);
            return new BlockPos(x, y, z);
        });
    }

    @Test
    void voidStartPlatform() throws Exception {
        // Deterministic feature: no random draws, so a handful of origins
        // across every relevant chunk offset is sufficient to exercise every
        // branch (skip-chunk, edge chunk, center chunk containing the
        // platform's own origin).
        var origins = new BlockPos[]{
                new BlockPos(0, 0, 0),
                new BlockPos(8, 0, 8),
                new BlockPos(15, 0, 15),
                new BlockPos(16, 0, 16),
                new BlockPos(-16, 0, -16),
                new BlockPos(31, 0, 31),
                new BlockPos(32, 0, 32),
                new BlockPos(-1, 0, -1),
                new BlockPos(100, 4, -100),
                new BlockPos(0, -10, 0),
        };
        runConfiguredCaseAtOrigins("void_start_platform", origins);
    }

    @FunctionalInterface
    private interface OriginPicker {
        BlockPos pick(int run);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runConfiguredCase(String configName, int runs, OriginPicker originPicker) throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/" + configName + ".json"))).getAsJsonObject();
        var typeName = configJson.get("type").getAsString();

        var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();

        Feature feature;
        rocks.minestom.worldgen.feature.FeatureConfiguration config;
        switch (typeName) {
            case "minecraft:end_island" -> {
                feature = new EndIslandFeature();
                config = new NoneFeatureConfiguration();
            }
            case "minecraft:end_gateway" -> {
                feature = new EndGatewayFeature();
                config = EndGatewayConfiguration.fromJson(configJson.get("config"));
            }
            default -> throw new IllegalStateException("unexpected feature type " + typeName);
        }

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < runs; run++) {
            var salt = 6000L + run;
            var seed = 13579L + run * 92821L;
            var origin = originPicker.pick(run);

            // vanilla
            var handler = new EndLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    EndFeaturesABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, origin);

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            // ours
            var ourWorld = new OurEndWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var originVec = new BlockVec(origin.getX(), origin.getY(), origin.getZ());
            var context = new FeaturePlaceContext(ourWorld, ourRandom, originVec, config, seed, -64, 319, 63);
            feature.place(context);

            var ourSets = new TreeMap<String, String>();
            for (var entry : ourWorld.overlay.entrySet()) {
                ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println(configName + " run=" + run + " DRAWS vanilla=" + vanillaRandom.count
                            + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            var keys = new TreeSet<String>();
            keys.addAll(vanillaSets.keySet());
            keys.addAll(ourSets.keySet());
            for (var key : keys) {
                var vanilla = vanillaSets.get(key);
                var ours = ourSets.get(key);
                if (!java.util.Objects.equals(vanilla, ours)) {
                    mismatches++;
                    if (printed < 16) {
                        System.out.println(configName + " run=" + run + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println(configName + ": runs=" + runs + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, configName + " block write mismatches");
        assertEquals(0, drawMismatchRuns, configName + " runs with diverging draw counts");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runConfiguredCaseAtOrigins(String configName, BlockPos[] origins) throws Exception {
        var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();

        Feature feature = new VoidStartPlatformFeature();
        rocks.minestom.worldgen.feature.FeatureConfiguration config = new NoneFeatureConfiguration();

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < origins.length; run++) {
            var salt = 7000L + run;
            var seed = 24680L + run * 10007L;
            var origin = origins[run];

            var handler = new EndLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    EndFeaturesABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, origin);

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            var ourWorld = new OurEndWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var originVec = new BlockVec(origin.getX(), origin.getY(), origin.getZ());
            var context = new FeaturePlaceContext(ourWorld, ourRandom, originVec, config, seed, -64, 319, 63);
            feature.place(context);

            var ourSets = new TreeMap<String, String>();
            for (var entry : ourWorld.overlay.entrySet()) {
                ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                System.out.println(configName + " run=" + run + " DRAWS vanilla=" + vanillaRandom.count
                        + " ours=" + ourRandom.count);
            }

            var keys = new TreeSet<String>();
            keys.addAll(vanillaSets.keySet());
            keys.addAll(ourSets.keySet());
            for (var key : keys) {
                var vanilla = vanillaSets.get(key);
                var ours = ourSets.get(key);
                if (!java.util.Objects.equals(vanilla, ours)) {
                    mismatches++;
                    if (printed < 32) {
                        System.out.println(configName + " run=" + run + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println(configName + ": origins=" + origins.length + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, configName + " block write mismatches");
        assertEquals(0, drawMismatchRuns, configName + " runs with diverging draw counts");
    }

    // --- synthetic end world -------------------------------------------------

    /** Canonical block id at a position, null for air (the end's void). */
    static String baseEndState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319) {
            return null;
        }

        var islandRadius = 60 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 40);
        var distanceSquared = (long) x * x + (long) z * z;
        if (distanceSquared > (long) islandRadius * islandRadius) {
            return null;
        }

        var surface = 50 + FeatureABCompare.lattice(x, z, salt * 8 + 2, 10);
        if (y > surface) {
            return null;
        }

        return "minecraft:end_stone";
    }

    // --- vanilla level proxy -------------------------------------------------

    static final class EndLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        EndLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = baseEndState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : FeatureABCompare.vanillaState(base);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "getFluidState" -> {
                    return this.state((BlockPos) args[0]).getFluidState();
                }
                case "isStateAtPosition" -> {
                    return ((java.util.function.Predicate<BlockState>) args[1]).test(this.state((BlockPos) args[0]));
                }
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "removeBlock", "destroyBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "isOutsideBuildHeight" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    return y < -64 || y > 319;
                }
                case "getMinY" -> {
                    return -64;
                }
                case "getMaxY" -> {
                    return 319;
                }
                case "getHeight" -> {
                    return 384;
                }
                case "getSeaLevel" -> {
                    return 0;
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? Optional.empty() : null;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "getChunk" -> {
                    return null;
                }
                case "toString" -> {
                    return "end-ab-level";
                }
                case "hashCode" -> {
                    return 0;
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> throw new UnsupportedOperationException("unexpected: " + method.getName());
            }
        }
    }

    // --- our side --------------------------------------------------------------

    static final class OurEndWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurEndWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseEndState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
