package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;

import java.util.List;

/**
 * Port of vanilla's {@code WeightedRandomSelectorFeature}: picks one placed
 * feature from a weighted list and places it. Extends
 * {@link RandomSelectorFeature} so the world generator hands it the feature
 * loader for nested feature references.
 */
public final class WeightedRandomSelectorFeature extends RandomSelectorFeature {
    private final List<WeightedPlacedFeature> features;
    private final int totalWeight;

    public WeightedRandomSelectorFeature(List<WeightedPlacedFeature> features) {
        super(null, List.of());
        this.features = features;

        var weight = 0;
        for (var feature : features) {
            weight += feature.weight();
        }
        this.totalWeight = weight;
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<FeatureConfiguration, T> context, FeatureLoader loader) {
        // Vanilla WeightedList.getRandom: no random is consumed when the list is empty.
        if (this.totalWeight <= 0) {
            return false;
        }

        var remaining = context.random().nextInt(this.totalWeight);
        for (var feature : this.features) {
            remaining -= feature.weight();
            if (remaining < 0) {
                return placePlacedFeature(context, loader, feature.feature());
            }
        }

        return false;
    }

    public record WeightedPlacedFeature(PlacedFeature feature, int weight) {
    }
}
