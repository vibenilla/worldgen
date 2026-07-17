package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TwistingVinesConfig;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code TwistingVinesFeature}: scatters upward-growing
 * twisting vine columns around the origin on netherrack, warped nylium or
 * warped wart blocks.
 */
public final class TwistingVinesFeature implements Feature<TwistingVinesConfig> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<TwistingVinesConfig, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        if (isInvalidPlacementLocation(level, origin)) {
            return false;
        }

        var random = context.random();
        var config = context.config();
        var spreadWidth = config.spreadWidth();
        var spreadHeight = config.spreadHeight();
        var maxHeight = config.maxHeight();

        for (var i = 0; i < spreadWidth * spreadWidth; i++) {
            var placePos = origin.add(
                    WeepingVinesFeature.nextIntInclusive(random, -spreadWidth, spreadWidth),
                    WeepingVinesFeature.nextIntInclusive(random, -spreadHeight, spreadHeight),
                    WeepingVinesFeature.nextIntInclusive(random, -spreadWidth, spreadWidth));
            var airPos = findFirstAirBlockAboveGround(level, placePos, context.minY(), context.maxY());
            if (airPos != null && !isInvalidPlacementLocation(level, airPos)) {
                var vineHeight = WeepingVinesFeature.nextIntInclusive(random, 1, maxHeight);
                if (random.nextInt(6) == 0) {
                    vineHeight *= 2;
                }

                if (random.nextInt(5) == 0) {
                    vineHeight = 1;
                }

                placeWeepingVinesColumn(level, random, airPos, vineHeight, 17, 25);
            }
        }

        return true;
    }

    /**
     * Vanilla {@code findFirstAirBlockAboveGround}: walks down until a
     * non-air block, returning the position above it, or null when the walk
     * leaves the build height.
     */
    private static BlockVec findFirstAirBlockAboveGround(Block.Getter level, BlockVec placePos, int minY, int maxY) {
        do {
            placePos = placePos.sub(0, 1, 0);
            if (placePos.blockY() < minY || placePos.blockY() > maxY) {
                return null;
            }
        } while (level.getBlock(placePos).isAir());

        return placePos.add(0, 1, 0);
    }

    /** Vanilla {@code TwistingVinesFeature.placeWeepingVinesColumn}: grows the column upward. */
    public static <T extends Block.Getter & Block.Setter> void placeWeepingVinesColumn(
            T level, RandomSource random, BlockVec placePos, int totalHeight, int minAge, int maxAge) {
        for (var height = 1; height <= totalHeight; height++) {
            if (level.getBlock(placePos).isAir()) {
                if (height == totalHeight || !level.getBlock(placePos.add(0, 1, 0)).isAir()) {
                    level.setBlock(placePos, Block.TWISTING_VINES
                            .withProperty("age", String.valueOf(WeepingVinesFeature.nextIntInclusive(random, minAge, maxAge))));
                    break;
                }

                level.setBlock(placePos, Block.TWISTING_VINES_PLANT);
            }

            placePos = placePos.add(0, 1, 0);
        }
    }

    private static boolean isInvalidPlacementLocation(Block.Getter level, BlockVec pos) {
        if (!level.getBlock(pos).isAir()) {
            return true;
        }

        var stateBelow = level.getBlock(pos.sub(0, 1, 0));
        return !stateBelow.compare(Block.NETHERRACK)
                && !stateBelow.compare(Block.WARPED_NYLIUM)
                && !stateBelow.compare(Block.WARPED_WART_BLOCK);
    }
}
