package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Transforms blocks during structure template placement, mirroring vanilla's
 * {@code StructureProcessor}.
 *
 * <p>Processors run in order on every template block; returning {@code null}
 * drops the block. States are template-space (unrotated); positions are world
 * positions.
 */
public interface StructureProcessor {

    @Nullable
    default StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        return processedBlockInfo;
    }

    /**
     * Whole-piece pass after per-block processing (capped processors).
     * {@code originalBlockInfoList} carries template-relative positions and
     * original states, index-aligned with {@code processedBlockInfoList}.
     */
    default List<StructureBlockInfo> finalizeProcessing(
            StructureProcessorContext context,
            List<StructureBlockInfo> originalBlockInfoList,
            List<StructureBlockInfo> processedBlockInfoList) {
        return processedBlockInfoList;
    }

    /**
     * When true the whole piece must be processed even for blocks outside the
     * generating chunk (vanilla's {@code evaluatesEntirePieceState}).
     */
    default boolean evaluatesEntirePieceState() {
        return false;
    }
}
