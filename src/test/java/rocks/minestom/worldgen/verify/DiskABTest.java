package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.configurations.DiskConfiguration;

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
 * A/B compares the {@code minecraft:disk} feature port against real vanilla
 * 26.2 code in process: both sides run over an identical synthetic
 * riverbed/lakebed world (dirt/sand/gravel/clay/mud floors under water
 * columns of varying depth, with some exposed dry land) from identical
 * random sequences, and every block write plus the total draw count must
 * match, for all four real datapack disk configs.
 */
final class DiskABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 300;

    private static net.minecraft.core.HolderLookup.Provider lookup;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();
        lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
    }

    @Test
    void diskSand() throws Exception {
        runCase("disk_sand");
    }

    @Test
    void diskGravel() throws Exception {
        runCase("disk_gravel");
    }

    @Test
    void diskClay() throws Exception {
        runCase("disk_clay");
    }

    @Test
    void diskGrass() throws Exception {
        runCase("disk_grass");
    }

    @SuppressWarnings("unchecked")
    private static void runCase(String configName) throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/" + configName + ".json"))).getAsJsonObject();

        var vanillaConfigured = (net.minecraft.world.level.levelgen.feature.ConfiguredFeature<
                net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration, ?>) (Object) lookup
                .lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();
        var vanillaConfig = vanillaConfigured.config();
        // Post-processing marking (Feature#markAboveForPostProcessing) only
        // schedules chunk bookkeeping for later liquid settling; it writes no
        // block state and is irrelevant to the block-for-block disk output
        // this test verifies, so it is stubbed out to avoid needing a real
        // ChunkAccess from the synthetic level proxy.
        var vanillaFeature = new net.minecraft.world.level.levelgen.feature.DiskFeature(
                net.minecraft.world.level.levelgen.feature.configurations.DiskConfiguration.CODEC) {
            @Override
            protected void markAboveForPostProcessing(WorldGenLevel level, BlockPos placePos) {
            }
        };

        var ourConfig = DiskConfiguration.fromJson(configJson.get("config"));
        var ourFeature = new rocks.minestom.worldgen.feature.DiskFeature();

        // Offsets from the riverbed floor: below the floor, at the floor
        // itself, at several depths inside the water column, and above the
        // water surface into open air, so the target/state-provider rules
        // that look above and below the origin all get exercised.
        var originOffsets = new int[]{-2, -1, 0, 1, 2, 3, 4, 6};

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        var totalAttempts = 0;
        for (var run = 0; run < RUNS; run++) {
            for (var offsetIndex = 0; offsetIndex < originOffsets.length; offsetIndex++) {
                totalAttempts++;
                var salt = 9000L + run;
                var seed = 13579L + run * 104729L + offsetIndex * 733L;
                var x = -1500 + run * 29;
                var z = 2500 - run * 41;
                var floor = floorHeight(x, z, salt);
                var originY = floor + originOffsets[offsetIndex];

                // vanilla
                var handler = new RiverbedLevel(salt);
                var level = (WorldGenLevel) Proxy.newProxyInstance(
                        DiskABTest.class.getClassLoader(),
                        new Class<?>[]{WorldGenLevel.class},
                        handler);
                var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
                vanillaFeature.place(vanillaConfig, level, null, vanillaRandom, new BlockPos(x, originY, z));

                var vanillaSets = new TreeMap<String, String>();
                for (var entry : handler.overlay.entrySet()) {
                    vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                            FeatureABCompare.canonical(entry.getValue()));
                }

                // ours
                var ourWorld = new OurRiverbedWorld(salt);
                var ourRandom = new FeatureABCompare.CountingOurRandom(
                        new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
                var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, originY, z), ourConfig, seed, -64, 319, 63);
                ourFeature.place(context);

                var ourSets = new TreeMap<String, String>();
                for (var entry : ourWorld.overlay.entrySet()) {
                    ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                            FeatureABCompare.canonical(entry.getValue()));
                }

                vanillaSetTotal += vanillaSets.size();
                if (vanillaRandom.count != ourRandom.count) {
                    drawMismatchRuns++;
                    if (printed < 4) {
                        System.out.println(configName + " run=" + run + " offset=" + originOffsets[offsetIndex]
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
                            System.out.println(configName + " run=" + run + " offset=" + originOffsets[offsetIndex]
                                    + " at " + key + " vanilla=" + vanilla + " ours=" + ours);
                            printed++;
                        }
                    }
                }
            }
        }

        System.out.println(configName + ": runs=" + totalAttempts + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, configName + " block write mismatches");
        assertEquals(0, drawMismatchRuns, configName + " runs with diverging draw counts");
    }

    // --- synthetic riverbed/lakebed world -----------------------------------

    /** Top solid floor block, with gentle rolling variation. */
    static int floorHeight(int x, int z, long salt) {
        return 60 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 8);
    }

    /**
     * Water depth above the floor: 0 means dry exposed land, otherwise the
     * column is flooded up to {@code floor + depth}.
     */
    static int waterDepth(int x, int z, long salt) {
        var value = FeatureABCompare.lattice(x, z, salt * 8 + 2, 10);
        if (value < 3) {
            return 0;
        }
        return 1 + (value % 5);
    }

    /** Floor material: dirt/grass_block/clay/mud/sand/gravel/stone, matching the four disk targets. */
    static String floorMaterial(int x, int z, long salt) {
        var value = FeatureABCompare.lattice(x, z, salt * 8 + 3, 100);
        if (value < 16) {
            return "minecraft:dirt";
        }
        if (value < 32) {
            return "minecraft:grass_block";
        }
        if (value < 48) {
            return "minecraft:clay";
        }
        if (value < 64) {
            return "minecraft:mud";
        }
        if (value < 76) {
            return "minecraft:sand";
        }
        if (value < 88) {
            return "minecraft:gravel";
        }
        return "minecraft:stone";
    }

    /** Occasional underhang: air immediately below the floor, testing "air below" rules. */
    static boolean hollowBelow(int x, int z, long salt) {
        return FeatureABCompare.lattice(x, z, salt * 8 + 4, 10) >= 8;
    }

    /** Occasional boulder resting directly on the floor, testing "solid above" rules. */
    static boolean boulderAbove(int x, int z, long salt) {
        return FeatureABCompare.lattice(x, z, salt * 8 + 5, 10) >= 9;
    }

    /**
     * Occasional waterlogged, non-solid block (a sea pickle) resting on the
     * floor: this is submerged (its fluid state is water) without being the
     * literal water block, which is what makes it a meaningful case for the
     * "matching_fluids water" rule used by disk_grass.
     */
    static boolean waterloggedPickleAbove(int x, int z, long salt) {
        return FeatureABCompare.lattice(x, z, salt * 8 + 6, 10) >= 8;
    }

    /** Sentinel canonical id for a waterlogged sea pickle, translated per side. */
    static final String WATERLOGGED_SEA_PICKLE = "minecraft:sea_pickle$waterlogged";

    /** Canonical block id at a position, null for air. */
    static String baseRiverbedState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319) {
            return null;
        }

        var floor = floorHeight(x, z, salt);
        if (y < floor) {
            if (y == floor - 1 && hollowBelow(x, z, salt)) {
                return null;
            }
            return "minecraft:stone";
        }

        if (y == floor) {
            return floorMaterial(x, z, salt);
        }

        if (y == floor + 1 && boulderAbove(x, z, salt)) {
            return "minecraft:stone";
        }

        if (y == floor + 1 && waterloggedPickleAbove(x, z, salt)) {
            return WATERLOGGED_SEA_PICKLE;
        }

        var depth = waterDepth(x, z, salt);
        if (depth > 0 && y <= floor + depth) {
            return "minecraft:water";
        }

        return null;
    }

    // --- vanilla level proxy -------------------------------------------------

    static final class RiverbedLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        RiverbedLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = baseRiverbedState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            if (base == null) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            if (WATERLOGGED_SEA_PICKLE.equals(base)) {
                return net.minecraft.world.level.block.Blocks.SEA_PICKLE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true);
            }
            return FeatureABCompare.vanillaState(base);
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
                    return "disk-ab-level";
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

    static final class OurRiverbedWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurRiverbedWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseRiverbedState(x, y, z, this.salt);
            if (base == null) {
                return Block.AIR;
            }
            if (WATERLOGGED_SEA_PICKLE.equals(base)) {
                return Block.SEA_PICKLE.withProperty("waterlogged", "true");
            }
            return FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
