package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.BlockBlobConfiguration;

/**
 * Port of vanilla's {@code BlockBlobFeature} (used by {@code forest_rock}).
 */
public final class BlockBlobFeature implements Feature<BlockBlobConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<BlockBlobConfiguration, T> context) {
        var origin = context.origin();
        var level = context.accessor();
        var random = context.random();
        var config = context.config();
        var predicateContext = Feature.predicateContext(level, context.minY(), context.maxY());

        while (origin.blockY() > context.minY() + 3 && !config.canPlaceOn().test(predicateContext, origin.sub(0, 1, 0))) {
            origin = origin.sub(0, 1, 0);
        }

        if (origin.blockY() <= context.minY() + 3) {
            return false;
        }

        for (var blob = 0; blob < 3; blob++) {
            var xr = random.nextInt(2);
            var yr = random.nextInt(2);
            var zr = random.nextInt(2);
            var tr = (xr + yr + zr) * 0.333F + 0.5F;

            for (var x = origin.blockX() - xr; x <= origin.blockX() + xr; x++) {
                for (var y = origin.blockY() - yr; y <= origin.blockY() + yr; y++) {
                    for (var z = origin.blockZ() - zr; z <= origin.blockZ() + zr; z++) {
                        var dx = x - origin.blockX();
                        var dy = y - origin.blockY();
                        var dz = z - origin.blockZ();
                        if (dx * dx + dy * dy + dz * dz <= tr * tr) {
                            level.setBlock(x, y, z, config.state());
                        }
                    }
                }
            }

            origin = origin.add(-1 + random.nextInt(2), -random.nextInt(2), -1 + random.nextInt(2));
        }

        return true;
    }
}
