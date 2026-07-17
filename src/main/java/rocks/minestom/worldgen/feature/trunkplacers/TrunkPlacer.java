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
            logSetter.accept(pos, config.trunkProvider().getState(getter, random, pos));
            return true;
        }
        return false;
    }

    default boolean isValidTreePosition(Block.Getter getter, BlockVec pos) {
        return Feature.isValidTreePosition(getter, pos);
    }

    default boolean isFree(Block.Getter getter, BlockVec pos) {
        return this.isValidTreePosition(getter, pos) || isLog(getter.getBlock(pos));
    }

    /**
     * Vanilla's {@code #minecraft:logs} block tag: every log/wood variant plus
     * bamboo blocks and the nether stems.
     */
    static boolean isLog(Block block) {
        var name = block.name();
        return name.endsWith("_log") || name.endsWith("_wood")
                || name.equals("minecraft:bamboo_block") || name.equals("minecraft:stripped_bamboo_block")
                || name.equals("minecraft:crimson_stem") || name.equals("minecraft:stripped_crimson_stem")
                || name.equals("minecraft:warped_stem") || name.equals("minecraft:stripped_warped_stem");
    }

    /**
     * Vanilla 26.2 {@code placeBelowTrunkBlock}: the below trunk provider (a
     * rule based provider in the vanilla datapack) decides whether anything is
     * placed. When it provides no state, the position is left untouched.
     */
    static void setDirtAt(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> blockSetter,
            RandomSource random,
            BlockVec pos,
            TreeConfiguration config
    ) {
        var provider = config.belowTrunkProvider();
        if (provider == null) {
            return;
        }

        var state = provider.getOptionalState(getter, random, pos);
        if (state != null) {
            blockSetter.accept(pos, state);
        }
    }
}
