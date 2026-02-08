package rocks.minestom.worldgen.structure.template;

import net.minestom.server.utils.Direction;

public enum JointType {
    ROLLABLE,
    ALIGNED;

    public static JointType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return switch (value.toLowerCase()) {
            case "rollable" -> ROLLABLE;
            case "aligned" -> ALIGNED;
            default -> null;
        };
    }

    public static JointType getDefault(Direction front) {
        return front.normalY() == 0 ? ALIGNED : ROLLABLE;
    }
}
