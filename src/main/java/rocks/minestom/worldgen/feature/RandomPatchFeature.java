package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.RandomPatchConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementContext;

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
        var chunkStartX = Math.floorDiv(origin.blockX(), 16) * 16;
        var chunkStartZ = Math.floorDiv(origin.blockZ(), 16) * 16;
        var surfaceHeights = new int[16 * 16];
        var waterHeights = new int[16 * 16];
        for (var index = 0; index < surfaceHeights.length; index++) {
            surfaceHeights[index] = context.minY();
            waterHeights[index] = Integer.MIN_VALUE;
        }

        var placementContext = new PlacementContext(
                context.accessor(),
                chunkStartX,
                chunkStartZ,
                16,
                16,
                surfaceHeights,
                waterHeights,
                context.minY(),
                context.maxY(),
                context.minY(),
                null,
                null);

        var placed = false;
        var xzRange = randomPatchConfiguration.xzSpread() + 1;
        var yRange = randomPatchConfiguration.ySpread() + 1;

        for (var attemptIndex = 0; attemptIndex < randomPatchConfiguration.tries(); attemptIndex++) {
            var offsetX = context.random().nextInt(xzRange) - context.random().nextInt(xzRange);
            var offsetY = context.random().nextInt(yRange) - context.random().nextInt(yRange);
            var offsetZ = context.random().nextInt(xzRange) - context.random().nextInt(xzRange);
            var targetPosition = new BlockVec(origin.blockX() + offsetX, origin.blockY() + offsetY, origin.blockZ() + offsetZ);

            var placementPositions = placedFeature.getPositions(placementContext, context.random(), targetPosition);
            for (var placementPosition : placementPositions) {
                if (placementPosition.blockY() < context.minY() || placementPosition.blockY() > context.maxY()) {
                    continue;
                }

                var innerContext = new FeaturePlaceContext<>(
                        context.accessor(),
                        context.random(),
                        placementPosition,
                        configuredFeature.config(),
                        context.worldSeed(),
                        context.minY(),
                        context.maxY());

                var featureImpl = configuredFeature.feature();
                if (featureImpl instanceof RandomSelectorFeature) {
                    continue;
                }

                if (((Feature) featureImpl).place(innerContext)) {
                    placed = true;
                }
            }
        }

        return placed;
    }
}
