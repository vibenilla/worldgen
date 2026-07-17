package rocks.minestom.worldgen.structure.processor;

import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.structure.StructureRng;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla {@code CappedProcessor}: applies a delegate processor to at most
 * {@code limit} blocks of the piece, visiting indices in a shuffled order
 * drawn from a world-seed positional random at the piece origin.
 */
public final class CappedProcessor implements StructureProcessor {
    private final StructureProcessor delegate;
    private final int limit;

    public CappedProcessor(StructureProcessor delegate, int limit) {
        this.delegate = delegate;
        this.limit = limit;
    }

    @Override
    public boolean evaluatesEntirePieceState() {
        return true;
    }

    @Override
    public List<StructureBlockInfo> finalizeProcessing(
            StructureProcessorContext context,
            List<StructureBlockInfo> originalBlockInfoList,
            List<StructureBlockInfo> processedBlockInfoList) {
        if (this.limit == 0 || processedBlockInfoList.isEmpty()
                || originalBlockInfoList.size() != processedBlockInfoList.size()) {
            return processedBlockInfoList;
        }

        // RandomSource.createThreadLocalInstance(seed).forkPositional().at(piecePos),
        // with vanilla's int-overflow position hash.
        var piecePos = context.piecePosition();
        var positionalSeed = new LegacyRandomSource(context.worldSeed()).nextLong();
        var random = new LegacyRandomSource(StructureRng.getSeed(
                piecePos.blockX(), piecePos.blockY(), piecePos.blockZ()) ^ positionalSeed);
        var maxToReplace = Math.min(this.limit, processedBlockInfoList.size());
        if (maxToReplace < 1) {
            return processedBlockInfoList;
        }

        var indices = new ArrayList<Integer>(processedBlockInfoList.size());
        for (var index = 0; index < processedBlockInfoList.size(); index++) {
            indices.add(index);
        }
        StructureRng.shuffle(indices, random);

        var replaced = 0;
        for (var index : indices) {
            if (replaced >= maxToReplace) {
                break;
            }

            var originalBlockInfo = originalBlockInfoList.get(index);
            var processedBlockInfo = processedBlockInfoList.get(index);
            var maybeAltered = this.delegate.processBlock(context, originalBlockInfo.pos(), processedBlockInfo);
            if (maybeAltered != null && !processedBlockInfo.equals(maybeAltered)) {
                replaced++;
                processedBlockInfoList.set(index, maybeAltered);
            }
        }

        return processedBlockInfoList;
    }
}
