package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.ProbabilityConfiguration;

/**
 * Port of vanilla {@code SeagrassFeature}: short or tall seagrass scattered on
 * the ocean floor around the origin.
 */
public final class SeagrassFeature implements Feature<ProbabilityConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<ProbabilityConfiguration, T> context) {
        var random = context.random();
        var level = context.accessor();
        var origin = context.origin();
        var offsetX = random.nextInt(8) - random.nextInt(8);
        var offsetZ = random.nextInt(8) - random.nextInt(8);
        var floorY = level instanceof GenerationUnitAdapter adapter
                ? adapter.getHeight(origin.blockX() + offsetX, origin.blockZ() + offsetZ)
                : origin.blockY();
        var grassPos = new BlockVec(origin.blockX() + offsetX, floorY, origin.blockZ() + offsetZ);

        if (!level.getBlock(grassPos).compare(Block.WATER)) {
            return false;
        }

        var tall = random.nextDouble() < context.config().probability();
        if (!level.getBlock(grassPos.add(0, -1, 0)).isSolid()) {
            return false;
        }

        if (tall) {
            var above = grassPos.add(0, 1, 0);
            if (level.getBlock(above).compare(Block.WATER)) {
                level.setBlock(grassPos, Block.TALL_SEAGRASS.withProperty("half", "lower"));
                level.setBlock(above, Block.TALL_SEAGRASS.withProperty("half", "upper"));
            }
        } else {
            level.setBlock(grassPos, Block.SEAGRASS);
        }

        return true;
    }
}
