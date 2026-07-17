package rocks.minestom.worldgen.structure.template;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.utils.Direction;

/**
 * Mirror of vanilla {@code net.minecraft.world.level.block.Mirror}: a
 * reflection applied to a structure template before rotation.
 *
 * <p>{@link #LEFT_RIGHT} reflects across the local X axis (flips north/south,
 * negates Z). {@link #FRONT_BACK} reflects across the local Z axis (flips
 * east/west, negates X).
 */
public enum Mirror {
    NONE,
    LEFT_RIGHT,
    FRONT_BACK;

    /**
     * Vanilla {@code StructureTemplate.transform} with a zero pivot: mirror
     * first, then the position transform matches {@link Rotation#rotate}.
     */
    public BlockVec mirror(BlockVec position) {
        return switch (this) {
            case LEFT_RIGHT -> new BlockVec(position.blockX(), position.blockY(), -position.blockZ());
            case FRONT_BACK -> new BlockVec(-position.blockX(), position.blockY(), position.blockZ());
            case NONE -> position;
        };
    }

    /** Vanilla {@code Mirror.mirror(Direction)}. */
    public Direction mirror(Direction direction) {
        if (this == FRONT_BACK && (direction == Direction.EAST || direction == Direction.WEST)) {
            return direction.opposite();
        }
        if (this == LEFT_RIGHT && (direction == Direction.NORTH || direction == Direction.SOUTH)) {
            return direction.opposite();
        }
        return direction;
    }
}
