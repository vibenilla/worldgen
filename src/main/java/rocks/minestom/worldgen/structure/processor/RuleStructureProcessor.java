package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.structure.StructureRng;

import java.util.List;

/**
 * Vanilla {@code RuleProcessor}: a fresh legacy random is seeded from the
 * block's world position hash; rules are tried in order and the first match
 * replaces the state (keeping the original NBT semantics out of scope).
 */
public final class RuleStructureProcessor implements StructureProcessor {
    private final List<ProcessorRule> rules;

    public RuleStructureProcessor(List<ProcessorRule> rules) {
        this.rules = rules;
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        RandomSource random = new LegacyRandomSource(StructureRng.getSeed(
                processedBlockInfo.pos().blockX(),
                processedBlockInfo.pos().blockY(),
                processedBlockInfo.pos().blockZ()));

        for (var rule : this.rules) {
            if (rule.test(processedBlockInfo.state(), context.level(), templateRelativePos,
                    processedBlockInfo.pos(), context.referencePos(), random, context.blockTags())) {
                // Vanilla's default block entity modifier is Passthrough.
                return new StructureBlockInfo(processedBlockInfo.pos(), rule.outputState(), processedBlockInfo.nbt());
            }
        }

        return processedBlockInfo;
    }
}
