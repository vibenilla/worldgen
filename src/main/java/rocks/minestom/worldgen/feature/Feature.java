package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;

public interface Feature<C extends FeatureConfiguration> {
    <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<C, T> context);

    static boolean isValidTreePosition(Block.Getter getter, BlockVec position) {
        var block = getter.getBlock(position);

        return block.isAir() || block.compare(Block.SHORT_GRASS) || block.compare(Block.FERN) ||
                block.compare(Block.DEAD_BUSH) || block.compare(Block.VINE) ||
                block.compare(Block.TALL_GRASS) || block.compare(Block.LARGE_FERN) ||
                block.compare(Block.WATER) || block.compare(Block.LAVA);
    }

    static boolean isDirt(Block block) {
        return block.compare(Block.DIRT) || block.compare(Block.GRASS_BLOCK) ||
                block.compare(Block.PODZOL) || block.compare(Block.COARSE_DIRT) ||
                block.compare(Block.MYCELIUM) || block.compare(Block.ROOTED_DIRT);
    }
}
