package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record SimpleStateProvider(Block state) implements BlockStateProvider {

    public static final Codec<SimpleStateProvider> CODEC = StructCodec.struct(
            "state", BlockCodec.CODEC, SimpleStateProvider::state,
            SimpleStateProvider::new
    );

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        return this.state;
    }
}
