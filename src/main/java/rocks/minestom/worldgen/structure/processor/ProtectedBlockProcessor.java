package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;

/**
 * Vanilla {@code ProtectedBlockProcessor}: refuses to replace world blocks in
 * the given tag (e.g. {@code minecraft:features_cannot_replace}).
 */
public final class ProtectedBlockProcessor implements StructureProcessor {
    private final Key cannotReplaceTag;

    public ProtectedBlockProcessor(Key cannotReplaceTag) {
        this.cannotReplaceTag = cannotReplaceTag;
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        var pos = processedBlockInfo.pos();
        var worldBlock = context.level().getBlock(pos.blockX(), pos.blockY(), pos.blockZ());
        return context.blockTags().blocks(this.cannotReplaceTag).contains(worldBlock.key())
                ? null
                : processedBlockInfo;
    }
}
