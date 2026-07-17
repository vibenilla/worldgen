package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;

import java.util.List;

/**
 * Port of vanilla's {@code SequenceFeature}: places each placed feature in
 * order, stopping at the first one that fails. Extends
 * {@link RandomSelectorFeature} so the world generator hands it the feature
 * loader for nested feature references.
 */
public final class SequenceFeature extends RandomSelectorFeature {
    private final List<PlacedFeature> features;

    public SequenceFeature(List<PlacedFeature> features) {
        super(null, List.of());
        this.features = features;
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<FeatureConfiguration, T> context, FeatureLoader loader) {
        for (var feature : this.features) {
            if (!placePlacedFeature(context, loader, feature)) {
                return false;
            }
        }

        return true;
    }
}
