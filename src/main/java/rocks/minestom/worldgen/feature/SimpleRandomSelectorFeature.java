package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SimpleRandomSelectorConfiguration;

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
        return RandomSelectorFeature.placePlacedFeature(context, null, selectedFeature);
    }
}
