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
}
