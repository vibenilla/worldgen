package rocks.minestom.worldgen.structure.scattered;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece;
import net.minecraft.world.level.levelgen.structure.structures.JungleTemplePiece;
import net.minecraft.world.level.levelgen.structure.structures.SwampHutPiece;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.UnitModifier;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the desert pyramid, jungle temple, swamp hut and buried
 * treasure piece ports against real vanilla 26.2 code in process: both sides
 * run the same piece construction and postProcess over an identical
 * synthetic flat floor from identical random sequences, and every block
 * write is compared. Loot table NBT is out of scope on both sides (bare
 * chest/dispenser blocks only); the vanilla side never reaches the NBT
 * calls because {@code getBlockEntity} is stubbed out.
 *
 * <p>Known deviations (see {@link DesertPyramidPiece#placeCollapsedRoof}
 * (vanilla) / {@link rocks.minestom.worldgen.structure.scattered.DesertPyramidPiece}
 * javadoc): the desert pyramid's collapsed-roof tile pattern and cellar
 * sand/sandstone variant bit are drawn from vanilla's live per-chunk
 * {@code level.getRandom()}, which this port cannot reproduce; both sides
 * are excluded from the block comparison for those specific tiles, while
 * every other block (including the collapse point and chest placements)
 * is compared exactly.
 */
final class ScatteredStructuresABTest {
    private static final int MIN_Y = -32;
    private static final int MAX_Y = 48;
    private static final int FLOOR_Y = 0;

    @org.junit.jupiter.api.BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void desertPyramid() {
        for (var seed = 0; seed < 12; seed++) {
            runDesertPyramid(1000L + seed * 7919L, seed * 16, seed * -16);
        }
    }

    @Test
    void jungleTemple() {
        for (var seed = 0; seed < 12; seed++) {
            runJungleTemple(2000L + seed * 7919L, seed * 16, seed * -16);
        }
    }

    @Test
    void swampHut() {
        for (var seed = 0; seed < 12; seed++) {
            runSwampHut(3000L + seed * 7919L, seed * 16, seed * -16);
        }
    }

    @Test
    void buriedTreasure() {
        for (var seed = 0; seed < 12; seed++) {
            runBuriedTreasure(4000L + seed * 7919L, seed * 16, seed * -16);
        }
    }

    // --- desert pyramid ------------------------------------------------------

    private static void runDesertPyramid(long seed, int chunkMinX, int chunkMinZ) {
        var contextRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        contextRandom.setLargeFeatureSeed(seed, chunkMinX >> 4, chunkMinZ >> 4);
        var vanillaPiece = new DesertPyramidPiece(contextRandom, chunkMinX, chunkMinZ);

        var ourContextRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                new rocks.minestom.worldgen.random.LegacyRandomSource(0L));
        ourContextRandom.setLargeFeatureSeed(seed, chunkMinX >> 4, chunkMinZ >> 4);
        var ourPiece = new rocks.minestom.worldgen.structure.scattered.DesertPyramidPiece(
                ourContextRandom, chunkMinX, chunkMinZ);

        assertBoundsMatch(vanillaPiece.getBoundingBox(), ourPiece.boundingBox());

        var handler = new VanillaLevel(seed, "minecraft:sandstone", "minecraft:sand");
        var level = proxyLevel(handler);
        var postProcessRandom = new WorldgenRandom(new LegacyRandomSource(seed ^ 0xD35EL));
        vanillaPiece.postProcess(level, null, null, postProcessRandom, vanillaChunkBB(), null, BlockPos.ZERO);

        var ourLevel = ourLevel(chunkMinX, chunkMinZ, Block.SANDSTONE, Block.SAND);
        var ourRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                new rocks.minestom.worldgen.random.LegacyRandomSource(seed ^ 0xD35EL));
        var ourChunkBB = ourChunkBB(chunkMinX, chunkMinZ);
        ourPiece.postProcess(ourLevel.level(), ourRandom, ourChunkBB, seed);

        // The cellar sand/sandstone variant tile is drawn from vanilla's live
        // level.getRandom(), which this port cannot reproduce (see the
        // addCellarStairs javadoc); exclude those two tiles from comparison.
        // Structure-level afterPlace (suspicious sand shuffle, collapsed
        // roof) is vanilla's Structure.afterPlace, not the piece's
        // postProcess, and is out of scope for this piece-level comparison.
        var excluded = new TreeSet<String>();
        excluded.add(key(vanillaPiece, 15, -1, 17));
        excluded.add(key(vanillaPiece, 16, -1, 17));
        // addCellarRoom's placeCollapsedRoof grid (x 14..18, y 0 local, z 11..15
        // local) draws its per-tile sand/sandstone choice from the same
        // unreproducible level.getRandom() stream.
        for (var x = 14; x <= 18; x++) {
            for (var z = 11; z <= 15; z++) {
                excluded.add(key(vanillaPiece, x, 0, z));
            }
        }

        compareBlocks("desertPyramid seed=" + seed, handler, ourLevel, chunkMinX - 8, chunkMinZ - 8,
                chunkMinX + 28, chunkMinZ + 28, excluded);
    }

    private static String key(DesertPyramidPiece piece, int x, int y, int z) {
        try {
            var method = net.minecraft.world.level.levelgen.structure.StructurePiece.class
                    .getDeclaredMethod("getWorldPos", int.class, int.class, int.class);
            method.setAccessible(true);
            var pos = (BlockPos) method.invoke(piece, x, y, z);
            return key(pos.getX(), pos.getY(), pos.getZ());
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    // --- jungle temple ---------------------------------------------------------

    private static void runJungleTemple(long seed, int chunkMinX, int chunkMinZ) {
        var contextRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        contextRandom.setLargeFeatureSeed(seed, chunkMinX >> 4, chunkMinZ >> 4);
        var vanillaPiece = new JungleTemplePiece(contextRandom, chunkMinX, chunkMinZ);

        var ourContextRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                new rocks.minestom.worldgen.random.LegacyRandomSource(0L));
        ourContextRandom.setLargeFeatureSeed(seed, chunkMinX >> 4, chunkMinZ >> 4);
        var ourPiece = new rocks.minestom.worldgen.structure.scattered.JungleTemplePiece(
                ourContextRandom, chunkMinX, chunkMinZ);

        assertBoundsMatch(vanillaPiece.getBoundingBox(), ourPiece.boundingBox());

        var handler = new VanillaLevel(seed, "minecraft:stone", "minecraft:dirt");
        var level = proxyLevel(handler);
        var postProcessRandom = new WorldgenRandom(new LegacyRandomSource(seed ^ 0xA17L));
        vanillaPiece.postProcess(level, null, null, postProcessRandom, vanillaChunkBB(), null, BlockPos.ZERO);

        var ourLevel = ourLevel(chunkMinX, chunkMinZ, Block.STONE, Block.DIRT);
        var ourRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                new rocks.minestom.worldgen.random.LegacyRandomSource(seed ^ 0xA17L));
        ourPiece.postProcess(ourLevel.level(), ourRandom, ourChunkBB(chunkMinX, chunkMinZ));

        compareBlocks("jungleTemple seed=" + seed, handler, ourLevel, chunkMinX - 4, chunkMinZ - 4,
                chunkMinX + 16, chunkMinZ + 19, java.util.Set.of());
    }

    // --- swamp hut -----------------------------------------------------------

    private static void runSwampHut(long seed, int chunkMinX, int chunkMinZ) {
        var contextRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        contextRandom.setLargeFeatureSeed(seed, chunkMinX >> 4, chunkMinZ >> 4);
        var vanillaPiece = new SwampHutPiece(contextRandom, chunkMinX, chunkMinZ);

        var ourContextRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                new rocks.minestom.worldgen.random.LegacyRandomSource(0L));
        ourContextRandom.setLargeFeatureSeed(seed, chunkMinX >> 4, chunkMinZ >> 4);
        var ourPiece = new rocks.minestom.worldgen.structure.scattered.SwampHutPiece(
                ourContextRandom, chunkMinX, chunkMinZ);

        assertBoundsMatch(vanillaPiece.getBoundingBox(), ourPiece.boundingBox());

        var handler = new VanillaLevel(seed, "minecraft:dirt", "minecraft:water");
        var level = proxyLevel(handler);
        vanillaPiece.postProcess(level, null, null, RandomSource.create(0L), vanillaChunkBB(), null, BlockPos.ZERO);

        var ourLevel = ourLevel(chunkMinX, chunkMinZ, Block.DIRT, Block.WATER);
        ourPiece.postProcess(ourLevel.level(), ourChunkBB(chunkMinX, chunkMinZ));

        compareBlocks("swampHut seed=" + seed, handler, ourLevel, chunkMinX - 2, chunkMinZ - 2,
                chunkMinX + 10, chunkMinZ + 12, java.util.Set.of());
    }

    // --- buried treasure -------------------------------------------------------

    private static void runBuriedTreasure(long seed, int chunkMinX, int chunkMinZ) {
        var anchorX = chunkMinX + 9;
        var anchorZ = chunkMinZ + 9;
        var vanillaPiece = new BuriedTreasurePieces.BuriedTreasurePiece(new BlockPos(anchorX, 90, anchorZ));
        var ourPiece = new rocks.minestom.worldgen.structure.scattered.BuriedTreasurePiece(anchorX, anchorZ);

        var handler = new VanillaLevel(seed, "minecraft:stone", "minecraft:sand");
        var level = proxyLevel(handler);
        var vanillaRandom = new WorldgenRandom(new LegacyRandomSource(seed ^ 0x7EA5L));
        vanillaPiece.postProcess(level, null, null, vanillaRandom, vanillaChunkBB(), null, BlockPos.ZERO);

        var ourLevel = ourLevel(chunkMinX, chunkMinZ, Block.STONE, Block.SAND);
        var ourRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                new rocks.minestom.worldgen.random.LegacyRandomSource(seed ^ 0x7EA5L));
        ourPiece.postProcess(ourLevel.level(), ourRandom);

        compareBlocks("buriedTreasure seed=" + seed, handler, ourLevel, anchorX - 2, anchorZ - 2,
                anchorX + 2, anchorZ + 2, java.util.Set.of());
    }

    // --- shared test infrastructure --------------------------------------------

    private static void assertBoundsMatch(net.minecraft.world.level.levelgen.structure.BoundingBox vanilla,
            rocks.minestom.worldgen.structure.template.BoundingBox ours) {
        assertEquals(vanilla.minX(), ours.minX(), "minX");
        assertEquals(vanilla.minY(), ours.minY(), "minY");
        assertEquals(vanilla.minZ(), ours.minZ(), "minZ");
        assertEquals(vanilla.maxX(), ours.maxX(), "maxX");
        assertEquals(vanilla.maxY(), ours.maxY(), "maxY");
        assertEquals(vanilla.maxZ(), ours.maxZ(), "maxZ");
    }

    private static net.minecraft.world.level.levelgen.structure.BoundingBox vanillaChunkBB() {
        return new net.minecraft.world.level.levelgen.structure.BoundingBox(
                Integer.MIN_VALUE / 2, MIN_Y, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, MAX_Y, Integer.MAX_VALUE / 2);
    }

    private static rocks.minestom.worldgen.structure.template.BoundingBox ourChunkBB(int chunkMinX, int chunkMinZ) {
        return new rocks.minestom.worldgen.structure.template.BoundingBox(
                Integer.MIN_VALUE / 2, MIN_Y, Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, MAX_Y, Integer.MAX_VALUE / 2);
    }

    private record OurLevel(ScatteredFeatureLevel level, Map<BlockVec, Block> writes) {
    }

    private static OurLevel ourLevel(int chunkMinX, int chunkMinZ, Block floorBlock, Block surfaceBlock) {
        var sizeX = 64;
        var sizeZ = 64;
        var startX = chunkMinX - 24;
        var startZ = chunkMinZ - 24;
        var height = MAX_Y - MIN_Y + 1;
        var blocks = new Block[sizeX * sizeZ * height];
        for (var localX = 0; localX < sizeX; localX++) {
            for (var localZ = 0; localZ < sizeZ; localZ++) {
                var base = (localX * sizeZ + localZ) * height;
                for (var y = MIN_Y; y < FLOOR_Y; y++) {
                    blocks[base + (y - MIN_Y)] = floorBlock;
                }
                blocks[base + (FLOOR_Y - MIN_Y)] = surfaceBlock;
            }
        }
        // Every explicit write (even one that happens to match the ambient
        // block, like sandstone laid over a sandstone ambient floor) must be
        // captured, mirroring vanilla's overlay map which records every
        // level.setBlock call unconditionally - so writes are captured at
        // the fake unit's modifier().setRelative boundary instead of by
        // diffing the block buffer against its initial contents.
        var writes = new HashMap<BlockVec, Block>();
        var unit = fakeUnit(startX, startZ, MIN_Y, writes);
        var adapter = new GenerationUnitAdapter(unit);
        var level = new ScatteredFeatureLevel(adapter, blocks, startX, startZ, sizeX, sizeZ, MIN_Y, MAX_Y, null);
        return new OurLevel(level, writes);
    }

    private static void compareBlocks(String label, VanillaLevel handler, OurLevel ourLevel,
            int minX, int minZ, int maxX, int maxZ, java.util.Set<String> excluded) {
        var vanillaSets = new TreeMap<String, String>();
        for (var entry : handler.overlay.entrySet()) {
            var pos = entry.getKey();
            if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) {
                continue;
            }
            vanillaSets.put(key(pos.getX(), pos.getY(), pos.getZ()), canonical(entry.getValue()));
        }

        var ourSets = new TreeMap<String, String>();
        for (var entry : ourLevel.writes().entrySet()) {
            var pos = entry.getKey();
            if (pos.blockX() < minX || pos.blockX() > maxX || pos.blockZ() < minZ || pos.blockZ() > maxZ) {
                continue;
            }
            ourSets.put(key(pos.blockX(), pos.blockY(), pos.blockZ()), canonical(entry.getValue()));
        }

        var keys = new TreeSet<String>();
        keys.addAll(vanillaSets.keySet());
        keys.addAll(ourSets.keySet());
        var mismatches = 0;
        var printed = 0;
        for (var k : keys) {
            if (excluded.contains(k)) {
                continue;
            }
            var vanilla = vanillaSets.get(k);
            var ours = ourSets.get(k);
            if (!java.util.Objects.equals(vanilla, ours)) {
                mismatches++;
                if (printed < 20) {
                    System.out.println(label + " at " + k + " vanilla=" + vanilla + " ours=" + ours);
                    printed++;
                }
            }
        }

        assertEquals(0, mismatches, label + ": " + mismatches + " block mismatches");
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String canonical(BlockState state) {
        var name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        var props = new TreeMap<String, String>();
        for (var property : state.getProperties()) {
            props.put(property.getName(), ((net.minecraft.world.level.block.state.properties.Property) property)
                    .getName(state.getValue(property)));
        }
        return name + props;
    }

    private static String canonical(Block block) {
        return block.key().asString() + new TreeMap<>(block.properties());
    }

    private static WorldGenLevel proxyLevel(VanillaLevel handler) {
        return (WorldGenLevel) Proxy.newProxyInstance(ScatteredStructuresABTest.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class}, handler);
    }

    private static GenerationUnit fakeUnit(int startX, int startZ, int minY, Map<BlockVec, Block> writes) {
        var handler = (InvocationHandler) (proxy, method, args) -> {
            switch (method.getName()) {
                case "absoluteStart" -> {
                    return new BlockVec(startX, minY, startZ);
                }
                case "size" -> {
                    return new BlockVec(64, MAX_Y - MIN_Y + 1, 64);
                }
                case "modifier" -> {
                    return Proxy.newProxyInstance(ScatteredStructuresABTest.class.getClassLoader(),
                            new Class<?>[]{UnitModifier.class}, (InvocationHandler) (p, m, a) -> {
                                if (m.getName().equals("setRelative")) {
                                    var worldX = startX + (Integer) a[0];
                                    var worldY = minY + (Integer) a[1];
                                    var worldZ = startZ + (Integer) a[2];
                                    writes.put(new BlockVec(worldX, worldY, worldZ), (Block) a[3]);
                                    return null;
                                }
                                return switch (m.getName()) {
                                    case "toString" -> "fake-unit-modifier";
                                    case "hashCode" -> 0;
                                    case "equals" -> p == (a != null && a.length > 0 ? a[0] : null);
                                    default -> null;
                                };
                            });
                }
                case "toString" -> {
                    return "fake-unit";
                }
                case "hashCode" -> {
                    return 0;
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        };
        return (GenerationUnit) Proxy.newProxyInstance(ScatteredStructuresABTest.class.getClassLoader(),
                new Class<?>[]{GenerationUnit.class}, handler);
    }

    /**
     * Synthetic flat floor: solid up to y=0, air above, so every piece's
     * {@code updateAverageGroundHeight}/{@code updateHeightPositionToLowestGroundHeight}
     * resolves to the same ground level (y=1) on both sides.
     */
    private static final class VanillaLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long seed;
        final BlockState floorState;
        final BlockState surfaceState;

        VanillaLevel(long seed, String floorId, String surfaceId) {
            this.seed = seed;
            this.floorState = vanillaState(floorId);
            this.surfaceState = vanillaState(surfaceId);
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }
            if (pos.getY() < FLOOR_Y) {
                return this.floorState;
            }
            if (pos.getY() == FLOOR_Y) {
                return this.surfaceState;
            }
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
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
                case "getHeightmapPos" -> {
                    var pos = (BlockPos) args[1];
                    return new BlockPos(pos.getX(), FLOOR_Y + 1, pos.getZ());
                }
                case "getHeight" -> {
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    return FLOOR_Y + 1;
                }
                case "isOutsideBuildHeight" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    return y < MIN_Y || y > MAX_Y;
                }
                case "getMinY" -> {
                    return MIN_Y;
                }
                case "getMaxY" -> {
                    return MAX_Y;
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
                case "getChunk" -> {
                    return Proxy.newProxyInstance(ScatteredStructuresABTest.class.getClassLoader(),
                            new Class<?>[]{net.minecraft.world.level.chunk.ChunkAccess.class},
                            (InvocationHandler) (p, m, a) -> switch (m.getName()) {
                                case "markPosForPostProcessing" -> null;
                                case "toString" -> "fake-chunk";
                                case "hashCode" -> 0;
                                case "equals" -> p == (a != null && a.length > 0 ? a[0] : null);
                                default -> null;
                            });
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return RandomSource.create(0L);
                }
                case "getCurrentDifficultyAt" -> {
                    return null;
                }
                case "getLevel" -> {
                    return null;
                }
                case "addFreshEntityWithPassengers" -> {
                    return true;
                }
                case "toString" -> {
                    return "scattered-ab-level";
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

    private static BlockState vanillaState(String id) {
        try {
            return net.minecraft.commands.arguments.blocks.BlockStateParser
                    .parseForBlock(BuiltInRegistries.BLOCK, id, false).blockState();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
