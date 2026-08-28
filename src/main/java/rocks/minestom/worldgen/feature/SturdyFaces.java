package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/**
 * Vanilla's {@code BlockState.isFaceSturdy} with {@code SupportType.FULL},
 * approximated with the collision shape. Minestom's face fullness misses
 * multi-box collision shapes whose face-adjacent box spans the full square
 * (azalea tops, hopper rims, scaffolding); vanilla reports those as sturdy.
 */
public final class SturdyFaces {
    private SturdyFaces() {
    }

    public static boolean isFaceSturdy(Block block, BlockFace face) {
        // A hopper's top face is NOT sturdy: the funnel interior punches a
        // hole through the face square (verified against a trial chamber
        // hopper with the instrumented-server vegetation patch probes).
        if (face == BlockFace.TOP
                && (block.compare(Block.AZALEA) || block.compare(Block.FLOWERING_AZALEA)
                        || block.compare(Block.SCAFFOLDING))) {
            return true;
        }
        // Vanilla LeavesBlock.getBlockSupportShape is Shapes.empty(), so no
        // leaves face is ever sturdy even though the collision box is full
        // (verified against an azalea_leaves vegetation patch probe at r24).
        if (block.name().endsWith("_leaves")) {
            return false;
        }
        // Stairs are a multi-box shape Minestom's face fullness misses: the
        // back (facing) face is full except for outer corners, an inner
        // corner adds the adjacent side, and the slab half fills top/bottom.
        if (block.name().endsWith("_stairs")) {
            var half = block.getProperty("half");
            if (("top".equals(half) && face == BlockFace.TOP)
                    || ("bottom".equals(half) && face == BlockFace.BOTTOM)) {
                return true;
            }
            var facing = block.getProperty("facing");
            var shape = block.getProperty("shape");
            if (face.name().toLowerCase().equals(facing)) {
                return !"outer_left".equals(shape) && !"outer_right".equals(shape);
            }
            if ("inner_left".equals(shape)) {
                return face.name().toLowerCase().equals(counterClockwise(facing));
            }
            if ("inner_right".equals(shape)) {
                return face.name().toLowerCase().equals(clockwise(facing));
            }
            return false;
        }
        // Mud and soul sand override the support shape to a full block, and a
        // full snow layer stack's support shape is a full cube.
        if (block.compare(Block.MUD) || block.compare(Block.SOUL_SAND)) {
            return true;
        }
        if (block.compare(Block.SNOW) && "8".equals(block.getProperty("layers"))) {
            return true;
        }
        return block.collisionShape().isFaceFull(face);
    }

    private static String clockwise(String facing) {
        return switch (facing) {
            case "north" -> "east";
            case "east" -> "south";
            case "south" -> "west";
            case "west" -> "north";
            default -> facing;
        };
    }

    private static String counterClockwise(String facing) {
        return switch (facing) {
            case "north" -> "west";
            case "west" -> "south";
            case "south" -> "east";
            case "east" -> "north";
            default -> facing;
        };
    }

    /**
     * Vanilla {@code MultifaceBlock.canAttachTo}: an attachment (vine, glow
     * lichen, sculk vein) holds when the neighbour's support shape OR
     * collision shape has a full face toward it. The support shape defaults
     * to the collision shape; mud and soul sand override it to a full block,
     * and a full snow layer stack is a full cube. {@code attachmentFace} is
     * the neighbour's face the attachment sits against (the opposite of the
     * direction from the attachment toward the neighbour).
     */
    public static boolean canAttachTo(Block neighbor, BlockFace attachmentFace) {
        if (isFaceSturdy(neighbor, attachmentFace)) {
            return true;
        }
        return neighbor.collisionShape().isFaceFull(attachmentFace);
    }
}
