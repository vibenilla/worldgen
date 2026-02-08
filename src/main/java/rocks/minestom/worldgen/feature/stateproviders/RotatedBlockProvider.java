package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * A block state provider that provides a block with random axis rotation.
 * Used for hay bales and other blocks that can be oriented on different axes.
 */
public record RotatedBlockProvider(Block state) implements BlockStateProvider {

    public static final Codec<RotatedBlockProvider> CODEC = StructCodec.struct(
            "state", BlockCodec.CODEC, RotatedBlockProvider::state,
            RotatedBlockProvider::new);

    private static final String[] AXES = { "x", "y", "z" };

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        var axis = state.getProperty("axis");
        if (axis != null) {
            // Randomly rotate to one of the three axes
            var newAxis = AXES[random.nextInt(AXES.length)];
            return state.withProperty("axis", newAxis);
        }
        return this.state;
    }
}
