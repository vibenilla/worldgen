package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Vanilla {@code ProcessorRule}: input predicate on the template state,
 * location predicate on the current world state, position predicate on the
 * distance from the structure reference. All three share the rule random and
 * are evaluated in this exact short-circuit order.
 */
public record ProcessorRule(
        RuleTest inputPredicate,
        RuleTest locationPredicate,
        PosRuleTest positionPredicate,
        Block outputState
) {
    public boolean test(
            Block inputState,
            Block.Getter level,
            BlockVec inTemplatePos,
            BlockVec worldPos,
            BlockVec referencePos,
            RandomSource random,
            BlockTagManager blockTags) {
        return this.inputPredicate.test(inputState, random, blockTags)
                && this.locationPredicate.test(
                        level.getBlock(worldPos.blockX(), worldPos.blockY(), worldPos.blockZ()), random, blockTags)
                && this.positionPredicate.test(inTemplatePos, worldPos, referencePos, random);
    }
}
