package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.instance.block.Block;

import java.util.List;

public final class RuleStructureProcessor implements StructureProcessor {
    private final List<ProcessorRule> rules;

    public RuleStructureProcessor(List<ProcessorRule> rules) {
        this.rules = rules;
    }

    @Override
    public Block process(Block block, StructureProcessorContext context) {
        for (var rule : this.rules) {
            var result = rule.apply(block, context);
            if (!result.equals(block)) {
                return result;
            }
        }
        return block;
    }
}
