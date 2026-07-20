package rocks.minestom.worldgen.structure.scattered;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

/**
 * Port of vanilla {@code ScatteredFeaturePiece} plus the subset of
 * {@code StructurePiece} helpers used by the desert pyramid, jungle temple
 * and swamp hut pieces: local-space block placement transformed by the
 * piece's random horizontal orientation, box fills, block selectors, chest
 * and dispenser stand-ins, and the two ground-height resolution strategies.
 */
abstract class ScatteredFeaturePiece {
    protected final int width;
    protected final int height;
    protected final int depth;
    protected BoundingBox boundingBox;
    protected int heightPosition = -1;
    private final Direction orientation;
    private final boolean mirrorLeftRight;
    private final boolean rotateClockwise;

    protected ScatteredFeaturePiece(int west, int floor, int north, int width, int height, int depth,
            Direction direction) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        this.boundingBox = makeBoundingBox(west, floor, north, direction, width, height, depth);
        this.orientation = direction;
        switch (direction) {
            case SOUTH -> {
                this.mirrorLeftRight = true;
                this.rotateClockwise = false;
            }
            case WEST -> {
                this.mirrorLeftRight = true;
                this.rotateClockwise = true;
            }
            case EAST -> {
                this.mirrorLeftRight = false;
                this.rotateClockwise = true;
            }
            default -> {
                this.mirrorLeftRight = false;
                this.rotateClockwise = false;
            }
        }
    }

    static Direction getRandomHorizontalDirection(RandomSource random) {
        return Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
    }

    private static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int width, int height,
            int depth) {
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            return new BoundingBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1);
        }
        return new BoundingBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
    }

    BoundingBox boundingBox() {
        return this.boundingBox;
    }

    /**
     * Vanilla {@code updateAverageGroundHeight}: moves the piece so its floor
     * sits at the average {@code MOTION_BLOCKING_NO_LEAVES} height across the
     * footprint, plus the given offset.
     */
    protected boolean updateAverageGroundHeight(ScatteredFeatureLevel level, BoundingBox chunkBB, int offset) {
        if (this.heightPosition >= 0) {
            return true;
        }

        var total = 0L;
        var count = 0;
        for (var z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
            for (var x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
                if (chunkBB.isInside(new net.minestom.server.coordinate.BlockVec(x, 64, z))) {
                    total += level.motionBlockingNoLeavesHeight(x, z);
                    count++;
                }
            }
        }

        if (count == 0) {
            return false;
        }

        this.heightPosition = (int) (total / count);
        this.boundingBox = this.boundingBox.moved(0, this.heightPosition - this.boundingBox.minY() + offset, 0);
        return true;
    }

    /**
     * Vanilla {@code updateHeightPositionToLowestGroundHeight}: moves the
     * piece so its floor sits at the lowest {@code MOTION_BLOCKING_NO_LEAVES}
     * height across the footprint, plus the given offset.
     */
    protected boolean updateHeightPositionToLowestGroundHeight(ScatteredFeatureLevel level, int offset) {
        if (this.heightPosition >= 0) {
            return true;
        }

        var lowest = level.maxY() + 1;
        var found = false;
        for (var z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
            for (var x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
                lowest = Math.min(lowest, level.motionBlockingNoLeavesHeight(x, z));
                found = true;
            }
        }

        if (!found) {
            return false;
        }

        this.heightPosition = lowest;
        this.boundingBox = this.boundingBox.moved(0, this.heightPosition - this.boundingBox.minY() + offset, 0);
        return true;
    }

    protected net.minestom.server.coordinate.BlockVec getWorldPos(int x, int y, int z) {
        return new net.minestom.server.coordinate.BlockVec(this.worldX(x, z), this.worldY(y), this.worldZ(x, z));
    }

    private int worldX(int x, int z) {
        return switch (this.orientation) {
            case NORTH, SOUTH -> this.boundingBox.minX() + x;
            case WEST -> this.boundingBox.maxX() - z;
            case EAST -> this.boundingBox.minX() + z;
            default -> x;
        };
    }

    private int worldY(int y) {
        return y + this.boundingBox.minY();
    }

    private int worldZ(int x, int z) {
        return switch (this.orientation) {
            case NORTH -> this.boundingBox.maxZ() - z;
            case SOUTH -> this.boundingBox.minZ() + z;
            case WEST, EAST -> this.boundingBox.minZ() + x;
            default -> z;
        };
    }

    protected void placeBlock(ScatteredFeatureLevel level, Block block, int x, int y, int z, BoundingBox chunkBB) {
        var wx = this.worldX(x, z);
        var wy = this.worldY(y);
        var wz = this.worldZ(x, z);
        if (!chunkBB.isInside(new net.minestom.server.coordinate.BlockVec(wx, wy, wz))) {
            return;
        }
        var transformed = this.transform(block);
        level.setBlock(wx, wy, wz, transformed);
        // Vanilla StructurePiece.placeBlock marks shape-sensitive blocks
        // (fences, iron bars, ladders, torches) for the FULL post-process
        // pass, where updateFromNeighbourShapes computes their connections
        if (isShapeCheckBlock(transformed)) {
            level.markPostProcess(wx, wy, wz);
        }
    }

    private static boolean isShapeCheckBlock(Block block) {
        var key = block.key().value();
        return switch (key) {
            case "nether_brick_fence", "oak_fence", "spruce_fence", "dark_oak_fence", "pale_oak_fence",
                    "acacia_fence", "birch_fence", "jungle_fence", "iron_bars", "ladder",
                    "torch", "wall_torch" -> true;
            default -> false;
        };
    }

    protected Block getBlock(ScatteredFeatureLevel level, int x, int y, int z, BoundingBox chunkBB) {
        var wx = this.worldX(x, z);
        var wy = this.worldY(y);
        var wz = this.worldZ(x, z);
        if (!chunkBB.isInside(new net.minestom.server.coordinate.BlockVec(wx, wy, wz))) {
            return Block.AIR;
        }
        return level.getBlock(wx, wy, wz);
    }

    protected void generateAirBox(ScatteredFeatureLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
            int x1, int y1, int z1) {
        for (var y = y0; y <= y1; y++) {
            for (var x = x0; x <= x1; x++) {
                for (var z = z0; z <= z1; z++) {
                    this.placeBlock(level, Block.AIR, x, y, z, chunkBB);
                }
            }
        }
    }

    protected void generateBox(ScatteredFeatureLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
            int x1, int y1, int z1, Block edgeBlock, Block fillBlock, boolean skipAir) {
        for (var y = y0; y <= y1; y++) {
            for (var x = x0; x <= x1; x++) {
                for (var z = z0; z <= z1; z++) {
                    if (skipAir && this.getBlock(level, x, y, z, chunkBB).isAir()) {
                        continue;
                    }
                    if (y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1) {
                        this.placeBlock(level, fillBlock, x, y, z, chunkBB);
                    } else {
                        this.placeBlock(level, edgeBlock, x, y, z, chunkBB);
                    }
                }
            }
        }
    }

    protected void generateBox(ScatteredFeatureLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
            int x1, int y1, int z1, boolean skipAir, RandomSource random, BlockSelector selector) {
        for (var y = y0; y <= y1; y++) {
            for (var x = x0; x <= x1; x++) {
                for (var z = z0; z <= z1; z++) {
                    if (skipAir && this.getBlock(level, x, y, z, chunkBB).isAir()) {
                        continue;
                    }
                    var isEdge = y == y0 || y == y1 || x == x0 || x == x1 || z == z0 || z == z1;
                    selector.next(random, x, y, z, isEdge);
                    this.placeBlock(level, selector.next(), x, y, z, chunkBB);
                }
            }
        }
    }

    protected void fillColumnDown(ScatteredFeatureLevel level, Block block, int x, int startY, int z,
            BoundingBox chunkBB) {
        var wx = this.worldX(x, z);
        var wz = this.worldZ(x, z);
        var wy = this.worldY(startY);
        if (!chunkBB.isInside(new net.minestom.server.coordinate.BlockVec(wx, wy, wz))) {
            return;
        }
        while (isReplaceableByStructures(level.getBlock(wx, wy, wz)) && wy > level.minY() + 1) {
            level.setBlock(wx, wy, wz, block);
            wy--;
        }
    }

    private static boolean isReplaceableByStructures(Block block) {
        return block.isAir() || block.isLiquid() || block.compare(Block.GLOW_LICHEN)
                || block.compare(Block.SEAGRASS) || block.compare(Block.TALL_SEAGRASS);
    }

    /**
     * Bare-block chest stand-in: places a chest (loot table NBT is out of
     * scope) and consumes the loot seed draw vanilla makes, so subsequent
     * random calls stay in sync.
     */
    protected boolean createChest(ScatteredFeatureLevel level, BoundingBox chunkBB, RandomSource random,
            int x, int y, int z) {
        var wx = this.worldX(x, z);
        var wy = this.worldY(y);
        var wz = this.worldZ(x, z);
        var pos = new net.minestom.server.coordinate.BlockVec(wx, wy, wz);
        if (!chunkBB.isInside(pos) || level.getBlock(wx, wy, wz).compare(Block.CHEST)) {
            return false;
        }

        level.setBlock(wx, wy, wz, reorientChest(level, wx, wy, wz));
        random.nextLong();
        return true;
    }

    /**
     * Vanilla {@code StructurePiece.reorient}: faces the chest away from its
     * one solid horizontal neighbor, or - with zero or several solid
     * neighbors - locks onto the first of north, south, east, west (starting
     * from the chest's default facing) whose own neighbor is not solid.
     */
    static Block reorientChest(ScatteredFeatureLevel level, int x, int y, int z) {
        Direction solidNeighbor = null;
        for (var direction : Direction.HORIZONTAL) {
            var state = level.getBlock(x + direction.stepX(), y + direction.stepY(), z + direction.stepZ());
            if (state.compare(Block.CHEST)) {
                return Block.CHEST;
            }
            if (state.isSolid()) {
                if (solidNeighbor != null) {
                    solidNeighbor = null;
                    break;
                }
                solidNeighbor = direction;
            }
        }

        if (solidNeighbor != null) {
            return Block.CHEST.withProperty("facing", solidNeighbor.opposite().serializedName());
        }

        var lockDirection = Direction.NORTH;
        if (level.getBlock(x + lockDirection.stepX(), y, z + lockDirection.stepZ()).isSolid()) {
            lockDirection = lockDirection.opposite();
        }
        if (level.getBlock(x + lockDirection.stepX(), y, z + lockDirection.stepZ()).isSolid()) {
            lockDirection = clockwise(lockDirection);
        }
        if (level.getBlock(x + lockDirection.stepX(), y, z + lockDirection.stepZ()).isSolid()) {
            lockDirection = lockDirection.opposite();
        }
        return Block.CHEST.withProperty("facing", lockDirection.serializedName());
    }

    private static Direction clockwise(Direction direction) {
        var index = Direction.HORIZONTAL.indexOf(direction);
        return Direction.HORIZONTAL.get((index + 1) % Direction.HORIZONTAL.size());
    }

    /**
     * Bare-block dispenser stand-in, matching {@link #createChest}.
     */
    protected boolean createDispenser(ScatteredFeatureLevel level, BoundingBox chunkBB, RandomSource random,
            int x, int y, int z, Direction facing) {
        var wx = this.worldX(x, z);
        var wy = this.worldY(y);
        var wz = this.worldZ(x, z);
        if (!chunkBB.isInside(new net.minestom.server.coordinate.BlockVec(wx, wy, wz))
                || level.getBlock(wx, wy, wz).compare(Block.DISPENSER)) {
            return false;
        }

        this.placeBlock(level, Block.DISPENSER.withProperty("facing", facing.name().toLowerCase()), x, y, z, chunkBB);
        random.nextLong();
        return true;
    }

    /**
     * Vanilla {@code StructurePiece.placeBlock}'s mirror-then-rotate step for
     * this piece's random horizontal orientation, applied to the same
     * property groups vanilla's per-block overrides touch: a single
     * {@code facing} direction (stairs, tripwire hooks, levers, dispensers,
     * repeaters, pistons), stairs {@code shape}, and the four cardinal
     * cross-collision properties (tripwire, redstone wire, vine).
     */
    private Block transform(Block block) {
        var result = block;
        if (this.mirrorLeftRight) {
            result = mirror(result);
        }
        if (this.rotateClockwise) {
            result = rotateClockwise(result);
        }
        return result;
    }

    private static Block mirror(Block block) {
        var facing = block.getProperty("facing");
        var shape = block.getProperty("shape");
        if (facing != null && shape != null) {
            if (facing.equals("north") || facing.equals("south")) {
                return block.withProperty("facing", mirrorFacing(facing)).withProperty("shape", mirrorShape(shape));
            }
            return block;
        }
        if (facing != null) {
            return block.withProperty("facing", mirrorFacing(facing));
        }
        if (hasCrossProperties(block)) {
            var north = block.getProperty("north");
            var south = block.getProperty("south");
            return block.withProperty("north", south).withProperty("south", north);
        }
        return block;
    }

    private static Block rotateClockwise(Block block) {
        var facing = block.getProperty("facing");
        if (facing != null) {
            return block.withProperty("facing", rotateFacingClockwise(facing));
        }
        if (hasCrossProperties(block)) {
            var north = block.getProperty("north");
            var east = block.getProperty("east");
            var south = block.getProperty("south");
            var west = block.getProperty("west");
            return block.withProperty("north", west).withProperty("east", north)
                    .withProperty("south", east).withProperty("west", south);
        }
        return block;
    }

    private static boolean hasCrossProperties(Block block) {
        return block.getProperty("north") != null && block.getProperty("south") != null
                && block.getProperty("east") != null && block.getProperty("west") != null;
    }

    private static String mirrorFacing(String facing) {
        return switch (facing) {
            case "north" -> "south";
            case "south" -> "north";
            default -> facing;
        };
    }

    private static String rotateFacingClockwise(String facing) {
        return switch (facing) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> facing;
        };
    }

    private static String mirrorShape(String shape) {
        return switch (shape) {
            case "outer_left" -> "outer_right";
            case "outer_right" -> "outer_left";
            case "inner_left" -> "inner_right";
            case "inner_right" -> "inner_left";
            default -> shape;
        };
    }

    protected abstract static class BlockSelector {
        protected Block next = Block.AIR;

        protected abstract void next(RandomSource random, int worldX, int worldY, int worldZ, boolean isEdge);

        Block next() {
            return this.next;
        }
    }
}
