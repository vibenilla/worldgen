package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public interface FoliagePlacer {

    void createFoliage(
            Block.Getter getter,
            FoliageSetter foliageSetter,
            RandomSource random,
            TreeConfiguration config,
            int maxFreeTreeHeight,
            FoliageAttachment attachment,
            int foliageHeight,
            int foliageRadius
    );

    int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config);

    int foliageRadius(RandomSource random, int baseHeight);

    static boolean tryPlaceLeaf(
            Block.Getter getter,
            FoliageSetter setter,
            RandomSource random,
            TreeConfiguration config,
            BlockVec position
    ) {
        if (Feature.isValidTreePosition(getter, position)) {
            var blockState = config.foliageProvider().getState(random, position);
            setter.set(position, blockState);
            return true;
        }
        return false;
    }

    record FoliageAttachment(BlockVec pos, int radiusOffset, boolean doubleTrunk) {
    }

    interface FoliageSetter {
        void set(BlockVec position, Block block);

        boolean isSet(BlockVec position);
    }
}
