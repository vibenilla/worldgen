package rocks.minestom.worldgen.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.CountConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.LayerConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the small feature ports (desert well, sea pickle, bamboo, fill
 * layer, bonus chest) against real vanilla 26.2 code in process: both sides
 * run over identical synthetic worlds tailored to each feature (sand desert
 * surface, warm ocean floor with a water column, jungle grass/podzol surface,
 * a flat world) from identical random sequences, and every block write plus
 * the total draw count must match.
 */
final class SmallFeaturesABTest {

    private static final int RUNS = 250;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();
    }

    // --- desert well ---------------------------------------------------------

    @Test
    void desertWell() {
        var vanillaFeature = new net.minecraft.world.level.levelgen.feature.DesertWellFeature(NoneFeatureConfiguration.CODEC);
        var ourFeature = new rocks.minestom.worldgen.feature.DesertWellFeature();

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 6000L + run;
            var seed = 13579L + run * 65537L;
            var x = 300 + run * 29;
            var z = -300 - run * 41;
            var sandY = 60 + (run % 5);
            var origin = new BlockPos(x, sandY, z);

            var handler = new DesertWellLevel(salt, sandY);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    SmallFeaturesABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaContext = new FeaturePlaceContext<>(Optional.empty(), level, null, vanillaRandom, origin, new NoneFeatureConfiguration());
            vanillaFeature.place(vanillaContext);

            var vanillaSets = collectVanilla(handler.overlay);

            var ourWorld = new DesertWellWorld(salt, sandY);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var ourContext = new rocks.minestom.worldgen.feature.FeaturePlaceContext<
                    rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration, DesertWellWorld>(
                    ourWorld, ourRandom, new BlockVec(x, sandY, z),
                    new rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration(), seed, -64, 319, 63);
            ourFeature.place(ourContext);

            var ourSets = collectOurs(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("desert_well run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            mismatches += compare("desert_well", run, vanillaSets, ourSets, printed);
        }

        System.out.println("desert_well: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "desert_well block write mismatches");
        assertEquals(0, drawMismatchRuns, "desert_well runs with diverging draw counts");
    }

    static final class DesertWellLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;
        final int sandY;

        DesertWellLevel(long salt, int sandY) {
            this.salt = salt;
            this.sandY = sandY;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            if (pos.getY() < this.sandY) {
                return FeatureABCompare.vanillaState("minecraft:stone");
            }

            if (pos.getY() == this.sandY) {
                return FeatureABCompare.vanillaState("minecraft:sand");
            }

            return Blocks.AIR.defaultBlockState();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "isStateAtPosition" -> {
                    return ((java.util.function.Predicate<BlockState>) args[1]).test(this.state((BlockPos) args[0]));
                }
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? Optional.empty() : null;
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
                    return "desert-well-ab-level";
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

    static final class DesertWellWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;
        final int sandY;

        DesertWellWorld(long salt, int sandY) {
            this.salt = salt;
            this.sandY = sandY;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            if (y < this.sandY) {
                return FeatureABCompare.ourState("minecraft:stone");
            }

            if (y == this.sandY) {
                return FeatureABCompare.ourState("minecraft:sand");
            }

            return Block.AIR;
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- sea pickle ------------------------------------------------------------

    @Test
    void seaPickle() {
        var vanillaFeature = new net.minecraft.world.level.levelgen.feature.SeaPickleFeature(CountConfiguration.CODEC);
        var ourFeature = new rocks.minestom.worldgen.feature.SeaPickleFeature();
        var vanillaConfig = new CountConfiguration(20);
        var ourConfig = new rocks.minestom.worldgen.feature.configurations.CountConfiguration(
                new rocks.minestom.worldgen.feature.valueproviders.ConstantIntProvider(20));

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 7000L + run;
            var seed = 24680L + run * 104729L;
            var x = -1500 + run * 33;
            var z = 2500 - run * 19;
            var floorY = seaFloorHeight(x, z, salt);
            var origin = new BlockPos(x, floorY + 1, z);

            var handler = new SeaPickleLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    SmallFeaturesABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaContext = new FeaturePlaceContext<>(Optional.empty(), level, null, vanillaRandom, origin, vanillaConfig);
            vanillaFeature.place(vanillaContext);

            var vanillaSets = collectVanilla(handler.overlay);

            var ourWorld = new SeaPickleWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var ourContext = new rocks.minestom.worldgen.feature.FeaturePlaceContext<
                    rocks.minestom.worldgen.feature.configurations.CountConfiguration, SeaPickleWorld>(
                    ourWorld, ourRandom, new BlockVec(x, floorY + 1, z), ourConfig, seed, -64, 319, 63);
            ourFeature.place(ourContext);

            var ourSets = collectOurs(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("sea_pickle run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            mismatches += compare("sea_pickle", run, vanillaSets, ourSets, printed);
        }

        System.out.println("sea_pickle: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "sea_pickle block write mismatches");
        assertEquals(0, drawMismatchRuns, "sea_pickle runs with diverging draw counts");
    }

    static int seaFloorHeight(int x, int z, long salt) {
        return 50 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 10);
    }

    static boolean isBeach(int x, int z, long salt) {
        return FeatureABCompare.lattice(x, z, salt * 8 + 9, 20) >= 18;
    }

    static String seaPickleBaseState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319) {
            return null;
        }

        var floor = seaFloorHeight(x, z, salt);
        if (isBeach(x, z, salt)) {
            return y <= floor + 3 ? (y < 0 ? "minecraft:deepslate" : "minecraft:stone") : null;
        }

        if (y <= floor) {
            return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
        }

        if (y <= floor + 6) {
            return "minecraft:water";
        }

        return null;
    }

    static final class SeaPickleLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        SeaPickleLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = seaPickleBaseState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? Blocks.AIR.defaultBlockState() : FeatureABCompare.vanillaState(base);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "isStateAtPosition" -> {
                    return ((java.util.function.Predicate<BlockState>) args[1]).test(this.state((BlockPos) args[0]));
                }
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "getHeight" -> {
                    var type = (Heightmap.Types) args[0];
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    return type == Heightmap.Types.OCEAN_FLOOR ? seaFloorHeight(x, z, this.salt) + 1 : 0;
                }
                case "getMinY" -> {
                    return -64;
                }
                case "getMaxY" -> {
                    return 319;
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
                    return "sea-pickle-ab-level";
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

    static final class SeaPickleWorld implements Block.Getter, Block.Setter,
            rocks.minestom.worldgen.feature.SeaPickleFeature.OceanFloor {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        SeaPickleWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public int oceanFloorHeight(int x, int z) {
            return seaFloorHeight(x, z, this.salt) + 1;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = seaPickleBaseState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- bamboo ------------------------------------------------------------------

    @Test
    void bamboo() {
        for (var probability : new float[]{0.2F, 0.0F, 1.0F}) {
            runBamboo(probability);
        }
    }

    private void runBamboo(float probability) {
        var vanillaFeature = new net.minecraft.world.level.levelgen.feature.BambooFeature(ProbabilityFeatureConfiguration.CODEC);
        var ourFeature = new rocks.minestom.worldgen.feature.BambooFeature();
        var vanillaConfig = new ProbabilityFeatureConfiguration(probability);
        var ourConfig = new rocks.minestom.worldgen.feature.configurations.ProbabilityConfiguration(probability);

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 8000L + run;
            var seed = 987654321L + run * 7919L;
            var x = -900 + run * 23;
            var z = 700 - run * 17;
            var surfaceY = bambooSurfaceHeight(x, z, salt);
            var origin = new BlockPos(x, surfaceY + 1, z);

            var handler = new BambooLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    SmallFeaturesABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaContext = new FeaturePlaceContext<>(Optional.empty(), level, null, vanillaRandom, origin, vanillaConfig);
            vanillaFeature.place(vanillaContext);

            var vanillaSets = collectVanilla(handler.overlay);

            var ourWorld = new BambooWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var ourContext = new rocks.minestom.worldgen.feature.FeaturePlaceContext<
                    rocks.minestom.worldgen.feature.configurations.ProbabilityConfiguration, BambooWorld>(
                    ourWorld, ourRandom, new BlockVec(x, surfaceY + 1, z), ourConfig, seed, -64, 319, 63);
            ourFeature.place(ourContext);

            var ourSets = collectOurs(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("bamboo[" + probability + "] run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            mismatches += compare("bamboo[" + probability + "]", run, vanillaSets, ourSets, printed);
        }

        System.out.println("bamboo[" + probability + "]: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "bamboo block write mismatches (probability=" + probability + ")");
        assertEquals(0, drawMismatchRuns, "bamboo runs with diverging draw counts (probability=" + probability + ")");
    }

    static int bambooSurfaceHeight(int x, int z, long salt) {
        return 64 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 8);
    }

    static String bambooSurfaceMaterial(int x, int z, long salt) {
        var value = FeatureABCompare.lattice(x, z, salt * 8 + 2, 10);
        if (value >= 9) {
            return "minecraft:stone";
        }
        if (value >= 5) {
            return "minecraft:podzol";
        }
        return "minecraft:grass_block";
    }

    static String bambooBaseState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319) {
            return null;
        }

        var surface = bambooSurfaceHeight(x, z, salt);
        if (y < surface) {
            return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
        }

        if (y == surface) {
            return bambooSurfaceMaterial(x, z, salt);
        }

        return null;
    }

    static final class BambooLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        BambooLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = bambooBaseState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? Blocks.AIR.defaultBlockState() : FeatureABCompare.vanillaState(base);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "isStateAtPosition" -> {
                    return ((java.util.function.Predicate<BlockState>) args[1]).test(this.state((BlockPos) args[0]));
                }
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "getHeight" -> {
                    var type = (Heightmap.Types) args[0];
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    return type == Heightmap.Types.WORLD_SURFACE ? bambooSurfaceHeight(x, z, this.salt) + 1 : 0;
                }
                case "getMinY" -> {
                    return -64;
                }
                case "getMaxY" -> {
                    return 319;
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
                    return "bamboo-ab-level";
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

    static final class BambooWorld implements Block.Getter, Block.Setter,
            rocks.minestom.worldgen.feature.BambooFeature.WorldSurface {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        BambooWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public int worldSurfaceHeight(int x, int z) {
            return bambooSurfaceHeight(x, z, this.salt) + 1;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = bambooBaseState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- fill layer ------------------------------------------------------------

    @Test
    void fillLayer() {
        var vanillaFeature = new net.minecraft.world.level.levelgen.feature.FillLayerFeature(LayerConfiguration.CODEC);
        var ourFeature = new rocks.minestom.worldgen.feature.FillLayerFeature();

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 9000L + run;
            var seed = 555555L + run * 4001L;
            var chunkX = run * 3;
            var chunkZ = -run * 5;
            var originX = chunkX << 4;
            var originZ = chunkZ << 4;
            var height = 5 + (run % 40);
            var origin = new BlockPos(originX, -64 + height, originZ);
            var vanillaState = FeatureABCompare.vanillaState("minecraft:sandstone");
            var ourState = FeatureABCompare.ourState("minecraft:sandstone");
            var vanillaConfig = new LayerConfiguration(height, vanillaState);
            var ourConfig = new rocks.minestom.worldgen.feature.configurations.LayerConfiguration(height, ourState);

            var handler = new FillLayerLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    SmallFeaturesABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaContext = new FeaturePlaceContext<>(Optional.empty(), level, null, vanillaRandom, origin, vanillaConfig);
            vanillaFeature.place(vanillaContext);

            var vanillaSets = collectVanilla(handler.overlay);

            var ourWorld = new FillLayerWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var ourContext = new rocks.minestom.worldgen.feature.FeaturePlaceContext<
                    rocks.minestom.worldgen.feature.configurations.LayerConfiguration, FillLayerWorld>(
                    ourWorld, ourRandom, new BlockVec(originX, -64 + height, originZ), ourConfig, seed, -64, 319, 63);
            ourFeature.place(ourContext);

            var ourSets = collectOurs(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("fill_layer run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            mismatches += compare("fill_layer", run, vanillaSets, ourSets, printed);
        }

        System.out.println("fill_layer: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "fill_layer block write mismatches");
        assertEquals(0, drawMismatchRuns, "fill_layer runs with diverging draw counts");
    }

    static boolean fillLayerOccupied(int x, int z, long salt) {
        return FeatureABCompare.lattice(x, z, salt * 8 + 1, 10) >= 7;
    }

    static final class FillLayerLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        FillLayerLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            return fillLayerOccupied(pos.getX(), pos.getZ(), this.salt)
                    ? FeatureABCompare.vanillaState("minecraft:stone") : Blocks.AIR.defaultBlockState();
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "getMinY" -> {
                    return -64;
                }
                case "getMaxY" -> {
                    return 319;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "toString" -> {
                    return "fill-layer-ab-level";
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

    static final class FillLayerWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        FillLayerWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            return fillLayerOccupied(x, z, this.salt) ? FeatureABCompare.ourState("minecraft:stone") : Block.AIR;
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- bonus chest -------------------------------------------------------------

    @Test
    void bonusChest() {
        var vanillaFeature = new net.minecraft.world.level.levelgen.feature.BonusChestFeature(NoneFeatureConfiguration.CODEC);
        var ourFeature = new rocks.minestom.worldgen.feature.BonusChestFeature();

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 10000L + run;
            var seed = 918273645L + run * 1299709L;
            var chunkX = run * 4;
            var chunkZ = -run * 6;
            var x = (chunkX << 4) + 8;
            var z = (chunkZ << 4) + 8;
            var origin = new BlockPos(x, bonusChestGroundHeight(x, z, salt) + 1, z);

            var handler = new BonusChestLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    SmallFeaturesABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaContext = new FeaturePlaceContext<>(Optional.empty(), level, null, vanillaRandom, origin, new NoneFeatureConfiguration());
            vanillaFeature.place(vanillaContext);

            var vanillaSets = collectVanilla(handler.overlay);

            var ourWorld = new BonusChestWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var ourContext = new rocks.minestom.worldgen.feature.FeaturePlaceContext<
                    rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration, BonusChestWorld>(
                    ourWorld, ourRandom, new BlockVec(x, bonusChestGroundHeight(x, z, salt) + 1, z),
                    new rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration(), seed, -64, 319, 63);
            ourFeature.place(ourContext);

            var ourSets = collectOurs(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("bonus_chest run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            mismatches += compare("bonus_chest", run, vanillaSets, ourSets, printed);
        }

        System.out.println("bonus_chest: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "bonus_chest block write mismatches");
        assertEquals(0, drawMismatchRuns, "bonus_chest runs with diverging draw counts");
    }

    static int bonusChestGroundHeight(int x, int z, long salt) {
        return 64 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 6);
    }

    static boolean bonusChestHasDecoration(int x, int z, long salt) {
        return FeatureABCompare.lattice(x, z, salt * 8 + 2, 10) >= 7;
    }

    static String bonusChestBaseState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319) {
            return null;
        }

        var ground = bonusChestGroundHeight(x, z, salt);
        if (y < ground) {
            return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
        }

        if (y == ground) {
            return "minecraft:grass_block";
        }

        if (y == ground + 1 && bonusChestHasDecoration(x, z, salt)) {
            return "minecraft:short_grass";
        }

        return null;
    }

    static final class BonusChestLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        BonusChestLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = bonusChestBaseState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? Blocks.AIR.defaultBlockState() : FeatureABCompare.vanillaState(base);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "getHeight" -> {
                    var type = (Heightmap.Types) args[0];
                    var pos = (BlockPos) args[1];
                    return type == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                            ? bonusChestGroundHeight(pos.getX(), pos.getZ(), this.salt) + 1 : 0;
                }
                case "getHeightmapPos" -> {
                    var type = (Heightmap.Types) args[0];
                    var pos = (BlockPos) args[1];
                    var height = type == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES
                            ? bonusChestGroundHeight(pos.getX(), pos.getZ(), this.salt) + 1 : 0;
                    return new BlockPos(pos.getX(), height, pos.getZ());
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? Optional.empty() : null;
                }
                case "getMinY" -> {
                    return -64;
                }
                case "getMaxY" -> {
                    return 319;
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
                    return "bonus-chest-ab-level";
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

    static final class BonusChestWorld implements Block.Getter, Block.Setter,
            rocks.minestom.worldgen.feature.BonusChestFeature.MotionBlockingNoLeaves {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        BonusChestWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public int motionBlockingNoLeavesHeight(int x, int z) {
            return bonusChestGroundHeight(x, z, this.salt) + 1;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = bonusChestBaseState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- shared helpers --------------------------------------------------------

    private static TreeMap<String, String> collectVanilla(Map<BlockPos, BlockState> overlay) {
        var result = new TreeMap<String, String>();
        for (var entry : overlay.entrySet()) {
            result.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                    FeatureABCompare.canonical(entry.getValue()));
        }
        return result;
    }

    private static TreeMap<String, String> collectOurs(Map<BlockVec, Block> overlay) {
        var result = new TreeMap<String, String>();
        for (var entry : overlay.entrySet()) {
            result.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                    FeatureABCompare.canonical(entry.getValue()));
        }
        return result;
    }

    private static long compare(String name, int run, TreeMap<String, String> vanillaSets, TreeMap<String, String> ourSets, int printed) {
        var mismatches = 0L;
        var keys = new TreeSet<String>();
        keys.addAll(vanillaSets.keySet());
        keys.addAll(ourSets.keySet());
        for (var key : keys) {
            var vanilla = vanillaSets.get(key);
            var ours = ourSets.get(key);
            if (!java.util.Objects.equals(vanilla, ours)) {
                mismatches++;
                if (printed < 24) {
                    System.out.println(name + " run=" + run + " at " + key + " vanilla=" + vanilla + " ours=" + ours);
                }
            }
        }
        return mismatches;
    }
}
