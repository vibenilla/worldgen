package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla's {@code CoralMushroomFeature}: fills the edge shell of a
 * three-to-six block box (sunk one to three blocks below the origin) with
 * coral blocks, skipping edge columns and randomly skipping individual
 * positions. The awkward all-{@code &&} condition with an empty body mirrors
 * vanilla exactly: every operand, including the random draw and the
 * placement attempt, must run in the same short-circuited order for the
 * random sequence to match.
 */
public final class CoralMushroomFeature extends CoralFeature {
    @Override
    protected <T extends Block.Getter & Block.Setter> boolean placeFeature(
            T level, RandomSource random, BlockVec origin, Block state) {
        var height = random.nextInt(3) + 3;
        var width = random.nextInt(3) + 3;
        var length = random.nextInt(3) + 3;
        var sinkValue = random.nextInt(3) + 1;

        for (var x = 0; x <= width; x++) {
            for (var y = 0; y <= height; y++) {
                for (var z = 0; z <= length; z++) {
                    var position = origin.add(x, y - sinkValue, z);
                    if ((x != 0 && x != width || y != 0 && y != height)
                            && (z != 0 && z != length || y != 0 && y != height)
                            && (x != 0 && x != width || z != 0 && z != length)
                            && (x == 0 || x == width || y == 0 || y == height || z == 0 || z == length)
                            && !(random.nextFloat() < 0.1F)
                            && !this.placeCoralBlock(level, random, position, state)) {
                    }
                }
            }
        }

        return true;
    }
}
