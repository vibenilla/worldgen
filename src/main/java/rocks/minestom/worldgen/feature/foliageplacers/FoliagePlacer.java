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
        var current = getter.getBlock(position);
        var persistent = "true".equals(current.getProperty("persistent"));
        if (!persistent && Feature.isValidTreePosition(getter, position)) {
            var blockState = config.foliageProvider().getState(getter, random, position);
            if (blockState.getProperty("waterlogged") != null) {
                var inWater = current.compare(Block.WATER) || "true".equals(current.getProperty("waterlogged"));
                blockState = blockState.withProperty("waterlogged", inWater ? "true" : "false");
            }
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
