package rocks.minestom.worldgen.structure.template;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import rocks.minestom.worldgen.structure.assembly.JigsawAssembler;

/**
 * Parsed data from a jigsaw block in a structure template.
 *
 * <p>Jigsaw blocks are the connection points used during structure assembly.
 * Each jigsaw defines:
 * <ul>
 *   <li>{@code pool} - The template pool to draw connecting pieces from
 *   <li>{@code name} - This jigsaw's identifier (matched by other jigsaws' targets)
 *   <li>{@code target} - The name to look for on connecting pieces
 *   <li>{@code front} - Direction the jigsaw faces (where pieces connect)
 *   <li>{@code jointType} - How rotations are constrained at this connection
 * </ul>
 *
 * <p>Two jigsaws can connect if their fronts face each other and the first's
 * target matches the second's name.
 *
 * @see JigsawAssembler for how jigsaws are used during assembly
 */
public record JigsawBlockInfo(
        BlockVec position,
        Key pool,
        String name,
        String target,
        JointType jointType,
        Direction front,
        Direction top,
        int placementPriority,
        String finalState
) {
    private static final Key EMPTY_KEY = Key.key("minecraft:empty");
    private static final String EMPTY_NAME = "minecraft:empty";

    public static JigsawBlockInfo fromBlock(BlockVec position, Block block, CompoundBinaryTag nbt) {
        var orientation = block.getProperty("orientation");
        var front = Direction.SOUTH;
        var top = Direction.UP;

        if (orientation != null) {
            var parsed = parseOrientation(orientation);
            front = parsed[0];
            top = parsed[1];
        }

        var poolString = getStringTag(nbt, "pool");
        var pool = poolString != null ? Key.key(poolString) : EMPTY_KEY;

        var name = getStringTag(nbt, "name");
        if (name == null) {
            name = EMPTY_NAME;
        }

        var target = getStringTag(nbt, "target");
        if (target == null) {
            target = EMPTY_NAME;
        }

        var jointString = getStringTag(nbt, "joint");
        var jointType = JointType.fromString(jointString);
        if (jointType == null) {
            jointType = JointType.getDefault(front);
        }

        var placementPriority = getIntTag(nbt, "placement_priority", 0);

        var finalState = getStringTag(nbt, "final_state");
        if (finalState == null) {
            finalState = "minecraft:air";
        }

        return new JigsawBlockInfo(position, pool, name, target, jointType, front, top, placementPriority, finalState);
    }

    public JigsawBlockInfo withRotation(Rotation rotation, BlockVec newPosition) {
        return new JigsawBlockInfo(
                newPosition,
                this.pool,
                this.name,
                this.target,
                this.jointType,
                rotation.rotate(this.front),
                rotation.rotate(this.top),
                this.placementPriority,
                this.finalState
        );
    }

    public boolean canAttach(JigsawBlockInfo other) {
        if (!this.front.equals(other.front.opposite())) {
            return false;
        }

        if (!this.target.equals(other.name)) {
            return false;
        }

        if (this.jointType == JointType.ALIGNED && !this.top.equals(other.top)) {
            return false;
        }

        return true;
    }

    private static Direction[] parseOrientation(String orientation) {
        var parts = orientation.toLowerCase().split("_");
        if (parts.length != 2) {
            return new Direction[]{Direction.SOUTH, Direction.UP};
        }

        var front = parseDirection(parts[0]);
        var top = parseDirection(parts[1]);
        return new Direction[]{front, top};
    }

    private static Direction parseDirection(String name) {
        return switch (name) {
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> Direction.SOUTH;
        };
    }

    private static String getStringTag(CompoundBinaryTag nbt, String key) {
        if (nbt == null) {
            return null;
        }
        var tag = nbt.get(key);
        if (tag instanceof StringBinaryTag stringTag) {
            return stringTag.value();
        }
        return null;
    }

    private static int getIntTag(CompoundBinaryTag nbt, String key, int defaultValue) {
        if (nbt == null) {
            return defaultValue;
        }
        return nbt.getInt(key, defaultValue);
    }
}
