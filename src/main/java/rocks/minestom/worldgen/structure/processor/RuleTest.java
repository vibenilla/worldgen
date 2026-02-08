package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;

public interface RuleTest {
    boolean test(Block block, StructureProcessorContext context);

    record BlockMatchTest(Key block) implements RuleTest {
        @Override
        public boolean test(Block block, StructureProcessorContext context) {
            return block.key().equals(this.block);
        }
    }

    record BlockStateMatchTest(Block state) implements RuleTest {
        @Override
        public boolean test(Block block, StructureProcessorContext context) {
            return block.equals(this.state);
        }
    }

    record RandomBlockMatchTest(Key block, float probability) implements RuleTest {
        @Override
        public boolean test(Block block, StructureProcessorContext context) {
            if (!block.key().equals(this.block)) {
                return false;
            }
            return context.random().nextFloat() < this.probability;
        }
    }

    record TagMatchTest(Key tag) implements RuleTest {
        @Override
        public boolean test(Block block, StructureProcessorContext context) {
            return context.blockTags().blocks(this.tag).contains(block.key());
        }
    }
}
