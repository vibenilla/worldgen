package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.instance.block.Block;

public record ProcessorRule(RuleTest inputPredicate, PosRuleTest locationPredicate, Block outputState) {
    public Block apply(Block block, StructureProcessorContext context) {
        if (!this.inputPredicate.test(block, context)) {
            return block;
        }

        if (!this.locationPredicate.test()) {
            return block;
        }

        return this.outputState;
    }
}
