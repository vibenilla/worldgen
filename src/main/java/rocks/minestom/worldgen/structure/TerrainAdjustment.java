package rocks.minestom.worldgen.structure;

/**
 * How the noise terrain molds itself around a structure's pieces, mirroring
 * vanilla's {@code TerrainAdjustment}. Structures declare this through the
 * {@code terrain_adaptation} datapack field; the beardifier turns it into a
 * density contribution added during the noise fill.
 */
public enum TerrainAdjustment {
    /** No terrain adaptation (the datapack default). */
    NONE,
    /** Pulls terrain up around the piece so it ends up buried (trail ruins). */
    BURY,
    /** Builds a support platform under the piece (villages, outposts). */
    BEARD_THIN,
    /** Platform under the piece and clears the piece's box (ancient cities). */
    BEARD_BOX,
    /** Wraps the piece's whole box in terrain (trial chambers). */
    ENCAPSULATE;

    public static TerrainAdjustment fromName(String name) {
        var stripped = name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
        return switch (stripped) {
            case "bury" -> BURY;
            case "beard_thin" -> BEARD_THIN;
            case "beard_box" -> BEARD_BOX;
            case "encapsulate" -> ENCAPSULATE;
            default -> NONE;
        };
    }
}
