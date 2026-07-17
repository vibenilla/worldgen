package rocks.minestom.worldgen.structure.pool;

/**
 * Vanilla {@code StructureTemplatePool.Projection}.
 */
public enum Projection {
    RIGID,
    TERRAIN_MATCHING;

    public static Projection fromName(String name) {
        return "terrain_matching".equals(name) ? TERRAIN_MATCHING : RIGID;
    }
}
