package rocks.minestom.worldgen.structure.ruinedportal;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

import java.util.List;

/**
 * The ruined portal structure ({@code minecraft:ruined_portal} and its six
 * biome variants, all type {@code minecraft:ruined_portal}). Unlike the generic
 * {@link rocks.minestom.worldgen.structure.SimpleStructure} template path, it
 * picks a weighted {@link Setup} (vertical placement, air pocket, mossiness,
 * vines, overgrowth, blackstone), rolls a giant/normal template plus a mirror,
 * resolves a suitable buried/surface Y, applies weathering processors and then
 * spreads netherrack, so placement is handled by {@link RuinedPortalPlacer}.
 */
public record RuinedPortalStructure(StructureBiomes biomes, List<Setup> setups) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by RuinedPortalPlacer; nothing to do through the template path.
    }

    /**
     * Vanilla {@code RuinedPortalStructure.Setup}: one weighted placement
     * configuration read from the structure's {@code setups} JSON array.
     */
    public record Setup(
            VerticalPlacement placement,
            float airPocketProbability,
            float mossiness,
            boolean overgrown,
            boolean vines,
            boolean canBeCold,
            boolean replaceWithBlackstone,
            float weight
    ) {
    }

    /**
     * Vanilla {@code RuinedPortalPiece.Properties}: the resolved per-piece
     * decoration flags handed to the processors and post-processing.
     */
    public record Properties(
            boolean cold,
            float mossiness,
            boolean airPocket,
            boolean overgrown,
            boolean vines,
            boolean replaceWithBlackstone
    ) {
    }

    /** Vanilla {@code RuinedPortalPiece.VerticalPlacement}. */
    public enum VerticalPlacement {
        ON_LAND_SURFACE("on_land_surface"),
        PARTLY_BURIED("partly_buried"),
        ON_OCEAN_FLOOR("on_ocean_floor"),
        IN_MOUNTAIN("in_mountain"),
        UNDERGROUND("underground"),
        IN_NETHER("in_nether");

        private final String serializedName;

        VerticalPlacement(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return this.serializedName;
        }

        public static VerticalPlacement fromName(String name) {
            for (var placement : values()) {
                if (placement.serializedName.equals(name)) {
                    return placement;
                }
            }
            throw new IllegalArgumentException("Unknown ruined portal vertical placement: " + name);
        }
    }
}
