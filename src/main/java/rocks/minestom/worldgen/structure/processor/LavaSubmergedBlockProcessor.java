package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;

/**
 * Vanilla {@code LavaSubmergedBlockProcessor}: when the current world block at
 * a target position is lava and the block being placed is not a full cube, the
 * placed block is submerged in lava instead (the lava is kept around it).
 */
public final class LavaSubmergedBlockProcessor implements StructureProcessor {
    public static final LavaSubmergedBlockProcessor INSTANCE = new LavaSubmergedBlockProcessor();

    private LavaSubmergedBlockProcessor() {
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        var position = processedBlockInfo.pos();
        var worldBlock = context.level().getBlock(position.blockX(), position.blockY(), position.blockZ());
        var wasLavaBefore = worldBlock.compare(Block.LAVA);
        return wasLavaBefore && !isFullCube(processedBlockInfo.state())
                ? new StructureBlockInfo(position, Block.LAVA, processedBlockInfo.nbt())
                : processedBlockInfo;
    }

    private static boolean isFullCube(Block state) {
        var shape = state.registry().collisionShape();
        var start = shape.relativeStart();
        var end = shape.relativeEnd();
        return start.x() == 0.0 && start.y() == 0.0 && start.z() == 0.0
                && end.x() == 1.0 && end.y() == 1.0 && end.z() == 1.0;
    }
}
