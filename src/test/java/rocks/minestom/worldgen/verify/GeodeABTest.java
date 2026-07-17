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
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.GeodeFeature;
import rocks.minestom.worldgen.feature.configurations.GeodeConfiguration;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the {@code minecraft:geode} port against real vanilla 26.2
 * code in process: both sides carve an identical amethyst geode into an
 * identical synthetic stone/cave world (the same one used by
 * {@link FeatureABCompare}) from identical random sequences, and every block
 * write plus the total draw count must match.
 */
final class GeodeABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 300;

    private static net.minecraft.world.level.chunk.ChunkGenerator generator;
    private static net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?> vanillaConfigured;
    private static GeodeConfiguration ourConfig;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var presets = registries.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);
        var noiseSettings = registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseSettings);

        vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:amethyst_geode")))
                .value();

        var blockTags = new BlockTagManager(ROOT);
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/amethyst_geode.json"))).getAsJsonObject();
        ourConfig = GeodeConfiguration.fromJson(configJson.get("config"), blockTags);
    }

    @Test
    void geode() {
        var feature = new GeodeFeature();
        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;

        for (var run = 0; run < RUNS; run++) {
            var salt = 9000L + run;
            var seed = 135790L + run * 65537L;
            var x = -3000 + run * 53;
            var y = -40 + (run % 80);
            var z = 2500 - run * 41;
            var origin = new BlockPos(x, y, z);

            // vanilla
            var handler = new GeodeLevel(salt, seed);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    GeodeABTest.class.getClassLoader(),
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
            var ourWorld = new OurGeodeWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(
                    ourWorld, ourRandom, new BlockVec(x, y, z), ourConfig, seed, -64, 319, 63);
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
                    System.out.println("geode run=" + run + " DRAWS vanilla=" + vanillaRandom.count
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
                    if (printed < 24) {
                        System.out.println("geode run=" + run + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println("geode: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "geode block write mismatches");
        assertEquals(0, drawMismatchRuns, "geode runs with diverging draw counts");
    }

    // --- vanilla level proxy -------------------------------------------------

    static final class GeodeLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;
        final long seed;

        GeodeLevel(long salt, long seed) {
            this.salt = salt;
            this.seed = seed;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = FeatureABCompare.baseState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
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
                case "isFluidAtPosition" -> {
                    return ((java.util.function.Predicate<net.minecraft.world.level.material.FluidState>) args[1])
                            .test(this.state((BlockPos) args[0]).getFluidState());
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
                    return 63;
                }
                case "getSeed" -> {
                    return this.seed;
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? java.util.Optional.empty() : null;
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
                    return "geode-ab-level";
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

    static final class OurGeodeWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurGeodeWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = FeatureABCompare.baseState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
