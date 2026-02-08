package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

public interface BlockStateProvider {

    Block getState(RandomSource random, BlockVec position);

    static BlockStateProvider simple(Block block) {
        return new SimpleStateProvider(block);
    }
}
