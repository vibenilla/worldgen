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
import rocks.minestom.worldgen.feature.BlockColumnFeature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.configurations.BlockColumnConfiguration;
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
 * A/B compares the {@code minecraft:block_column} port (used by the cave vine
 * configured features) against real vanilla 26.2 code in process: both sides
 * run over identical synthetic lush-cave ceilings (a stone or mossy-block
 * ceiling with an air cavity of varying height below, some cavities shallow
 * enough to force truncation) from identical random sequences, and every
 * block write plus the total draw count must match.
 */
final class CaveVineABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 400;

    private static Object lookup;
    private static net.minecraft.world.level.chunk.ChunkGenerator generator;
    private static BlockTagManager blockTags;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        lookup = registries;

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

        blockTags = new BlockTagManager(ROOT);
    }

    @Test
    void caveVine() throws Exception {
        runCase("cave_vine", false);
    }

    @Test
    void caveVineInMoss() throws Exception {
        runCase("cave_vine_in_moss", true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runCase(String configName, boolean moss) throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/" + configName + ".json"))).getAsJsonObject();

        var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();

        var feature = new BlockColumnFeature();
        var config = BlockColumnConfiguration.fromJson(configJson.get("config"), blockTags);

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 9000L + run;
            var seed = 13579L + run * 65537L;
            var x = 500 - run * 23;
            var z = -800 + run * 17;
            var ceilY = 90;
            // Cavity height cycles through short (forces truncation across the
            // full biased-to-bottom / uniform ranges used by both configs) and
            // tall (never truncates) values.
            var cavityHeight = 1 + (run % 32);
            var originY = ceilY - 1;
            var floorY = ceilY - cavityHeight;

            // vanilla
            var handler = new CeilingLevel(salt, ceilY, floorY, moss);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    CaveVineABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, new BlockPos(x, originY, z));

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            // ours
            var ourWorld = new OurCeilingWorld(salt, ceilY, floorY, moss);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext(ourWorld, ourRandom, new BlockVec(x, originY, z), config, seed, -64, 319, 63);
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
                    System.out.println(configName + " run=" + run + " cavityHeight=" + cavityHeight
                            + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
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
                    if (printed < 32) {
                        System.out.println(configName + " run=" + run + " cavityHeight=" + cavityHeight + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println(configName + ": runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, configName + " block write mismatches");
        assertEquals(0, drawMismatchRuns, configName + " runs with diverging draw counts");
    }

    // --- synthetic ceiling world ---------------------------------------------

    /** Canonical block id at a position, null for air. */
    static String baseCeilingState(int x, int y, int z, int ceilY, int floorY, boolean moss) {
        if (y < -64 || y > 319) {
            return null;
        }

        if (y >= ceilY) {
            return moss ? "minecraft:moss_block" : "minecraft:stone";
        }

        if (y <= floorY) {
            return "minecraft:stone";
        }

        return null;
    }

    static final class CeilingLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;
        final int ceilY;
        final int floorY;
        final boolean moss;

        CeilingLevel(long salt, int ceilY, int floorY, boolean moss) {
            this.salt = salt;
            this.ceilY = ceilY;
            this.floorY = floorY;
            this.moss = moss;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = baseCeilingState(pos.getX(), pos.getY(), pos.getZ(), this.ceilY, this.floorY, this.moss);
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
                    return 63;
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
                case "toString" -> {
                    return "cave-vine-ab-level";
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

    static final class OurCeilingWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;
        final int ceilY;
        final int floorY;
        final boolean moss;

        OurCeilingWorld(long salt, int ceilY, int floorY, boolean moss) {
            this.salt = salt;
            this.ceilY = ceilY;
            this.floorY = floorY;
            this.moss = moss;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseCeilingState(x, y, z, this.ceilY, this.floorY, this.moss);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
