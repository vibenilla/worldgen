package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;

/**
 * Port of vanilla's {@code RandomBooleanSelectorFeature}: a coin flip picks
 * one of two placed features. Extends {@link RandomSelectorFeature} so the
 * world generator hands it the feature loader for nested feature references.
 */
public final class RandomBooleanSelectorFeature extends RandomSelectorFeature {
    private final PlacedFeature featureTrue;
    private final PlacedFeature featureFalse;

    public RandomBooleanSelectorFeature(PlacedFeature featureTrue, PlacedFeature featureFalse) {
        super(null, java.util.List.of());
        this.featureTrue = featureTrue;
        this.featureFalse = featureFalse;
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<FeatureConfiguration, T> context, FeatureLoader loader) {
        var selected = context.random().nextBoolean() ? this.featureTrue : this.featureFalse;
        return placePlacedFeature(context, loader, selected);
    }
}
