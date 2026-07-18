package rocks.minestom.worldgen.structure.template;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.utils.Direction;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Vanilla's post-placement connection-shape pass: the tail of
 * {@code StructureTemplate.placeInWorld} that recomputes fence/pane, wall, and
 * leaves connection properties against the neighbors actually present after
 * placement, run whenever {@code StructurePlaceSettings.knownShape} is false.
 *
 * <p>Vanilla drives this through {@code Block.updateShape}: fence/pane and
 * wall side connections depend only on a neighbor's block identity (never on
 * the neighbor's own connection state), so they are computed directly from
 * the final placed blocks; a stair's {@code shape} depends only on the
 * facing and half of the stair behind and in front of it. A wall's
 * {@code up} (post) property additionally depends on whatever is directly
 * above it, resolved by reprocessing walls in their placement order with a
 * single downward reactive hop, mirroring {@code level.updateNeighborsAt}.
 *
 * <p>Leaves {@code distance} is handled separately by {@link #collectLeaves}
 * and {@link #updateLeavesDistance}: vanilla only settles it through
 * scheduled ticks that run after vegetation decoration has planted whatever
 * natural trees end up near the structure, so callers defer that part of the
 * pass until after the chunk's own decoration instead of running it here.
 */
public final class StructureShapeUpdater {
    private static final Key TAG_WALLS = Key.key("minecraft:walls");
    private static final Key TAG_FENCES = Key.key("minecraft:fences");
    private static final Key TAG_WOODEN_FENCES = Key.key("minecraft:wooden_fences");
    private static final Key TAG_FENCE_GATES = Key.key("minecraft:fence_gates");
    private static final Key TAG_SHULKER_BOXES = Key.key("minecraft:shulker_boxes");
    private static final Key TAG_WALL_POST_OVERRIDE = Key.key("minecraft:wall_post_override");
    private static final Key TAG_PREVENTS_NEARBY_LEAF_DECAY = Key.key("minecraft:prevents_nearby_leaf_decay");

    private StructureShapeUpdater() {
    }

    public static void update(GenerationUnitAdapter level, BlockTagManager blockTags, List<BlockVec> placedPositions) {
        var walls = new ArrayList<BlockVec>();
        var stairs = new ArrayList<BlockVec>();

        for (var position : placedPositions) {
            var block = level.getBlock(position.blockX(), position.blockY(), position.blockZ());
            switch (classify(block)) {
                case FENCE, PANE -> updateCrossCollisionSides(level, blockTags, position, block);
                case WALL -> walls.add(position);
                case STAIRS -> stairs.add(position);
                case LEAVES, NONE -> {
                }
            }
        }

        // Stairs first: a wall's connection to a neighboring stair depends on
        // that stair's actual (rotated, mirrored) collision shape, which in
        // turn depends on its shape property - so the raw placed shape has to
        // be corrected before any wall queries it.
        for (var position : stairs) {
            updateStairShape(level, position);
        }

        // A wall's side (none/low/tall) and post (up) both depend on whatever
        // is directly above it. Vanilla resolves this through
        // Block.updateFromNeighbourShapes (a single threaded pass over one
        // block's own six neighbor directions) plus level.updateNeighborsAt
        // notifying real neighbors afterward - applied once per placed block,
        // in the structure's own block list order (bottom-to-top within a
        // palette). Reprocessing in that same order, so a wall sees whatever
        // is directly above it exactly as it stood at that point (either
        // already finalized by an earlier entry, or still the raw placed
        // state if its own turn has not come up yet), reproduces vanilla's
        // order-dependent result; the reactive notification to the real
        // neighbor below is replayed as a single, non-recursive hop, matching
        // updateNeighborsAt touching a block outside this pass's own list
        // without re-triggering that block's full explicit update.
        for (var position : walls) {
            updateWall(level, blockTags, position);
            cascadeWallBelow(level, blockTags, position);
        }
    }

    /**
     * The leaves among {@code placedPositions}, for a caller that wants to
     * defer their distance relaxation (see {@link #updateLeavesDistance})
     * until after the chunk has finished decorating.
     */
    public static List<BlockVec> collectLeaves(GenerationUnitAdapter level, List<BlockVec> placedPositions) {
        var leaves = new ArrayList<BlockVec>();
        for (var position : placedPositions) {
            var block = level.getBlock(position.blockX(), position.blockY(), position.blockZ());
            if (classify(block) == Family.LEAVES) {
                leaves.add(position);
            }
        }
        return leaves;
    }

    /**
     * Every leaf reachable from {@code seeds} by walking connected leaf
     * blocks, structure-placed or natural. Vanilla settles leaves distance
     * through scheduled ticks that chain across a whole connected canopy
     * regardless of which feature or structure placed which leaf, so a
     * structure's own leaves cannot be relaxed as an isolated set: a shorter
     * path routed through a naturally grown leaf needs that leaf relaxed
     * too, not just read as a fixed input.
     */
    public static List<BlockVec> expandConnectedLeaves(GenerationUnitAdapter level, List<BlockVec> seeds) {
        var seen = new LinkedHashSet<BlockVec>();
        var queue = new ArrayDeque<BlockVec>(seeds);
        seen.addAll(seeds);
        while (!queue.isEmpty()) {
            var position = queue.poll();
            for (var direction : Direction.values()) {
                var neighborPos = new BlockVec(
                        position.blockX() + direction.normalX(),
                        position.blockY() + direction.normalY(),
                        position.blockZ() + direction.normalZ());
                if (seen.contains(neighborPos)) {
                    continue;
                }
                if (classify(level.getBlock(neighborPos.blockX(), neighborPos.blockY(), neighborPos.blockZ())) == Family.LEAVES) {
                    seen.add(neighborPos);
                    queue.add(neighborPos);
                }
            }
        }
        return new ArrayList<>(seen);
    }

    private enum Family { FENCE, PANE, WALL, STAIRS, LEAVES, NONE }

    private static Family classify(Block block) {
        var key = block.key().value();
        if (key.endsWith("_wall")) {
            return Family.WALL;
        }
        if (key.endsWith("_fence") && !key.endsWith("_fence_gate")) {
            return Family.FENCE;
        }
        if (key.endsWith("_pane") || key.equals("iron_bars")) {
            return Family.PANE;
        }
        if (key.endsWith("_stairs")) {
            return Family.STAIRS;
        }
        if (block.getProperty("distance") != null && block.getProperty("persistent") != null) {
            return Family.LEAVES;
        }
        return Family.NONE;
    }

    private static boolean isPaneFamily(Block block) {
        var key = block.key().value();
        return key.endsWith("_pane") || key.equals("iron_bars");
    }

    /** Vanilla {@code Block.isExceptionForConnection}. */
    private static boolean isExceptionForConnection(BlockTagManager blockTags, Block block) {
        var key = block.key().value();
        if (key.endsWith("_leaves")) {
            return true;
        }
        if (key.equals("barrier") || key.equals("carved_pumpkin") || key.equals("jack_o_lantern")
                || key.equals("melon") || key.equals("pumpkin")) {
            return true;
        }
        return blockTags.blocks(TAG_SHULKER_BOXES).contains(block.key());
    }

    /** Vanilla {@code BlockState.isFaceSturdy}, approximated with the precomputed collision shape. */
    private static boolean neighborFaceFull(Block neighbor, Direction towardNeighbor) {
        var shape = neighbor.registry().collisionShape();
        return shape != null && shape.isFaceFull(BlockFace.fromDirection(towardNeighbor.opposite()));
    }

    private static boolean isSameFenceFamily(BlockTagManager blockTags, Block fence, Block neighbor) {
        if (!blockTags.blocks(TAG_FENCES).contains(neighbor.key())) {
            return false;
        }
        var fenceWooden = blockTags.blocks(TAG_WOODEN_FENCES).contains(fence.key());
        var neighborWooden = blockTags.blocks(TAG_WOODEN_FENCES).contains(neighbor.key());
        return fenceWooden == neighborWooden;
    }

    /** Vanilla {@code FenceGateBlock.connectsToDirection}. */
    private static boolean isFenceGateConnecting(BlockTagManager blockTags, Block neighbor, Direction towardNeighbor) {
        if (!blockTags.blocks(TAG_FENCE_GATES).contains(neighbor.key())) {
            return false;
        }
        var facing = parseHorizontalDirection(neighbor.getProperty("facing"));
        if (facing == null) {
            return false;
        }
        return axis(clockwise(facing)) == axis(towardNeighbor);
    }

    private static boolean fenceConnects(BlockTagManager blockTags, Block fence, Block neighbor, Direction towardNeighbor) {
        var exception = isExceptionForConnection(blockTags, neighbor);
        var faceSolid = neighborFaceFull(neighbor, towardNeighbor);
        var sameFence = isSameFenceFamily(blockTags, fence, neighbor);
        var gate = isFenceGateConnecting(blockTags, neighbor, towardNeighbor);
        return (!exception && faceSolid) || sameFence || gate;
    }

    private static boolean paneConnects(BlockTagManager blockTags, Block neighbor, Direction towardNeighbor) {
        var exception = isExceptionForConnection(blockTags, neighbor);
        var faceSolid = neighborFaceFull(neighbor, towardNeighbor);
        var samePaneFamily = isPaneFamily(neighbor);
        var wall = blockTags.blocks(TAG_WALLS).contains(neighbor.key());
        return (!exception && faceSolid) || samePaneFamily || wall;
    }

    private static boolean wallConnects(BlockTagManager blockTags, Block neighbor, Direction towardNeighbor) {
        var isWall = blockTags.blocks(TAG_WALLS).contains(neighbor.key());
        var exception = isExceptionForConnection(blockTags, neighbor);
        var faceSolid = neighborFaceFull(neighbor, towardNeighbor);
        var bars = isPaneFamily(neighbor);
        var gate = isFenceGateConnecting(blockTags, neighbor, towardNeighbor);
        return isWall || (!exception && faceSolid) || bars || gate;
    }

    private static void updateCrossCollisionSides(GenerationUnitAdapter level, BlockTagManager blockTags,
            BlockVec position, Block block) {
        var isFence = classify(block) == Family.FENCE;
        var north = neighbor(level, position, Direction.NORTH);
        var east = neighbor(level, position, Direction.EAST);
        var south = neighbor(level, position, Direction.SOUTH);
        var west = neighbor(level, position, Direction.WEST);

        var northConnects = isFence
                ? fenceConnects(blockTags, block, north, Direction.NORTH)
                : paneConnects(blockTags, north, Direction.NORTH);
        var eastConnects = isFence
                ? fenceConnects(blockTags, block, east, Direction.EAST)
                : paneConnects(blockTags, east, Direction.EAST);
        var southConnects = isFence
                ? fenceConnects(blockTags, block, south, Direction.SOUTH)
                : paneConnects(blockTags, south, Direction.SOUTH);
        var westConnects = isFence
                ? fenceConnects(blockTags, block, west, Direction.WEST)
                : paneConnects(blockTags, west, Direction.WEST);

        block = block.withProperties(Map.of(
                "north", Boolean.toString(northConnects),
                "east", Boolean.toString(eastConnects),
                "south", Boolean.toString(southConnects),
                "west", Boolean.toString(westConnects)));
        level.setBlock(position, block);
    }

    private static void updateWall(GenerationUnitAdapter level, BlockTagManager blockTags, BlockVec position) {
        var block = neighbor(level, position, null);
        var above = neighbor(level, position, Direction.UP);
        var north = neighbor(level, position, Direction.NORTH);
        var east = neighbor(level, position, Direction.EAST);
        var south = neighbor(level, position, Direction.SOUTH);
        var west = neighbor(level, position, Direction.WEST);

        var northSide = wallSide(wallConnects(blockTags, north, Direction.NORTH), above, Direction.NORTH);
        var eastSide = wallSide(wallConnects(blockTags, east, Direction.EAST), above, Direction.EAST);
        var southSide = wallSide(wallConnects(blockTags, south, Direction.SOUTH), above, Direction.SOUTH);
        var westSide = wallSide(wallConnects(blockTags, west, Direction.WEST), above, Direction.WEST);

        var aboveHasPost = classify(above) == Family.WALL && "true".equals(above.getProperty("up"));
        boolean up;
        if (aboveHasPost) {
            up = true;
        } else {
            var northNone = "none".equals(northSide);
            var southNone = "none".equals(southSide);
            var eastNone = "none".equals(eastSide);
            var westNone = "none".equals(westSide);
            var hasCorner = (northNone && southNone && westNone && eastNone) || (northNone != southNone) || (westNone != eastNone);
            if (hasCorner) {
                up = true;
            } else {
                var highNorthSouth = "tall".equals(northSide) && "tall".equals(southSide);
                var highEastWest = "tall".equals(eastSide) && "tall".equals(westSide);
                if (highNorthSouth || highEastWest) {
                    up = false;
                } else {
                    var overrideTag = blockTags.blocks(TAG_WALL_POST_OVERRIDE).contains(above.key());
                    up = overrideTag || isFaceFullDown(above);
                }
            }
        }

        block = block.withProperties(Map.of(
                "north", northSide,
                "east", eastSide,
                "south", southSide,
                "west", westSide,
                "up", Boolean.toString(up)));
        level.setBlock(position, block);
    }

    /**
     * Vanilla {@code level.updateNeighborsAt} notifying the real neighbor
     * below a just-updated wall (direction {@code UP} from that neighbor's
     * point of view), replayed as a single non-recursive hop: that neighbor
     * reacts once, but (since a reactive notification is not itself an
     * explicit placed-block update) does not go on to notify whatever is
     * below it in turn. A wall further down the same column still gets
     * its own explicit update from this pass when its own turn comes up in
     * {@code placedPositions}.
     */
    private static void cascadeWallBelow(GenerationUnitAdapter level, BlockTagManager blockTags, BlockVec position) {
        var below = new BlockVec(position.blockX(), position.blockY() - 1, position.blockZ());
        var belowBlock = level.getBlock(below.blockX(), below.blockY(), below.blockZ());
        if (classify(belowBlock) == Family.WALL) {
            updateWall(level, blockTags, below);
        }
    }

    /**
     * Vanilla {@code WallBlock.makeWallState}: {@code NONE} if the side does
     * not connect; otherwise {@code TALL} when the block above fully covers
     * that side's test column, else {@code LOW}. When the block above is
     * itself a wall, its own connection for the same direction (whatever it
     * currently is, since walls are not resolved in a fixed top-down order
     * any more) stands in for vanilla's exact voxel-shape coverage test: a
     * wall arm reaching down from above covers the column below it exactly
     * when it also connects that way.
     */
    private static String wallSide(boolean connects, Block above, Direction direction) {
        if (!connects) {
            return "none";
        }
        var coveredAbove = classify(above) == Family.WALL
                ? !"none".equals(above.getProperty(directionName(direction)))
                : isFaceFullDown(above);
        return coveredAbove ? "tall" : "low";
    }

    /**
     * Vanilla {@code StairBlock.getStairsShape}: depends only on the facing
     * and half of the stair directly behind and in front of this one (never
     * on their own shape), so unlike walls this needs no particular pass
     * order.
     */
    private static void updateStairShape(GenerationUnitAdapter level, BlockVec position) {
        var block = neighbor(level, position, null);
        var facing = parseHorizontalDirection(block.getProperty("facing"));
        var half = block.getProperty("half");
        if (facing == null || half == null) {
            return;
        }

        var behind = neighbor(level, position, facing);
        if (isStairs(behind) && half.equals(behind.getProperty("half"))) {
            var behindFacing = parseHorizontalDirection(behind.getProperty("facing"));
            if (behindFacing != null && axis(behindFacing) != axis(facing)
                    && canTakeStairShape(level, position, behindFacing.opposite(), facing, half)) {
                var shape = behindFacing == counterClockwise(facing) ? "outer_left" : "outer_right";
                level.setBlock(position, block.withProperty("shape", shape));
                return;
            }
        }

        var front = neighbor(level, position, facing.opposite());
        if (isStairs(front) && half.equals(front.getProperty("half"))) {
            var frontFacing = parseHorizontalDirection(front.getProperty("facing"));
            if (frontFacing != null && axis(frontFacing) != axis(facing)
                    && canTakeStairShape(level, position, frontFacing, facing, half)) {
                var shape = frontFacing == counterClockwise(facing) ? "inner_left" : "inner_right";
                level.setBlock(position, block.withProperty("shape", shape));
                return;
            }
        }

        level.setBlock(position, block.withProperty("shape", "straight"));
    }

    private static boolean isStairs(Block block) {
        return block.key().value().endsWith("_stairs");
    }

    /** Vanilla {@code StairBlock.canTakeShape}. */
    private static boolean canTakeStairShape(GenerationUnitAdapter level, BlockVec position, Direction towardCorner,
            Direction facing, String half) {
        var corner = neighbor(level, position, towardCorner);
        return !isStairs(corner) || !facing.name().toLowerCase().equals(corner.getProperty("facing"))
                || !half.equals(corner.getProperty("half"));
    }

    public static void updateLeavesDistance(GenerationUnitAdapter level, BlockTagManager blockTags, List<BlockVec> positions) {
        for (var pass = 0; pass < 8; pass++) {
            var changed = false;
            for (var position : positions) {
                var block = neighbor(level, position, null);
                var newDistance = 7;
                for (var direction : Direction.values()) {
                    newDistance = Math.min(newDistance, leafDistanceAt(blockTags, neighbor(level, position, direction)) + 1);
                    if (newDistance == 1) {
                        break;
                    }
                }

                var current = parseDistance(block.getProperty("distance"));
                if (newDistance != current) {
                    level.setBlock(position, block.withProperty("distance", Integer.toString(newDistance)));
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    private static int leafDistanceAt(BlockTagManager blockTags, Block block) {
        if (blockTags.blocks(TAG_PREVENTS_NEARBY_LEAF_DECAY).contains(block.key())) {
            return 0;
        }
        var distanceProperty = block.getProperty("distance");
        return distanceProperty != null ? parseDistance(distanceProperty) : 7;
    }

    private static int parseDistance(String value) {
        if (value == null) {
            return 7;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 7;
        }
    }

    private static boolean isFaceFullDown(Block block) {
        var shape = block.registry().collisionShape();
        return shape != null && shape.isFaceFull(BlockFace.BOTTOM);
    }

    private static Block neighbor(GenerationUnitAdapter level, BlockVec position, Direction direction) {
        if (direction == null) {
            return level.getBlock(position.blockX(), position.blockY(), position.blockZ());
        }
        return level.getBlock(
                position.blockX() + direction.normalX(),
                position.blockY() + direction.normalY(),
                position.blockZ() + direction.normalZ());
    }

    private static String directionName(Direction direction) {
        return switch (direction) {
            case NORTH -> "north";
            case EAST -> "east";
            case SOUTH -> "south";
            case WEST -> "west";
            default -> throw new IllegalArgumentException("Not a horizontal direction: " + direction);
        };
    }

    private static Direction parseHorizontalDirection(String name) {
        if (name == null) {
            return null;
        }
        return switch (name.toLowerCase()) {
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> null;
        };
    }

    private static Direction clockwise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> direction;
        };
    }

    private static Direction counterClockwise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> direction;
        };
    }

    private static boolean axis(Direction direction) {
        return direction == Direction.NORTH || direction == Direction.SOUTH;
    }
}
