package rocks.minestom.worldgen.structure.template;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.utils.Direction;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Vanilla's post-placement connection-shape pass: the tail of
 * {@code StructureTemplate.placeInWorld} that recomputes fence/pane, wall, and
 * leaves connection properties against the neighbors actually present after
 * placement, run whenever {@code StructurePlaceSettings.knownShape} is false.
 *
 * <p>Vanilla drives this through {@code Block.updateShape} and, for leaves,
 * a scheduled tick that eventually converges to a shortest-path distance from
 * the nearest {@code minecraft:prevents_nearby_leaf_decay} block. This port has
 * no tick scheduler during worldgen, so the pass is restructured into a direct
 * fixed-point computation that reaches the same converged result:
 * <ul>
 * <li>Fence/pane and wall side connections depend only on a neighbor's block
 * identity (never on the neighbor's own connection state), so they are
 * computed directly from the final placed blocks in a single pass.
 * <li>A wall's {@code up} (post) property additionally depends on whether the
 * block directly above is itself a wall with {@code up} already resolved, so
 * walls are finalized top-down (highest Y first) after every wall's sides are
 * known.
 * <li>Leaves {@code distance} is a shortest-path value, computed with
 * bounded relaxation (distance is capped at 7, so 8 passes always reach the
 * fixed point).
 * </ul>
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
        var leaves = new ArrayList<BlockVec>();

        for (var position : placedPositions) {
            var block = level.getBlock(position.blockX(), position.blockY(), position.blockZ());
            switch (classify(block)) {
                case FENCE, PANE -> updateCrossCollisionSides(level, blockTags, position, block);
                case WALL -> walls.add(position);
                case LEAVES -> leaves.add(position);
                case NONE -> {
                }
            }
        }

        // A wall's side (none/low/tall) and post (up) both depend on whatever
        // is directly above it. Vanilla's reactive per-direction propagation
        // (Block.updateFromNeighbourShapes threading WEST, EAST, NORTH, SOUTH,
        // DOWN, UP, plus further level.updateNeighborsAt cascades) does not
        // reduce to a single deterministic pass order in general, but
        // resolving tall stacked columns top-down (so a lower wall sees the
        // already-finalized state of the wall above it) matches vanilla for
        // the common case of a wall continuing unbroken into what is above it.
        walls.sort(Comparator.comparingInt(BlockVec::blockY).reversed());
        for (var position : walls) {
            updateWall(level, blockTags, position);
        }

        if (!leaves.isEmpty()) {
            updateLeavesDistance(level, blockTags, leaves);
        }
    }

    private enum Family { FENCE, PANE, WALL, LEAVES, NONE }

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
     * Vanilla {@code WallBlock.makeWallState}: {@code NONE} if the side does
     * not connect; otherwise {@code TALL} when the block above fully covers
     * that side's test column, else {@code LOW}. When the block above is
     * itself a wall, its own (already-resolved, since walls are finalized
     * top-down) connection for the same direction stands in for vanilla's
     * exact voxel-shape coverage test: a wall arm reaching down from above
     * covers the column below it exactly when it also connects that way.
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

    private static void updateLeavesDistance(GenerationUnitAdapter level, BlockTagManager blockTags, List<BlockVec> positions) {
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

    private static boolean axis(Direction direction) {
        return direction == Direction.NORTH || direction == Direction.SOUTH;
    }
}
