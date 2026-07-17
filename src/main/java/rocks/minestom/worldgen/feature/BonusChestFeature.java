package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;

/**
 * Port of vanilla {@code BonusChestFeature}: places a chest with surrounding
 * torches at the first free position found while scanning a shuffled order of
 * the origin chunk's columns. Loot table assignment is out of scope for this
 * library; the chest is placed with no inventory contents.
 */
public final class BonusChestFeature implements Feature<NoneFeatureConfiguration> {

    /** Level types that can answer MOTION_BLOCKING_NO_LEAVES queries in tests. */
    public interface MotionBlockingNoLeaves {
        int motionBlockingNoLeavesHeight(int x, int z);
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var random = context.random();
        var level = context.accessor();
        var origin = context.origin();
        var chunkX = origin.blockX() >> 4;
        var chunkZ = origin.blockZ() >> 4;
        var minBlockX = chunkX << 4;
        var minBlockZ = chunkZ << 4;

        var xPositions = shuffledRange(minBlockX, minBlockX + 15, random);
        var zPositions = shuffledRange(minBlockZ, minBlockZ + 15, random);

        for (var x : xPositions) {
            for (var z : zPositions) {
                var height = motionBlockingNoLeavesHeight(level, x, z);
                var chestPosition = new BlockVec(x, height, z);
                var chestBlock = level.getBlock(chestPosition);
                if (chestBlock.isAir() || !chestBlock.isSolid()) {
                    level.setBlock(chestPosition, Block.CHEST);

                    for (var direction : Direction.HORIZONTAL) {
                        var torchPosition = direction.relative(chestPosition);
                        if (canSurviveTorch(level, torchPosition)) {
                            level.setBlock(torchPosition, Block.TORCH);
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    private static <T extends Block.Getter> int motionBlockingNoLeavesHeight(T level, int x, int z) {
        if (level instanceof GenerationUnitAdapter adapter) {
            return adapter.heightmap(GenerationUnitAdapter.HeightmapType.MOTION_BLOCKING_NO_LEAVES, x, z);
        }

        if (level instanceof MotionBlockingNoLeaves motionBlockingNoLeaves) {
            return motionBlockingNoLeaves.motionBlockingNoLeavesHeight(x, z);
        }

        return 0;
    }

    private static <T extends Block.Getter> boolean canSurviveTorch(T level, BlockVec position) {
        var below = level.getBlock(position.sub(0, 1, 0));
        return below.isSolid();
    }

    private static ArrayList<Integer> shuffledRange(int minInclusive, int maxInclusive, RandomSource random) {
        var values = new ArrayList<Integer>();
        for (var value = minInclusive; value <= maxInclusive; value++) {
            values.add(value);
        }

        for (var index = values.size(); index > 1; index--) {
            var swapTo = random.nextInt(index);
            var swapped = values.get(index - 1);
            values.set(index - 1, values.get(swapTo));
            values.set(swapTo, swapped);
        }

        return values;
    }
}
