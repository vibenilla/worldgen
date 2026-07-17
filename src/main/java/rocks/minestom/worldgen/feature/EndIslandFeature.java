package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Exact port of vanilla {@code EndIslandFeature}. Draws a small floating
 * blob of end stone below the origin: starting from a random radius of four
 * to six blocks, it fills a circular disc of end stone at each descending
 * layer, shrinking the radius by a random amount of half a block to two and
 * a half blocks between layers until the radius drops to half a block or
 * less.
 */
public final class EndIslandFeature implements Feature<NoneFeatureConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var random = context.random();
        var origin = context.origin();
        var size = (float) random.nextInt(3) + 4.0F;

        for (var y = 0; size > 0.5F; y--) {
            var minX = floor(-size);
            var maxX = ceil(size);
            var minZ = floor(-size);
            var maxZ = ceil(size);
            for (var x = minX; x <= maxX; x++) {
                for (var z = minZ; z <= maxZ; z++) {
                    if ((float) (x * x + z * z) <= (size + 1.0F) * (size + 1.0F)) {
                        level.setBlock(origin.blockX() + x, origin.blockY() + y, origin.blockZ() + z, Block.END_STONE);
                    }
                }
            }

            size -= (float) random.nextInt(2) + 0.5F;
        }

        return true;
    }

    private static int floor(float value) {
        var truncated = (int) value;
        return value < (float) truncated ? truncated - 1 : truncated;
    }

    private static int ceil(float value) {
        var truncated = (int) value;
        return value > (float) truncated ? truncated + 1 : truncated;
    }
}
