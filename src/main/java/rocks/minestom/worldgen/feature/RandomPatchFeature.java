package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.RandomPatchConfiguration;

public final class RandomPatchFeature implements Feature<RandomPatchConfiguration> {

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<RandomPatchConfiguration, T> context) {
        var randomPatchConfiguration = context.config();
        var placedFeature = randomPatchConfiguration.feature();
        var configuredFeature = placedFeature.configuredFeature(null);
        if (configuredFeature == null) {
            return false;
        }

        var origin = context.origin();
        var placed = false;

        for (var attemptIndex = 0; attemptIndex < randomPatchConfiguration.tries(); attemptIndex++) {
            var offsetX = context.random().nextInt(randomPatchConfiguration.xzSpread() + 1) - context.random().nextInt(randomPatchConfiguration.xzSpread() + 1);
            var offsetY = context.random().nextInt(randomPatchConfiguration.ySpread() + 1) - context.random().nextInt(randomPatchConfiguration.ySpread() + 1);
            var offsetZ = context.random().nextInt(randomPatchConfiguration.xzSpread() + 1) - context.random().nextInt(randomPatchConfiguration.xzSpread() + 1);
            var targetPosition = new BlockVec(origin.blockX() + offsetX, origin.blockY() + offsetY, origin.blockZ() + offsetZ);

            if (targetPosition.blockY() < context.minY() || targetPosition.blockY() > context.maxY()) {
                continue;
            }

            var innerContext = new FeaturePlaceContext<>(
                    context.accessor(),
                    context.random(),
                    targetPosition,
                    configuredFeature.config(),
                    context.worldSeed(),
                    context.minY(),
                    context.maxY());

            var featureImpl = configuredFeature.feature();
            if (featureImpl instanceof RandomSelectorFeature randomSelectorFeature) {
                continue;
            }

            if (((Feature) featureImpl).place(innerContext)) {
                placed = true;
            }
        }

        return placed;
    }
}
