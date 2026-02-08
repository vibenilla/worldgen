package rocks.minestom.worldgen.feature.trunkplacers;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;
import java.util.function.BiConsumer;

public interface TrunkPlacer {

    List<FoliagePlacer.FoliageAttachment> placeTrunk(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            int freeTreeHeight,
            BlockVec basePos,
            TreeConfiguration config
    );

    int getTreeHeight(RandomSource random);

    default boolean placeLog(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            BlockVec pos,
            TreeConfiguration config
    ) {
        if (this.isValidTreePosition(getter, pos)) {
            logSetter.accept(pos, config.trunkProvider().getState(random, pos));
            return true;
        }
        return false;
    }

    default boolean isValidTreePosition(Block.Getter getter, BlockVec pos) {
        return Feature.isValidTreePosition(getter, pos);
    }

    default boolean isFree(Block.Getter getter, BlockVec pos) {
        if (!this.isValidTreePosition(getter, pos)) {
            var block = getter.getBlock(pos);

            return block.compare(Block.OAK_LOG) || block.compare(Block.SPRUCE_LOG) ||
                    block.compare(Block.BIRCH_LOG) || block.compare(Block.JUNGLE_LOG) ||
                    block.compare(Block.ACACIA_LOG) || block.compare(Block.DARK_OAK_LOG) ||
                    block.compare(Block.CHERRY_LOG) || block.compare(Block.MANGROVE_LOG);
        }

        return true;
    }

    static void setDirtAt(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> blockSetter,
            RandomSource random,
            BlockVec pos,
            TreeConfiguration config
    ) {
        var block = getter.getBlock(pos);
        if (config.forceDirt() || !Feature.isDirt(block) || block.compare(Block.GRASS_BLOCK) || block.compare(Block.MYCELIUM)) {
            blockSetter.accept(pos, config.dirtProvider().getState(random, pos));
        }
    }
}
