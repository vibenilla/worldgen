package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

/** Prints collision face fullness for a few blocks (diagnostic). */
public final class ShapeCheck {
    public static void main(String[] args) {
        MinecraftServer.init();
        for (var block : new Block[]{Block.AZALEA, Block.FLOWERING_AZALEA, Block.MOSS_BLOCK, Block.HOPPER, Block.SHORT_GRASS, Block.BIG_DRIPLEAF}) {
            System.out.println(block.name() + " topFull=" + block.registry().collisionShape().isFaceFull(BlockFace.TOP)
                    + " bottomFull=" + block.registry().collisionShape().isFaceFull(BlockFace.BOTTOM));
        }
        System.exit(0);
    }
}
