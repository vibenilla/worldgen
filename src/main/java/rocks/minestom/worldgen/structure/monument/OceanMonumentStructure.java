package rocks.minestom.worldgen.structure.monument;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The procedural ocean monument structure ({@code minecraft:monument}, type
 * {@code minecraft:ocean_monument}). Like the nether fortress, it is
 * generated piece by piece rather than from templates; placement is handled
 * by {@link MonumentPlacer} instead of the generic template path.
 */
public record OceanMonumentStructure(StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by MonumentPlacer; nothing to do through the template path.
    }
}
