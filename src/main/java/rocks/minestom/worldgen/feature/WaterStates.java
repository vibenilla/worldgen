package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;

/**
 * Vanilla fluid-state checks: a block position holds water if it is the water
 * block itself, a waterlogged block, or one of the blocks whose fluid state is
 * inherently water without exposing a waterlogged property (seagrass, kelp and
 * bubble columns).
 */
public final class WaterStates {
    private WaterStates() {
    }

    public static boolean hasWaterFluid(Block block) {
        return block.compare(Block.WATER)
                || "true".equals(block.getProperty("waterlogged"))
                || block.compare(Block.SEAGRASS)
                || block.compare(Block.TALL_SEAGRASS)
                || block.compare(Block.KELP)
                || block.compare(Block.KELP_PLANT)
                || block.compare(Block.BUBBLE_COLUMN);
    }
}
