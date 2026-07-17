package rocks.minestom.worldgen.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.KelpFeature;
import net.minecraft.world.level.levelgen.feature.SeagrassFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.ProbabilityFeatureConfiguration;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.UnitModifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.feature.configurations.ProbabilityConfiguration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the ocean vegetation feature ports (seagrass, tall seagrass,
 * kelp) against real vanilla 26.2 code in process: both sides run over an
 * identical synthetic ocean floor (sand/gravel/magma_block floor, a water
 * column whose depth varies per column including one-block-deep shallows)
 * from identical random sequences, and every block write plus the total draw
 * count must match over 300+ runs per feature.
 *
 * <p>The synthetic floor varies the water depth and floor block by column so
 * a single run exercises: the short vs tall seagrass branch, tall seagrass
 * failing because the block above the floor is not water (one-block-deep
 * shallow), and kelp columns that run out of water mid-growth (the loop's
 * {@code break} branch) at many different heights.
 */
final class SeagrassKelpABTest {

    private static final int RUNS = 320;
    private static final int SEA_LEVEL = 63;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 96;
    private static final int SIZE = 40;
    private static final int BASE_FLOOR_Y = 40;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();
    }

    @Test
    void seagrassShort() {
        runSeagrass("seagrass_short", 0.3F);
    }

    @Test
    void seagrassMid() {
        runSeagrass("seagrass_mid", 0.6F);
    }

    @Test
    void seagrassTall() {
        runSeagrass("seagrass_tall", 0.8F);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void kelp() {
        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;

        for (var run = 0; run < RUNS; run++) {
            var seed = 24680L + run * 65537L;
            var originX = 3000 - run * 19;
            var originZ = -2200 + run * 37;
            var floorBlockIndex = run % FLOOR_BLOCKS.length;

            var handler = new SyntheticOcean(floorBlockIndex, seed);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    SeagrassKelpABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaContext = new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                    Optional.empty(), level, null, vanillaRandom,
                    new BlockPos(originX, BASE_FLOOR_Y + 1, originZ), NoneFeatureConfiguration.INSTANCE);
            new KelpFeature(NoneFeatureConfiguration.CODEC).place(vanillaContext);

            var vanillaSets = collectVanilla(handler);

            var ourWorld = ourWorld(originX, originZ, floorBlockIndex, seed);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration, GenerationUnitAdapter>(
                    ourWorld.adapter, ourRandom, new BlockVec(originX, BASE_FLOOR_Y + 1, originZ),
                    new rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration(),
                    seed, MIN_Y, MAX_Y, SEA_LEVEL);
            new rocks.minestom.worldgen.feature.KelpFeature().place(context);

            var ourSets = collectOurs(ourWorld);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 8) {
                    System.out.println("kelp run=" + run + " DRAWS vanilla=" + vanillaRandom.count
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
                    if (printed < 40) {
                        System.out.println("kelp run=" + run + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println("kelp: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "kelp block write mismatches");
        assertEquals(0, drawMismatchRuns, "kelp runs with diverging draw counts");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runSeagrass(String name, float probability) {
        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;

        for (var run = 0; run < RUNS; run++) {
            var seed = 13579L + run * 65537L;
            var originX = 4000 - run * 23;
            var originZ = -1500 + run * 41;
            var floorBlockIndex = run % FLOOR_BLOCKS.length;

            var handler = new SyntheticOcean(floorBlockIndex, seed);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    SeagrassKelpABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaConfig = new ProbabilityFeatureConfiguration(probability);
            var vanillaContext = new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                    Optional.empty(), level, null, vanillaRandom,
                    new BlockPos(originX, BASE_FLOOR_Y + 1, originZ), vanillaConfig);
            new SeagrassFeature(ProbabilityFeatureConfiguration.CODEC).place((net.minecraft.world.level.levelgen.feature.FeaturePlaceContext) vanillaContext);

            var vanillaSets = collectVanilla(handler);

            var ourWorld = ourWorld(originX, originZ, floorBlockIndex, seed);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<ProbabilityConfiguration, GenerationUnitAdapter>(
                    ourWorld.adapter, ourRandom, new BlockVec(originX, BASE_FLOOR_Y + 1, originZ),
                    new ProbabilityConfiguration(probability), seed, MIN_Y, MAX_Y, SEA_LEVEL);
            new rocks.minestom.worldgen.feature.SeagrassFeature().place(context);

            var ourSets = collectOurs(ourWorld);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 8) {
                    System.out.println(name + " run=" + run + " DRAWS vanilla=" + vanillaRandom.count
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
                    if (printed < 40) {
                        System.out.println(name + " run=" + run + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println(name + ": runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, name + " block write mismatches");
        assertEquals(0, drawMismatchRuns, name + " runs with diverging draw counts");
    }

    // --- shared synthetic ocean floor ---------------------------------------

    private static final String[] FLOOR_BLOCKS = {
            "minecraft:sand", "minecraft:gravel", "minecraft:magma_block", "minecraft:dirt", "minecraft:stone"
    };

    /**
     * Deterministic per-column water depth, 1..14, including frequent
     * one-block-deep shallows: the {@code minecraft:water} column above the
     * floor runs from {@code floorY + 1} to {@code floorY + depth}.
     */
    private static int waterDepth(int x, int z, long seed) {
        var h = (long) x * 341873128712L + (long) z * 132897987541L + seed;
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        var bucket = Math.floorMod(h, 16L);
        // Weight bucket 0 heavily toward the one-block shallow case.
        if (bucket < 4) {
            return 1;
        }
        return 1 + (int) Math.floorMod(h >>> 8, 13L);
    }

    private static TreeMap<String, String> collectVanilla(SyntheticOcean handler) {
        var vanillaSets = new TreeMap<String, String>();
        for (var entry : handler.overlay.entrySet()) {
            vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                    FeatureABCompare.canonical(entry.getValue()));
        }
        return vanillaSets;
    }

    private static TreeMap<String, String> collectOurs(OurWorld ourWorld) {
        var ourSets = new TreeMap<String, String>();
        var height = MAX_Y - MIN_Y + 1;
        for (var localX = 0; localX < SIZE; localX++) {
            for (var localZ = 0; localZ < SIZE; localZ++) {
                var base = (localX * SIZE + localZ) * height;
                for (var yIndex = 0; yIndex < height; yIndex++) {
                    var index = base + yIndex;
                    var before = ourWorld.before[index];
                    var after = ourWorld.blocks[index];
                    if (!java.util.Objects.equals(before, after)) {
                        var worldX = ourWorld.startX + localX;
                        var worldY = MIN_Y + yIndex;
                        var worldZ = ourWorld.startZ + localZ;
                        ourSets.put(worldX + "," + worldY + "," + worldZ, FeatureABCompare.canonical(after));
                    }
                }
            }
        }
        return ourSets;
    }

    private record OurWorld(GenerationUnitAdapter adapter, Block[] blocks, Block[] before, int startX, int startZ) {
    }

    private static OurWorld ourWorld(int originX, int originZ, int floorBlockIndex, long seed) {
        var startX = originX - SIZE / 2;
        var startZ = originZ - SIZE / 2;
        var height = MAX_Y - MIN_Y + 1;
        var blocks = new Block[SIZE * SIZE * height];
        var floorId = FLOOR_BLOCKS[floorBlockIndex];
        var floorBlock = FeatureABCompare.ourState(floorId);
        for (var localX = 0; localX < SIZE; localX++) {
            for (var localZ = 0; localZ < SIZE; localZ++) {
                var worldX = startX + localX;
                var worldZ = startZ + localZ;
                var depth = waterDepth(worldX, worldZ, seed);
                var base = (localX * SIZE + localZ) * height;
                blocks[base + (BASE_FLOOR_Y - MIN_Y)] = floorBlock;
                for (var y = BASE_FLOOR_Y + 1; y <= BASE_FLOOR_Y + depth; y++) {
                    blocks[base + (y - MIN_Y)] = Block.WATER;
                }
            }
        }

        var before = blocks.clone();
        var unit = fakeUnit(startX, startZ, MIN_Y, SIZE, height);
        var adapter = new GenerationUnitAdapter(unit, startX, startZ, SIZE, SIZE, MIN_Y, blocks, height, null);
        return new OurWorld(adapter, blocks, before, startX, startZ);
    }

    private static GenerationUnit fakeUnit(int startX, int startZ, int minY, int size, int height) {
        var handler = (InvocationHandler) (proxy, method, args) -> {
            return switch (method.getName()) {
                case "absoluteStart" -> new BlockVec(startX, minY, startZ);
                case "size" -> new BlockVec(size, height, size);
                case "modifier" -> Proxy.newProxyInstance(
                        SeagrassKelpABTest.class.getClassLoader(),
                        new Class<?>[]{UnitModifier.class},
                        (InvocationHandler) (p, m, a) -> switch (m.getName()) {
                            case "setRelative" -> null;
                            case "toString" -> "fake-unit-modifier";
                            case "hashCode" -> 0;
                            case "equals" -> p == (a != null && a.length > 0 ? a[0] : null);
                            default -> null;
                        });
                case "toString" -> "fake-unit";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException(method.getName());
            };
        };
        return (GenerationUnit) Proxy.newProxyInstance(
                SeagrassKelpABTest.class.getClassLoader(), new Class<?>[]{GenerationUnit.class}, handler);
    }

    /** Vanilla-side synthetic ocean, mirroring {@link #ourWorld}. */
    static final class SyntheticOcean implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final int floorBlockIndex;
        final long seed;

        SyntheticOcean(int floorBlockIndex, long seed) {
            this.floorBlockIndex = floorBlockIndex;
            this.seed = seed;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            if (pos.getY() == BASE_FLOOR_Y) {
                return FeatureABCompare.vanillaState(FLOOR_BLOCKS[this.floorBlockIndex]);
            }

            var depth = waterDepth(pos.getX(), pos.getZ(), this.seed);
            if (pos.getY() > BASE_FLOOR_Y && pos.getY() <= BASE_FLOOR_Y + depth) {
                return FeatureABCompare.vanillaState("minecraft:water");
            }

            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            return switch (method.getName()) {
                case "getBlockState" -> this.state((BlockPos) args[0]);
                case "getFluidState" -> this.state((BlockPos) args[0]).getFluidState();
                case "isStateAtPosition" -> ((java.util.function.Predicate<BlockState>) args[1]).test(this.state((BlockPos) args[0]));
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    yield true;
                }
                case "getHeight" -> {
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    for (var y = MAX_Y; y >= MIN_Y; y--) {
                        var state = this.state(new BlockPos(x, y, z));
                        if (state.getFluidState().isEmpty() && !state.isAir()) {
                            yield y + 1;
                        }
                    }
                    yield MIN_Y;
                }
                case "isOutsideBuildHeight" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    yield y < MIN_Y || y > MAX_Y;
                }
                case "getMinY" -> MIN_Y;
                case "getMaxY" -> MAX_Y;
                case "getSeaLevel" -> SEA_LEVEL;
                case "getBlockEntity" -> args != null && args.length > 1 ? java.util.Optional.empty() : null;
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> null;
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> true;
                case "getRandom" -> net.minecraft.util.RandomSource.create(0L);
                case "getChunk" -> null;
                case "toString" -> "seagrass-kelp-ab-level";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("unexpected: " + method.getName());
            };
        }
    }
}
