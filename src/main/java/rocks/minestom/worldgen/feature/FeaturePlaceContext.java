package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

public record FeaturePlaceContext<C extends FeatureConfiguration, T extends Block.Getter & Block.Setter>(
        T accessor,
        RandomSource random,
        BlockVec origin,
        C config,
        long worldSeed,
        int minY,
        int maxY
) {
}
