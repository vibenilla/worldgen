package rocks.minestom.worldgen.structure.endcity;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The procedural end city structure ({@code minecraft:end_city}).
 * Like the nether fortress, it is generated piece by piece from a recursive
 * template assembly rather than a single template; placement is handled by
 * {@link rocks.minestom.worldgen.structure.placement.StructurePlacer} through
 * {@link EndCityPieces} instead of the generic jigsaw/simple template path.
 */
public record EndCityStructure(StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled directly by StructurePlacer; nothing to do through the
        // generic template path.
    }
}
