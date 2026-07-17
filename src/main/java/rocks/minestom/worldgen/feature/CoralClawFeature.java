package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla's {@code CoralClawFeature}: places a base coral block, then
 * two or three claw branches around a random horizontal direction (that
 * direction, its clockwise neighbor, and its counterclockwise neighbor, in
 * shuffled order), each reaching sideways before curling inward toward the
 * claw direction.
 */
public final class CoralClawFeature extends CoralFeature {
    @Override
    protected <T extends Block.Getter & Block.Setter> boolean placeFeature(
            T level, RandomSource random, BlockVec origin, Block state) {
        if (!this.placeCoralBlock(level, random, origin, state)) {
            return false;
        }

        var clawDirection = randomHorizontalDirection(random);
        var branchCount = random.nextInt(2) + 2;
        var possibleDirections = new ArrayList<>(List.of(
                clawDirection, clockWise(clawDirection), counterClockWise(clawDirection)));
        TreeDecorator.shuffle(possibleDirections, random);

        for (var index = 0; index < branchCount; index++) {
            var branchDirection = possibleDirections.get(index);
            var position = branchDirection.relative(origin);
            var sidewayLength = random.nextInt(2) + 1;

            int inwayLength;
            Direction segmentDirection;
            if (branchDirection == clawDirection) {
                segmentDirection = clawDirection;
                inwayLength = random.nextInt(3) + 2;
            } else {
                position = Direction.UP.relative(position);
                var segmentCandidates = new Direction[]{branchDirection, Direction.UP};
                segmentDirection = segmentCandidates[random.nextInt(segmentCandidates.length)];
                inwayLength = random.nextInt(3) + 3;
            }

            for (var i = 0; i < sidewayLength; i++) {
                if (!this.placeCoralBlock(level, random, position, state)) {
                    break;
                }

                position = segmentDirection.relative(position);
            }

            position = segmentDirection.opposite().relative(position);
            position = Direction.UP.relative(position);

            for (var i = 0; i < inwayLength; i++) {
                position = clawDirection.relative(position);
                if (!this.placeCoralBlock(level, random, position, state)) {
                    break;
                }

                if (random.nextFloat() < 0.25F) {
                    position = Direction.UP.relative(position);
                }
            }
        }

        return true;
    }

    private static Direction clockWise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> throw new IllegalStateException("Unable to get clockwise facing of " + direction);
        };
    }

    private static Direction counterClockWise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> throw new IllegalStateException("Unable to get counterclockwise facing of " + direction);
        };
    }
}
