package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Port of vanilla {@code GlowstoneFeature}: a seed glowstone block under
 * netherrack/basalt/blackstone, grown by 1500 random attach attempts that each
 * require exactly one glowstone neighbor.
 */
public final class GlowstoneFeature implements Feature<NoneFeatureConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var random = context.random();
        if (!level.getBlock(origin).air()) {
            return false;
        }

        var aboveState = level.getBlock(origin.add(0, 1, 0));
        if (!aboveState.compare(Block.NETHERRACK) && !aboveState.compare(Block.BASALT)
                && !aboveState.compare(Block.BLACKSTONE)) {
            return false;
        }

        level.setBlock(origin, Block.GLOWSTONE);

        for (var attempt = 0; attempt < 1500; attempt++) {
            var placePos = origin.add(
                    random.nextInt(8) - random.nextInt(8),
                    -random.nextInt(12),
                    random.nextInt(8) - random.nextInt(8));
            if (level.getBlock(placePos).air()) {
                var neighbours = 0;

                for (var direction : Direction.values()) {
                    if (level.getBlock(direction.relative(placePos)).compare(Block.GLOWSTONE)) {
                        neighbours++;
                    }

                    if (neighbours > 1) {
                        break;
                    }
                }

                if (neighbours == 1) {
                    level.setBlock(placePos, Block.GLOWSTONE);
                }
            }
        }

        return true;
    }
}
