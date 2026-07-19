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
        if (face == BlockFace.TOP
                && (block.compare(Block.AZALEA) || block.compare(Block.FLOWERING_AZALEA)
                        || block.compare(Block.HOPPER) || block.compare(Block.SCAFFOLDING))) {
            return true;
        }
        return block.registry().collisionShape().isFaceFull(face);
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
        if (neighbor.compare(Block.MUD) || neighbor.compare(Block.SOUL_SAND)) {
            return true;
        }
        if (neighbor.compare(Block.SNOW) && "8".equals(neighbor.getProperty("layers"))) {
            return true;
        }
        return isFaceSturdy(neighbor, attachmentFace);
    }
}
