package rocks.minestom.worldgen.structure.mineshaft;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The procedural mineshaft structure ({@code minecraft:mineshaft}). Unlike the
 * template-based structures it is generated piece by piece; placement is
 * handled by {@link MineshaftPlacer} instead of the generic template path.
 */
public record MineshaftStructure(MineshaftType type, StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by MineshaftPlacer; nothing to do through the template path.
    }
}
