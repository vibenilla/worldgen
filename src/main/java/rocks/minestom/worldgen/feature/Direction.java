package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;

/**
 * Mirror of vanilla's {@code net.minecraft.core.Direction}. Declaration order
 * matches vanilla so {@link #getRandom(RandomSource)} consumes the random in
 * the exact same way, and {@link #HORIZONTAL} matches
 * {@code Direction.Plane.HORIZONTAL} iteration order.
 */
public enum Direction {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public static final List<Direction> HORIZONTAL = List.of(NORTH, EAST, SOUTH, WEST);
    /** Vanilla {@code Direction.BY_2D_DATA}: horizontal directions sorted by legacy 2D data value. */
    private static final List<Direction> BY_2D_DATA = List.of(SOUTH, WEST, NORTH, EAST);
    private static final Direction[] VALUES = values();

    private final int stepX;
    private final int stepY;
    private final int stepZ;

    Direction(int stepX, int stepY, int stepZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public int stepX() {
        return this.stepX;
    }

    public int stepY() {
        return this.stepY;
    }

    public int stepZ() {
        return this.stepZ;
    }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    public BlockVec relative(BlockVec position) {
        return position.add(this.stepX, this.stepY, this.stepZ);
    }

    public String serializedName() {
        return this.name().toLowerCase();
    }

    public net.minestom.server.instance.block.BlockFace blockFace() {
        return switch (this) {
            case DOWN -> net.minestom.server.instance.block.BlockFace.BOTTOM;
            case UP -> net.minestom.server.instance.block.BlockFace.TOP;
            case NORTH -> net.minestom.server.instance.block.BlockFace.NORTH;
            case SOUTH -> net.minestom.server.instance.block.BlockFace.SOUTH;
            case WEST -> net.minestom.server.instance.block.BlockFace.WEST;
            case EAST -> net.minestom.server.instance.block.BlockFace.EAST;
        };
    }

    public static Direction getRandom(RandomSource random) {
        return VALUES[random.nextInt(VALUES.length)];
    }

    public static Direction fromSerializedName(String name) {
        return valueOf(name.toUpperCase());
    }

    /** Vanilla {@code Direction.getClockWise()}: Y-axis clockwise rotation of a horizontal direction. */
    public Direction getClockWise() {
        return switch (this) {
            case NORTH -> EAST;
            case SOUTH -> WEST;
            case WEST -> NORTH;
            case EAST -> SOUTH;
            default -> throw new IllegalStateException("Unable to get Y-rotated facing of " + this);
        };
    }

    /** Vanilla {@code Direction.getCounterClockWise()}: Y-axis counter-clockwise rotation of a horizontal direction. */
    public Direction getCounterClockWise() {
        return switch (this) {
            case NORTH -> WEST;
            case SOUTH -> EAST;
            case WEST -> SOUTH;
            case EAST -> NORTH;
            default -> throw new IllegalStateException("Unable to get CCW facing of " + this);
        };
    }

    /** Vanilla {@code Direction.from2DDataValue(int)}. */
    public static Direction from2DDataValue(int data) {
        var length = BY_2D_DATA.size();
        var index = Math.abs(data % length);
        return BY_2D_DATA.get(index);
    }

    public static final net.minestom.server.codec.Codec<Direction> CODEC =
            net.minestom.server.codec.Codec.STRING.transform(Direction::fromSerializedName, Direction::serializedName);
}
