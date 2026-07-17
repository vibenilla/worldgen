package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

public interface BlockStateProvider {

    Block getState(RandomSource random, BlockVec position);

    /**
     * Level-aware variant, mirroring vanilla's
     * {@code BlockStateProvider.getState(WorldGenLevel, RandomSource, BlockPos)}.
     * Only providers that inspect surrounding blocks (rule based) override it.
     */
    default Block getState(Block.Getter accessor, RandomSource random, BlockVec position) {
        return this.getState(random, position);
    }

    /**
     * Mirrors vanilla's {@code getOptionalState}: rule based providers return
     * null when no rule matches and there is no fallback, everything else
     * always provides a state.
     */
    default Block getOptionalState(Block.Getter accessor, RandomSource random, BlockVec position) {
        return this.getState(accessor, random, position);
    }

    static BlockStateProvider simple(Block block) {
        return new SimpleStateProvider(block);
    }
}
