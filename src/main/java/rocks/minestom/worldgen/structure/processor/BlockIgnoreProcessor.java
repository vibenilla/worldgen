package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;

import java.util.List;

/**
 * Vanilla {@code BlockIgnoreProcessor}: drops template blocks whose block type
 * is in the ignore list. Single pool elements ignore structure blocks; legacy
 * elements additionally ignore air (appended last in the chain).
 */
public final class BlockIgnoreProcessor implements StructureProcessor {
    public static final BlockIgnoreProcessor STRUCTURE_BLOCK =
            new BlockIgnoreProcessor(List.of(Key.key("minecraft:structure_block")));
    public static final BlockIgnoreProcessor STRUCTURE_AND_AIR =
            new BlockIgnoreProcessor(List.of(Key.key("minecraft:air"), Key.key("minecraft:structure_block")));

    private final List<Key> toIgnore;

    public BlockIgnoreProcessor(List<Key> toIgnore) {
        this.toIgnore = toIgnore;
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        return this.toIgnore.contains(processedBlockInfo.state().key()) ? null : processedBlockInfo;
    }
}
