package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;

import java.util.HashSet;
import java.util.function.BiConsumer;

public final class TreeFeature implements Feature<TreeConfiguration> {

    public TreeFeature() {
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<TreeConfiguration, T> context) {
        var level = context.accessor();
        var random = context.random();
        var pos = context.origin();
        var config = context.config();

        var treeHeight = config.trunkPlacer().getTreeHeight(random);
        var foliageHeight = config.foliagePlacer().foliageHeight(random, treeHeight, config);
        var trunkHeight = treeHeight - foliageHeight;
        var foliageRadius = config.foliagePlacer().foliageRadius(random, trunkHeight);

        var maxFreeTreeHeight = this.getMaxFreeTreeHeight(level, treeHeight, pos, config);

        if (maxFreeTreeHeight < treeHeight) {
            var minClipped = config.minimumSize().minClippedHeight();
            if (minClipped.isEmpty() || maxFreeTreeHeight < minClipped.getAsInt()) {
                return false;
            }
        }

        var logs = new HashSet<BlockVec>();
        var leaves = new HashSet<BlockVec>();

        BiConsumer<BlockVec, Block> logSetter = (position, block) -> {
            logs.add(position);
            level.setBlock(position, block);
        };

        var leafSetter = new FoliagePlacer.FoliageSetter() {
            @Override
            public void set(BlockVec position, Block block) {
                leaves.add(position);
                level.setBlock(position, block);
            }

            @Override
            public boolean isSet(BlockVec position) {
                return leaves.contains(position);
            }
        };

        var foliageAttachments = config.trunkPlacer().placeTrunk(level, logSetter, random, maxFreeTreeHeight, pos, config);

        for (var attachment : foliageAttachments) {
            config.foliagePlacer().createFoliage(level, leafSetter, random, config, maxFreeTreeHeight, attachment, foliageHeight, foliageRadius);
        }

        return !logs.isEmpty() || !leaves.isEmpty();
    }

    private int getMaxFreeTreeHeight(Block.Getter getter, int treeHeight, BlockVec pos, TreeConfiguration config) {
        for (var height = 0; height <= treeHeight + 1; height++) {
            var size = config.minimumSize().getSizeAtHeight(treeHeight, height);

            for (var x = -size; x <= size; x++) {
                for (var z = -size; z <= size; z++) {
                    var checkPos = pos.add(x, height, z);
                    if (!config.trunkPlacer().isFree(getter, checkPos)) {
                        return height - 2;
                    }
                }
            }
        }

        return treeHeight;
    }
}
