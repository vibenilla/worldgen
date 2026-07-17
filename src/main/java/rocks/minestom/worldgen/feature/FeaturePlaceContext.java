package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * @param seaLevel vanilla {@code ChunkGenerator.getSeaLevel()} for the dimension
 *                 (32 in the nether, where it is the lava-ocean level used by
 *                 basalt columns)
 */
public record FeaturePlaceContext<C extends FeatureConfiguration, T extends Block.Getter & Block.Setter>(
        T accessor,
        RandomSource random,
        BlockVec origin,
        C config,
        long worldSeed,
        int minY,
        int maxY,
        int seaLevel
) {
}
