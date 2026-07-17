package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla's {@code CoralTreeFeature}: grows a one-to-three block
 * trunk, then two to four branches (in a shuffled horizontal direction each)
 * that climb two to six blocks, occasionally stepping sideways as they rise.
 */
public final class CoralTreeFeature extends CoralFeature {
    @Override
    protected <T extends Block.Getter & Block.Setter> boolean placeFeature(
            T level, RandomSource random, BlockVec origin, Block state) {
        var position = origin;
        var trunkHeight = random.nextInt(3) + 1;

        for (var i = 0; i < trunkHeight; i++) {
            if (!this.placeCoralBlock(level, random, position, state)) {
                return true;
            }

            position = Direction.UP.relative(position);
        }

        var trunkTop = position;
        var branchCount = random.nextInt(3) + 2;
        var directions = shuffledHorizontalDirections(random);

        for (var index = 0; index < branchCount; index++) {
            var branchDirection = directions.get(index);
            position = branchDirection.relative(trunkTop);
            var branchHeight = random.nextInt(5) + 2;
            var segmentLength = 0;

            for (var j = 0; j < branchHeight; j++) {
                if (!this.placeCoralBlock(level, random, position, state)) {
                    break;
                }

                segmentLength++;
                position = Direction.UP.relative(position);
                if (j == 0 || segmentLength >= 2 && random.nextFloat() < 0.25F) {
                    position = branchDirection.relative(position);
                    segmentLength = 0;
                }
            }
        }

        return true;
    }
}
