package rocks.minestom.worldgen.structure.template;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.utils.Direction;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a rotation around the Y-axis in 90-degree increments.
 *
 * <p>Used when placing structure templates to add variety. Rotation affects:
 * <ul>
 *   <li>Block positions within templates
 *   <li>Directional block properties (facing, axis, etc.)
 *   <li>Jigsaw block orientations
 * </ul>
 */
public enum Rotation {
    NONE,
    CLOCKWISE_90,
    CLOCKWISE_180,
    COUNTERCLOCKWISE_90;

    private static final Rotation[] VALUES = values();

    public Rotation getRotated(Rotation other) {
        return switch (other) {
            case CLOCKWISE_90 -> switch (this) {
                case NONE -> CLOCKWISE_90;
                case CLOCKWISE_90 -> CLOCKWISE_180;
                case CLOCKWISE_180 -> COUNTERCLOCKWISE_90;
                case COUNTERCLOCKWISE_90 -> NONE;
            };
            case CLOCKWISE_180 -> switch (this) {
                case NONE -> CLOCKWISE_180;
                case CLOCKWISE_90 -> COUNTERCLOCKWISE_90;
                case CLOCKWISE_180 -> NONE;
                case COUNTERCLOCKWISE_90 -> CLOCKWISE_90;
            };
            case COUNTERCLOCKWISE_90 -> switch (this) {
                case NONE -> COUNTERCLOCKWISE_90;
                case CLOCKWISE_90 -> NONE;
                case CLOCKWISE_180 -> CLOCKWISE_90;
                case COUNTERCLOCKWISE_90 -> CLOCKWISE_180;
            };
            case NONE -> this;
        };
    }

    public Direction rotate(Direction direction) {
        if (direction.normalY() != 0) {
            return direction;
        }

        return switch (this) {
            case CLOCKWISE_90 -> rotateClockwise(direction);
            case CLOCKWISE_180 -> direction.opposite();
            case COUNTERCLOCKWISE_90 -> rotateCounterClockwise(direction);
            case NONE -> direction;
        };
    }

    public BlockVec rotate(BlockVec position, BlockVec size) {
        return switch (this) {
            case CLOCKWISE_90 -> new BlockVec(
                    -position.blockZ(),
                    position.blockY(),
                    position.blockX()
            );
            case CLOCKWISE_180 -> new BlockVec(
                    -position.blockX(),
                    position.blockY(),
                    -position.blockZ()
            );
            case COUNTERCLOCKWISE_90 -> new BlockVec(
                    position.blockZ(),
                    position.blockY(),
                    -position.blockX()
            );
            case NONE -> position;
        };
    }

    public BlockVec rotateSize(BlockVec size) {
        return switch (this) {
            case CLOCKWISE_90, COUNTERCLOCKWISE_90 -> new BlockVec(size.blockZ(), size.blockY(), size.blockX());
            case CLOCKWISE_180, NONE -> size;
        };
    }

    public static Rotation getRandom(RandomSource random) {
        return VALUES[random.nextInt(VALUES.length)];
    }

    public static List<Rotation> getShuffled(RandomSource random) {
        var list = new ArrayList<>(List.of(VALUES));
        for (var index = list.size(); index > 1; index--) {
            var swapIndex = random.nextInt(index);
            var temp = list.get(index - 1);
            list.set(index - 1, list.get(swapIndex));
            list.set(swapIndex, temp);
        }
        return Collections.unmodifiableList(list);
    }

    private static Direction rotateClockwise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> direction;
        };
    }

    private static Direction rotateCounterClockwise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            case EAST -> Direction.NORTH;
            default -> direction;
        };
    }
}
