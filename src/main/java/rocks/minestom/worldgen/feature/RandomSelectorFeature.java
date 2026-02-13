package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;

import java.util.List;

public final class RandomSelectorFeature implements Feature<FeatureConfiguration> {
    private final Key defaultFeatureId;
    private final List<WeightedFeature> features;

    public RandomSelectorFeature(Key defaultFeatureId, List<WeightedFeature> features) {
        this.defaultFeatureId = defaultFeatureId;
        this.features = features;
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<FeatureConfiguration, T> context) {
        return false;
    }

    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<FeatureConfiguration, T> context, FeatureLoader loader) {
        var random = context.random();

        for (var weightedFeature : this.features) {
            if (random.nextFloat() < weightedFeature.chance) {
                var placedFeature = loader.getPlacedFeature(weightedFeature.featureId);
                if (placedFeature == null) {
                    continue;
                }

                return placePlacedFeature(context, loader, placedFeature);
            }
        }

        var defaultPlacedFeature = loader.getPlacedFeature(this.defaultFeatureId);
        if (defaultPlacedFeature == null) {
            return false;
        }

        return placePlacedFeature(context, loader, defaultPlacedFeature);
    }

    private static <T extends Block.Getter & Block.Setter> boolean placePlacedFeature(
            FeaturePlaceContext<FeatureConfiguration, T> context,
            FeatureLoader loader,
            PlacedFeature placedFeature
    ) {
        var configuredFeature = placedFeature.configuredFeature(loader);
        if (configuredFeature == null) {
            return false;
        }

        var newContext = new FeaturePlaceContext<>(
                context.accessor(),
                context.random(),
                context.origin(),
                configuredFeature.config(),
                context.worldSeed(),
                context.minY(),
                context.maxY()
        );

        var featureImpl = configuredFeature.feature();
        if (featureImpl instanceof RandomSelectorFeature randomSelector) {
            return randomSelector.place((FeaturePlaceContext) newContext, loader);
        }

        return ((Feature) featureImpl).place(newContext);
    }

    public record WeightedFeature(float chance, Key featureId) {
    }
}
