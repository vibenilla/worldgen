package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SimpleRandomSelectorConfiguration;

@SuppressWarnings("unchecked")
public final class SimpleRandomSelectorFeature implements Feature<SimpleRandomSelectorConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<SimpleRandomSelectorConfiguration, T> context
    ) {
        var features = context.config().features();
        if (features.isEmpty()) {
            return false;
        }

        var selectedFeature = features.get(context.random().nextInt(features.size()));
        var configuredFeature = selectedFeature.configuredFeature(null);
        if (configuredFeature == null) {
            return false;
        }

        var placedContext = new FeaturePlaceContext<>(
                context.accessor(),
                context.random(),
                context.origin(),
                configuredFeature.config(),
                context.worldSeed(),
                context.minY(),
                context.maxY());

        var featureImplementation = configuredFeature.feature();
        if (featureImplementation instanceof RandomSelectorFeature) {
            return false;
        }

        return ((Feature) featureImplementation).place(placedContext);
    }
}
