package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.RandomPatchConfiguration;

public final class FlowerFeature implements Feature<RandomPatchConfiguration> {
    private static final RandomPatchFeature PATCH_FEATURE = new RandomPatchFeature();

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<RandomPatchConfiguration, T> context) {
        return PATCH_FEATURE.place(context);
    }
}
