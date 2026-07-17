package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Vanilla {@code RuleTest}: predicates over a block state, drawing from the
 * per-block rule random exactly like vanilla (short-circuit order matters for
 * random-draw parity).
 */
public interface RuleTest {
    boolean test(Block state, RandomSource random, BlockTagManager blockTags);

    record AlwaysTrueTest() implements RuleTest {
        public static final AlwaysTrueTest INSTANCE = new AlwaysTrueTest();

        @Override
        public boolean test(Block state, RandomSource random, BlockTagManager blockTags) {
            return true;
        }
    }

    record BlockMatchTest(Key block) implements RuleTest {
        @Override
        public boolean test(Block state, RandomSource random, BlockTagManager blockTags) {
            return state.key().equals(this.block);
        }
    }

    record BlockStateMatchTest(Block state) implements RuleTest {
        @Override
        public boolean test(Block block, RandomSource random, BlockTagManager blockTags) {
            return block.stateId() == this.state.stateId();
        }
    }

    record RandomBlockMatchTest(Key block, float probability) implements RuleTest {
        @Override
        public boolean test(Block state, RandomSource random, BlockTagManager blockTags) {
            // The nextFloat draw only happens when the block matches (vanilla
            // short-circuits), keeping the shared rule random in sync.
            return state.key().equals(this.block) && random.nextFloat() < this.probability;
        }
    }

    record RandomBlockStateMatchTest(Block state, float probability) implements RuleTest {
        @Override
        public boolean test(Block block, RandomSource random, BlockTagManager blockTags) {
            return block.stateId() == this.state.stateId() && random.nextFloat() < this.probability;
        }
    }

    record TagMatchTest(Key tag) implements RuleTest {
        @Override
        public boolean test(Block state, RandomSource random, BlockTagManager blockTags) {
            return blockTags.blocks(this.tag).contains(state.key());
        }
    }
}
