package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Port of vanilla {@code KelpFeature}: kelp columns growing from the ocean floor.
 */
public final class KelpFeature implements Feature<NoneFeatureConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var placed = 0;
        var level = context.accessor();
        var origin = context.origin();
        var random = context.random();
        var floorY = level instanceof GenerationUnitAdapter adapter
                ? adapter.getHeight(origin.blockX(), origin.blockZ())
                : origin.blockY();
        var kelpPos = new BlockVec(origin.blockX(), floorY, origin.blockZ());

        if (isWater(level.getBlock(kelpPos))) {
            var height = 1 + random.nextInt(10);

            for (var step = 0; step <= height; step++) {
                if (isWater(level.getBlock(kelpPos)) && isWater(level.getBlock(kelpPos.add(0, 1, 0)))
                        && canSurvive(level, kelpPos)) {
                    if (step == height) {
                        level.setBlock(kelpPos, Block.KELP.withProperty("age", String.valueOf(random.nextInt(4) + 20)));
                        placed++;
                    } else {
                        level.setBlock(kelpPos, Block.KELP_PLANT);
                    }
                } else if (step > 0) {
                    var below = kelpPos.add(0, -1, 0);
                    if (canSurvive(level, below) && !level.getBlock(below.add(0, -1, 0)).compare(Block.KELP)) {
                        level.setBlock(below, Block.KELP.withProperty("age", String.valueOf(random.nextInt(4) + 20)));
                        placed++;
                    }
                    break;
                }

                kelpPos = kelpPos.add(0, 1, 0);
            }
        }

        return placed > 0;
    }

    private static boolean isWater(Block block) {
        return block.compare(Block.WATER);
    }

    private static <T extends Block.Getter & Block.Setter> boolean canSurvive(T level, BlockVec position) {
        var below = level.getBlock(position.add(0, -1, 0));
        return below.isSolid() || below.compare(Block.KELP) || below.compare(Block.KELP_PLANT);
    }
}
