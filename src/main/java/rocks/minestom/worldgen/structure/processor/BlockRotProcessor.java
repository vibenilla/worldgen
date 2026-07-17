package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.structure.StructureRng;

/**
 * Vanilla {@code BlockRotProcessor}: each (rottable) block survives with
 * probability {@code integrity}, drawn from a fresh position-seeded random.
 */
public final class BlockRotProcessor implements StructureProcessor {
    @Nullable
    private final Key rottableBlocksTag;
    private final float integrity;

    public BlockRotProcessor(@Nullable Key rottableBlocksTag, float integrity) {
        this.rottableBlocksTag = rottableBlocksTag;
        this.integrity = integrity;
    }

    @Nullable
    public Key rottableBlocksTag() {
        return this.rottableBlocksTag;
    }

    public float integrity() {
        return this.integrity;
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        var pos = processedBlockInfo.pos();
        var random = new LegacyRandomSource(StructureRng.getSeed(pos.blockX(), pos.blockY(), pos.blockZ()));
        var rottable = this.rottableBlocksTag == null
                || context.blockTags().blocks(this.rottableBlocksTag).contains(processedBlockInfo.state().key());
        return rottable && !(random.nextFloat() <= this.integrity) ? null : processedBlockInfo;
    }
}
