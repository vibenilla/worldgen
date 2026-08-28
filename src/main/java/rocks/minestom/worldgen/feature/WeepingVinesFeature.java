package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code WeepingVinesFeature}: a nether-wart-block blob grown
 * against the netherrack ceiling with weeping vine columns hanging below it.
 */
public final class WeepingVinesFeature implements Feature<NoneFeatureConfiguration> {
    private static final Direction[] DIRECTIONS = Direction.values();

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var random = context.random();
        if (!level.getBlock(origin).air()) {
            return false;
        }

        var stateAbove = level.getBlock(origin.add(0, 1, 0));
        if (!stateAbove.compare(Block.NETHERRACK) && !stateAbove.compare(Block.NETHER_WART_BLOCK)) {
            return false;
        }

        placeRoofNetherWart(level, random, origin);
        placeRoofWeepingVines(level, random, origin);
        return true;
    }

    private static <T extends Block.Getter & Block.Setter> void placeRoofNetherWart(T level, RandomSource random, BlockVec origin) {
        level.setBlock(origin, Block.NETHER_WART_BLOCK);

        for (var i = 0; i < 200; i++) {
            var placePos = origin.add(
                    random.nextInt(6) - random.nextInt(6),
                    random.nextInt(2) - random.nextInt(5),
                    random.nextInt(6) - random.nextInt(6));
            if (level.getBlock(placePos).air()) {
                var neighbours = 0;

                for (var direction : DIRECTIONS) {
                    var neighbourState = level.getBlock(direction.relative(placePos));
                    if (neighbourState.compare(Block.NETHERRACK) || neighbourState.compare(Block.NETHER_WART_BLOCK)) {
                        neighbours++;
                    }

                    if (neighbours > 1) {
                        break;
                    }
                }

                if (neighbours == 1) {
                    level.setBlock(placePos, Block.NETHER_WART_BLOCK);
                }
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> void placeRoofWeepingVines(T level, RandomSource random, BlockVec origin) {
        for (var i = 0; i < 100; i++) {
            var placePos = origin.add(
                    random.nextInt(8) - random.nextInt(8),
                    random.nextInt(2) - random.nextInt(7),
                    random.nextInt(8) - random.nextInt(8));
            if (level.getBlock(placePos).air()) {
                var stateAbove = level.getBlock(placePos.add(0, 1, 0));
                if (stateAbove.compare(Block.NETHERRACK) || stateAbove.compare(Block.NETHER_WART_BLOCK)) {
                    var vineHeight = nextIntInclusive(random, 1, 8);
                    if (random.nextInt(6) == 0) {
                        vineHeight *= 2;
                    }

                    if (random.nextInt(5) == 0) {
                        vineHeight = 1;
                    }

                    placeWeepingVinesColumn(level, random, placePos, vineHeight, 17, 25);
                }
            }
        }
    }

    /**
     * Vanilla {@code WeepingVinesFeature.placeWeepingVinesColumn}: grows a
     * weeping vine column downward from {@code placePos}, also reused by
     * {@link HugeFungusFeature} hat decoration.
     */
    public static <T extends Block.Getter & Block.Setter> void placeWeepingVinesColumn(
            T level, RandomSource random, BlockVec placePos, int totalHeight, int minAge, int maxAge) {
        for (var height = 0; height <= totalHeight; height++) {
            if (level.getBlock(placePos).air()) {
                if (height == totalHeight || !level.getBlock(placePos.sub(0, 1, 0)).air()) {
                    level.setBlock(placePos, Block.WEEPING_VINES
                            .withProperty("age", String.valueOf(nextIntInclusive(random, minAge, maxAge))));
                    break;
                }

                level.setBlock(placePos, Block.WEEPING_VINES_PLANT);
            }

            placePos = placePos.sub(0, 1, 0);
        }
    }

    /** Vanilla {@code Mth.nextInt}: inclusive bounds, no draw when min >= max. */
    static int nextIntInclusive(RandomSource random, int minInclusive, int maxInclusive) {
        return minInclusive >= maxInclusive ? minInclusive : minInclusive + random.nextInt(maxInclusive - minInclusive + 1);
    }
}
