package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;

/**
 * Vanilla {@code GravityProcessor} as appended by the {@code terrain_matching}
 * projection: every block column snaps to the world surface heightmap plus an
 * offset (-1), preserving the block's template-relative Y.
 */
public final class GravityProcessor implements StructureProcessor {
    private final int offset;

    public GravityProcessor(int offset) {
        this.offset = offset;
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        var sampler = context.heightSampler();
        if (sampler == null) {
            return processedBlockInfo;
        }

        var pos = processedBlockInfo.pos();
        var height = sampler.firstFreeHeight(pos.blockX(), pos.blockZ());
        if (height == Integer.MIN_VALUE) {
            return processedBlockInfo;
        }

        var newY = height + this.offset + templateRelativePos.blockY();
        return new StructureBlockInfo(
                new BlockVec(pos.blockX(), newY, pos.blockZ()),
                processedBlockInfo.state(),
                processedBlockInfo.nbt());
    }
}
