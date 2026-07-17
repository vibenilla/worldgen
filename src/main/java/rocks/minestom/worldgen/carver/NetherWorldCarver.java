package rocks.minestom.worldgen.carver;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Exact port of vanilla {@code NetherWorldCarver}: a cave carver with wider,
 * taller tunnels that skips the aquifer and floods everything below
 * {@code minY + 31} with lava.
 */
public final class NetherWorldCarver extends CaveWorldCarver {

    @Override
    protected int getCaveBound() {
        return 10;
    }

    @Override
    protected float getThickness(RandomSource random) {
        return (random.nextFloat() * 2.0F + random.nextFloat()) * 2.0F;
    }

    @Override
    protected double getYScale() {
        return 5.0;
    }

    @Override
    protected boolean carveBlock(CarvingContext context, CaveCarverConfiguration configuration, int blockX,
            int blockY, int blockZ, MutableBoolean hasGrass) {
        if (!configuration.base().canReplace(context.getBlock(blockX, blockY, blockZ))) {
            return false;
        }

        var state = blockY <= context.minGenY() + 31 ? Block.LAVA : Block.AIR;
        context.setCarved(blockX, blockY, blockZ, state);
        return true;
    }
}
