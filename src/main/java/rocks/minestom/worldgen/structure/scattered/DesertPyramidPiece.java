package rocks.minestom.worldgen.structure.scattered;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Port of vanilla {@code DesertPyramidPiece}. Suspicious sand and the
 * collapsed roof position vanilla resolves at structure-level (in
 * {@code DesertPyramidStructure.afterPlace}) are recorded here for the
 * placer to finish after every piece of the start has run.
 */
final class DesertPyramidPiece extends ScatteredFeaturePiece {
    static final int WIDTH = 21;
    static final int DEPTH = 21;

    private final boolean[] hasPlacedChest = new boolean[4];
    private final List<BlockVec> potentialSuspiciousSandWorldPositions = new ArrayList<>();
    private BlockVec randomCollapsedRoofPos = new BlockVec(0, 0, 0);

    DesertPyramidPiece(RandomSource random, int west, int north) {
        super(west, 64, north, WIDTH, 15, DEPTH, getRandomHorizontalDirection(random));
    }

    void postProcess(ScatteredFeatureLevel level, RandomSource random, BoundingBox chunkBB, long levelSeed) {
        if (!this.updateHeightPositionToLowestGroundHeight(level, -random.nextInt(3))) {
            return;
        }

        this.generateBox(level, chunkBB, 0, -4, 0, this.width - 1, 0, this.depth - 1,
                Block.SANDSTONE, Block.SANDSTONE, false);

        for (var pos = 1; pos <= 9; pos++) {
            this.generateBox(level, chunkBB, pos, pos, pos, this.width - 1 - pos, pos, this.depth - 1 - pos,
                    Block.SANDSTONE, Block.SANDSTONE, false);
            this.generateBox(level, chunkBB, pos + 1, pos, pos + 1, this.width - 2 - pos, pos, this.depth - 2 - pos,
                    Block.AIR, Block.AIR, false);
        }

        for (var x = 0; x < this.width; x++) {
            for (var z = 0; z < this.depth; z++) {
                this.fillColumnDown(level, Block.SANDSTONE, x, -5, z, chunkBB);
            }
        }

        var northStairs = Block.SANDSTONE_STAIRS.withProperty("facing", "north");
        var southStairs = Block.SANDSTONE_STAIRS.withProperty("facing", "south");
        var eastStairs = Block.SANDSTONE_STAIRS.withProperty("facing", "east");
        var westStairs = Block.SANDSTONE_STAIRS.withProperty("facing", "west");

        this.generateBox(level, chunkBB, 0, 0, 0, 4, 9, 4, Block.SANDSTONE, Block.AIR, false);
        this.generateBox(level, chunkBB, 1, 10, 1, 3, 10, 3, Block.SANDSTONE, Block.SANDSTONE, false);
        this.placeBlock(level, northStairs, 2, 10, 0, chunkBB);
        this.placeBlock(level, southStairs, 2, 10, 4, chunkBB);
        this.placeBlock(level, eastStairs, 0, 10, 2, chunkBB);
        this.placeBlock(level, westStairs, 4, 10, 2, chunkBB);
        this.generateBox(level, chunkBB, this.width - 5, 0, 0, this.width - 1, 9, 4, Block.SANDSTONE, Block.AIR, false);
        this.generateBox(level, chunkBB, this.width - 4, 10, 1, this.width - 2, 10, 3, Block.SANDSTONE, Block.SANDSTONE, false);
        this.placeBlock(level, northStairs, this.width - 3, 10, 0, chunkBB);
        this.placeBlock(level, southStairs, this.width - 3, 10, 4, chunkBB);
        this.placeBlock(level, eastStairs, this.width - 5, 10, 2, chunkBB);
        this.placeBlock(level, westStairs, this.width - 1, 10, 2, chunkBB);
        this.generateBox(level, chunkBB, 8, 0, 0, 12, 4, 4, Block.SANDSTONE, Block.AIR, false);
        this.generateBox(level, chunkBB, 9, 1, 0, 11, 3, 4, Block.AIR, Block.AIR, false);
        this.placeBlock(level, Block.CUT_SANDSTONE, 9, 1, 1, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 9, 2, 1, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 9, 3, 1, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 10, 3, 1, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 11, 3, 1, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 11, 2, 1, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 11, 1, 1, chunkBB);
        this.generateBox(level, chunkBB, 4, 1, 1, 8, 3, 3, Block.SANDSTONE, Block.AIR, false);
        this.generateBox(level, chunkBB, 4, 1, 2, 8, 2, 2, Block.AIR, Block.AIR, false);
        this.generateBox(level, chunkBB, 12, 1, 1, 16, 3, 3, Block.SANDSTONE, Block.AIR, false);
        this.generateBox(level, chunkBB, 12, 1, 2, 16, 2, 2, Block.AIR, Block.AIR, false);
        this.generateBox(level, chunkBB, 5, 4, 5, this.width - 6, 4, this.depth - 6, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, 9, 4, 9, 11, 4, 11, Block.AIR, Block.AIR, false);
        this.generateBox(level, chunkBB, 8, 1, 8, 8, 3, 8, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.generateBox(level, chunkBB, 12, 1, 8, 12, 3, 8, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.generateBox(level, chunkBB, 8, 1, 12, 8, 3, 12, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.generateBox(level, chunkBB, 12, 1, 12, 12, 3, 12, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.generateBox(level, chunkBB, 1, 1, 5, 4, 4, 11, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, this.width - 5, 1, 5, this.width - 2, 4, 11, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, 6, 7, 9, 6, 7, 11, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, this.width - 7, 7, 9, this.width - 7, 7, 11, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, 5, 5, 9, 5, 7, 11, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.generateBox(level, chunkBB, this.width - 6, 5, 9, this.width - 6, 7, 11, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.placeBlock(level, Block.AIR, 5, 5, 10, chunkBB);
        this.placeBlock(level, Block.AIR, 5, 6, 10, chunkBB);
        this.placeBlock(level, Block.AIR, 6, 6, 10, chunkBB);
        this.placeBlock(level, Block.AIR, this.width - 6, 5, 10, chunkBB);
        this.placeBlock(level, Block.AIR, this.width - 6, 6, 10, chunkBB);
        this.placeBlock(level, Block.AIR, this.width - 7, 6, 10, chunkBB);
        this.generateBox(level, chunkBB, 2, 4, 4, 2, 6, 4, Block.AIR, Block.AIR, false);
        this.generateBox(level, chunkBB, this.width - 3, 4, 4, this.width - 3, 6, 4, Block.AIR, Block.AIR, false);
        this.placeBlock(level, northStairs, 2, 4, 5, chunkBB);
        this.placeBlock(level, northStairs, 2, 3, 4, chunkBB);
        this.placeBlock(level, northStairs, this.width - 3, 4, 5, chunkBB);
        this.placeBlock(level, northStairs, this.width - 3, 3, 4, chunkBB);
        this.generateBox(level, chunkBB, 1, 1, 3, 2, 2, 3, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, this.width - 3, 1, 3, this.width - 2, 2, 3, Block.SANDSTONE, Block.SANDSTONE, false);
        this.placeBlock(level, Block.SANDSTONE, 1, 1, 2, chunkBB);
        this.placeBlock(level, Block.SANDSTONE, this.width - 2, 1, 2, chunkBB);
        this.placeBlock(level, Block.SANDSTONE_SLAB, 1, 2, 2, chunkBB);
        this.placeBlock(level, Block.SANDSTONE_SLAB, this.width - 2, 2, 2, chunkBB);
        this.placeBlock(level, westStairs, 2, 1, 2, chunkBB);
        this.placeBlock(level, eastStairs, this.width - 3, 1, 2, chunkBB);
        this.generateBox(level, chunkBB, 4, 3, 5, 4, 3, 17, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, this.width - 5, 3, 5, this.width - 5, 3, 17, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, 3, 1, 5, 4, 2, 16, Block.AIR, Block.AIR, false);
        this.generateBox(level, chunkBB, this.width - 6, 1, 5, this.width - 5, 2, 16, Block.AIR, Block.AIR, false);

        for (var z = 5; z <= 17; z += 2) {
            this.placeBlock(level, Block.CUT_SANDSTONE, 4, 1, z, chunkBB);
            this.placeBlock(level, Block.CHISELED_SANDSTONE, 4, 2, z, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, this.width - 5, 1, z, chunkBB);
            this.placeBlock(level, Block.CHISELED_SANDSTONE, this.width - 5, 2, z, chunkBB);
        }

        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 10, 0, 7, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 10, 0, 8, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 9, 0, 9, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 11, 0, 9, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 8, 0, 10, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 12, 0, 10, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 7, 0, 10, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 13, 0, 10, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 9, 0, 11, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 11, 0, 11, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 10, 0, 12, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 10, 0, 13, chunkBB);
        this.placeBlock(level, Block.BLUE_TERRACOTTA, 10, 0, 10, chunkBB);

        for (var x = 0; x <= this.width - 1; x += this.width - 1) {
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 2, 1, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 2, 2, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 2, 3, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 3, 1, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 3, 2, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 3, 3, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 4, 1, chunkBB);
            this.placeBlock(level, Block.CHISELED_SANDSTONE, x, 4, 2, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 4, 3, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 5, 1, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 5, 2, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 5, 3, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 6, 1, chunkBB);
            this.placeBlock(level, Block.CHISELED_SANDSTONE, x, 6, 2, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 6, 3, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 7, 1, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 7, 2, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 7, 3, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 8, 1, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 8, 2, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 8, 3, chunkBB);
        }

        for (var x = 2; x <= this.width - 3; x += this.width - 3 - 2) {
            this.placeBlock(level, Block.CUT_SANDSTONE, x - 1, 2, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 2, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x + 1, 2, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x - 1, 3, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 3, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x + 1, 3, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x - 1, 4, 0, chunkBB);
            this.placeBlock(level, Block.CHISELED_SANDSTONE, x, 4, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x + 1, 4, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x - 1, 5, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 5, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x + 1, 5, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x - 1, 6, 0, chunkBB);
            this.placeBlock(level, Block.CHISELED_SANDSTONE, x, 6, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x + 1, 6, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x - 1, 7, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, 7, 0, chunkBB);
            this.placeBlock(level, Block.ORANGE_TERRACOTTA, x + 1, 7, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x - 1, 8, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x, 8, 0, chunkBB);
            this.placeBlock(level, Block.CUT_SANDSTONE, x + 1, 8, 0, chunkBB);
        }

        this.generateBox(level, chunkBB, 8, 4, 0, 12, 6, 0, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.placeBlock(level, Block.AIR, 8, 6, 0, chunkBB);
        this.placeBlock(level, Block.AIR, 12, 6, 0, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 9, 5, 0, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, 10, 5, 0, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, 11, 5, 0, chunkBB);
        this.generateBox(level, chunkBB, 8, -14, 8, 12, -11, 12, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.generateBox(level, chunkBB, 8, -10, 8, 12, -10, 12, Block.CHISELED_SANDSTONE, Block.CHISELED_SANDSTONE, false);
        this.generateBox(level, chunkBB, 8, -9, 8, 12, -9, 12, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, false);
        this.generateBox(level, chunkBB, 8, -8, 8, 12, -1, 12, Block.SANDSTONE, Block.SANDSTONE, false);
        this.generateBox(level, chunkBB, 9, -11, 9, 11, -1, 11, Block.AIR, Block.AIR, false);
        this.placeBlock(level, Block.STONE_PRESSURE_PLATE, 10, -11, 10, chunkBB);
        this.generateBox(level, chunkBB, 9, -13, 9, 11, -13, 11, Block.TNT, Block.AIR, false);
        this.placeBlock(level, Block.AIR, 8, -11, 10, chunkBB);
        this.placeBlock(level, Block.AIR, 8, -10, 10, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, 7, -10, 10, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 7, -11, 10, chunkBB);
        this.placeBlock(level, Block.AIR, 12, -11, 10, chunkBB);
        this.placeBlock(level, Block.AIR, 12, -10, 10, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, 13, -10, 10, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 13, -11, 10, chunkBB);
        this.placeBlock(level, Block.AIR, 10, -11, 8, chunkBB);
        this.placeBlock(level, Block.AIR, 10, -10, 8, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, 10, -10, 7, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 10, -11, 7, chunkBB);
        this.placeBlock(level, Block.AIR, 10, -11, 12, chunkBB);
        this.placeBlock(level, Block.AIR, 10, -10, 12, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, 10, -10, 13, chunkBB);
        this.placeBlock(level, Block.CUT_SANDSTONE, 10, -11, 13, chunkBB);

        for (var direction : Direction.HORIZONTAL) {
            var index = horizontalIndex(direction);
            if (!this.hasPlacedChest[index]) {
                var xo = direction.stepX() * 2;
                var zo = direction.stepZ() * 2;
                this.hasPlacedChest[index] = this.createChest(level, chunkBB, random, 10 + xo, -11, 10 + zo);
            }
        }

        this.addCellar(level, chunkBB, random, levelSeed);
    }

    /** Vanilla {@code Direction.get2DDataValue()}: south, west, north, east. */
    private static int horizontalIndex(Direction direction) {
        return switch (direction) {
            case SOUTH -> 0;
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> throw new IllegalArgumentException(direction.toString());
        };
    }

    private void addCellar(ScatteredFeatureLevel level, BoundingBox chunkBB, RandomSource random, long levelSeed) {
        var roomX = 16;
        var roomY = -4;
        var roomZ = 13;
        this.addCellarStairs(roomX, roomY, roomZ, level, chunkBB, levelSeed);
        this.addCellarRoom(roomX, roomY, roomZ, level, chunkBB, levelSeed);
    }

    /**
     * Vanilla draws the sand/sandstone variant bit from {@code level.getRandom()}
     * (the chunk's live world-gen random), not the postProcess {@code random}
     * argument, so this never touches that argument either - see
     * {@link #placeCollapsedRoof} for the same substitution and its caveats.
     */
    private void addCellarStairs(int x, int y, int z, ScatteredFeatureLevel level, BoundingBox chunkBB,
            long levelSeed) {
        var stairs = Block.SANDSTONE_STAIRS.withProperty("facing", "west");
        this.placeBlock(level, stairs, 13, -1, 17, chunkBB);
        this.placeBlock(level, stairs, 14, -2, 17, chunkBB);
        this.placeBlock(level, stairs, 15, -3, 17, chunkBB);
        var origin = this.getWorldPos(x, y, z);
        var variant = new LegacyRandomSource(levelSeed).forkPositional()
                .at(origin.blockX(), origin.blockY(), origin.blockZ()).nextBoolean();
        this.placeBlock(level, Block.SAND, x - 4, y + 4, z + 4, chunkBB);
        this.placeBlock(level, Block.SAND, x - 3, y + 4, z + 4, chunkBB);
        this.placeBlock(level, Block.SAND, x - 2, y + 4, z + 4, chunkBB);
        this.placeBlock(level, Block.SAND, x - 1, y + 4, z + 4, chunkBB);
        this.placeBlock(level, Block.SAND, x, y + 4, z + 4, chunkBB);
        this.placeBlock(level, Block.SAND, x - 2, y + 3, z + 4, chunkBB);
        this.placeBlock(level, variant ? Block.SAND : Block.SANDSTONE, x - 1, y + 3, z + 4, chunkBB);
        this.placeBlock(level, !variant ? Block.SAND : Block.SANDSTONE, x, y + 3, z + 4, chunkBB);
        this.placeBlock(level, Block.SAND, x - 1, y + 2, z + 4, chunkBB);
        this.placeBlock(level, Block.SANDSTONE, x, y + 2, z + 4, chunkBB);
        this.placeBlock(level, Block.SAND, x, y + 1, z + 4, chunkBB);
    }

    private void addCellarRoom(int x, int y, int z, ScatteredFeatureLevel level, BoundingBox chunkBB,
            long levelSeed) {
        this.generateBox(level, chunkBB, x - 3, y + 1, z - 3, x - 3, y + 1, z + 2, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.generateBox(level, chunkBB, x + 3, y + 1, z - 3, x + 3, y + 1, z + 2, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, y + 1, z - 3, x + 3, y + 1, z - 2, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, y + 1, z + 3, x + 3, y + 1, z + 3, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, y + 2, z - 3, x - 3, y + 2, z + 2, Block.CHISELED_SANDSTONE, Block.CHISELED_SANDSTONE, true);
        this.generateBox(level, chunkBB, x + 3, y + 2, z - 3, x + 3, y + 2, z + 2, Block.CHISELED_SANDSTONE, Block.CHISELED_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, y + 2, z - 3, x + 3, y + 2, z - 2, Block.CHISELED_SANDSTONE, Block.CHISELED_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, y + 2, z + 3, x + 3, y + 2, z + 3, Block.CHISELED_SANDSTONE, Block.CHISELED_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, -1, z - 3, x - 3, -1, z + 2, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.generateBox(level, chunkBB, x + 3, -1, z - 3, x + 3, -1, z + 2, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, -1, z - 3, x + 3, -1, z - 2, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.generateBox(level, chunkBB, x - 3, -1, z + 3, x + 3, -1, z + 3, Block.CUT_SANDSTONE, Block.CUT_SANDSTONE, true);
        this.placeSandBox(x - 2, y + 1, z - 2, x + 2, y + 3, z + 2);
        this.placeCollapsedRoof(level, chunkBB, levelSeed, x - 2, y + 4, z - 2, x + 2, z + 2);
        this.placeBlock(level, Block.BLUE_TERRACOTTA, x, y, z, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x + 1, y, z - 1, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x + 1, y, z + 1, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x - 1, y, z - 1, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x - 1, y, z + 1, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x + 2, y, z, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x - 2, y, z, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, y, z + 2, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, y, z - 2, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x + 3, y, z, chunkBB);
        this.placeSand(x + 3, y + 1, z);
        this.placeSand(x + 3, y + 2, z);
        this.placeBlock(level, Block.CUT_SANDSTONE, x + 4, y + 1, z, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, x + 4, y + 2, z, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x - 3, y, z, chunkBB);
        this.placeSand(x - 3, y + 1, z);
        this.placeSand(x - 3, y + 2, z);
        this.placeBlock(level, Block.CUT_SANDSTONE, x - 4, y + 1, z, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, x - 4, y + 2, z, chunkBB);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, y, z + 3, chunkBB);
        this.placeSand(x, y + 1, z + 3);
        this.placeSand(x, y + 2, z + 3);
        this.placeBlock(level, Block.ORANGE_TERRACOTTA, x, y, z - 3, chunkBB);
        this.placeSand(x, y + 1, z - 3);
        this.placeSand(x, y + 2, z - 3);
        this.placeBlock(level, Block.CUT_SANDSTONE, x, y + 1, z - 4, chunkBB);
        this.placeBlock(level, Block.CHISELED_SANDSTONE, x, -2, z - 4, chunkBB);
    }

    private void placeSand(int x, int y, int z) {
        this.potentialSuspiciousSandWorldPositions.add(this.getWorldPos(x, y, z));
    }

    private void placeSandBox(int x0, int y0, int z0, int x1, int y1, int z1) {
        for (var y = y0; y <= y1; y++) {
            for (var x = x0; x <= x1; x++) {
                for (var z = z0; z <= z1; z++) {
                    this.placeSand(x, y, z);
                }
            }
        }
    }

    private void placeCollapsedRoofPiece(ScatteredFeatureLevel level, RandomSource levelRandom, int x, int y, int z,
            BoundingBox chunkBB) {
        if (levelRandom.nextFloat() < 0.33F) {
            this.placeBlock(level, Block.SANDSTONE, x, y, z, chunkBB);
        } else {
            this.placeBlock(level, Block.SAND, x, y, z, chunkBB);
        }
    }

    /**
     * Vanilla uses {@code level.getRandom()} (the chunk's live world-gen
     * random, entangled with the rest of chunk generation and not
     * reproducible here) for the per-tile roof choice, then a positional
     * random forked from the world seed for the single collapse point. This
     * port never touches the postProcess {@code random} argument for either
     * draw - preserving its draw count for later calls like vanilla - and
     * substitutes a deterministic per-position positional random for the
     * per-tile choice, which reproduces vanilla's bit-exact collapse point
     * but not its bit-exact per-tile sand/sandstone pattern.
     */
    private void placeCollapsedRoof(ScatteredFeatureLevel level, BoundingBox chunkBB, long levelSeed,
            int x0, int y0, int z0, int x1, int z1) {
        var tileRandomFactory = new LegacyRandomSource(levelSeed).forkPositional();
        for (var x = x0; x <= x1; x++) {
            for (var z = z0; z <= z1; z++) {
                var world = this.getWorldPos(x, y0, z);
                var tileRandom = tileRandomFactory.at(world.blockX(), world.blockY(), world.blockZ());
                this.placeCollapsedRoofPiece(level, tileRandom, x, y0, z, chunkBB);
            }
        }

        var origin = this.getWorldPos(x0, y0, z0);
        var positionalRandom = new LegacyRandomSource(levelSeed).forkPositional()
                .at(origin.blockX(), origin.blockY(), origin.blockZ());
        var roofPosX = x0 + positionalRandom.nextInt(x1 - x0 + 1);
        var roofPosZ = z0 + positionalRandom.nextInt(z1 - z0 + 1);
        this.randomCollapsedRoofPos = this.getWorldPos(roofPosX, y0, roofPosZ);
    }

    List<BlockVec> getPotentialSuspiciousSandWorldPositions() {
        return this.potentialSuspiciousSandWorldPositions;
    }

    BlockVec getRandomCollapsedRoofPos() {
        return this.randomCollapsedRoofPos;
    }

    /**
     * Port of vanilla {@code DesertPyramidStructure.afterPlace}: places the
     * single collapsed-roof block, then shuffles the piece's potential
     * suspicious sand positions with a positional random forked from the
     * world seed at the structure's bounding box center, turning 5-7 of them
     * into suspicious sand (loot table NBT out of scope) and the rest into
     * plain sand.
     */
    static void afterPlace(ScatteredFeatureLevel level, BoundingBox chunkBB, long levelSeed, DesertPyramidPiece piece) {
        var uniquePositions = new TreeSet<BlockVec>(Comparator.comparingInt(BlockVec::blockX)
                .thenComparingInt(BlockVec::blockY).thenComparingInt(BlockVec::blockZ));
        uniquePositions.addAll(piece.getPotentialSuspiciousSandWorldPositions());
        placeSuspiciousSand(level, chunkBB, piece.getRandomCollapsedRoofPos());

        var shuffled = new ArrayList<>(uniquePositions);
        var center = piece.boundingBox().getCenter();
        var positionalRandom = new LegacyRandomSource(levelSeed).forkPositional()
                .at(center.blockX(), center.blockY(), center.blockZ());
        shuffle(shuffled, positionalRandom);
        var toPlace = Math.min(uniquePositions.size(), 5 + positionalRandom.nextInt(3));

        for (var pos : shuffled) {
            if (toPlace > 0) {
                toPlace--;
                placeSuspiciousSand(level, chunkBB, pos);
            } else if (chunkBB.isInside(pos)) {
                level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.SAND);
            }
        }
    }

    private static void placeSuspiciousSand(ScatteredFeatureLevel level, BoundingBox chunkBB, BlockVec pos) {
        if (chunkBB.isInside(pos)) {
            level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.SUSPICIOUS_SAND);
        }
    }

    private static void shuffle(List<BlockVec> list, RandomSource random) {
        for (var i = list.size(); i > 1; i--) {
            var swapTo = random.nextInt(i);
            var temp = list.get(i - 1);
            list.set(i - 1, list.get(swapTo));
            list.set(swapTo, temp);
        }
    }
}
