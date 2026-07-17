package rocks.minestom.worldgen.structure.mansion;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The woodland mansion structure ({@code minecraft:woodland_mansion}). Like
 * the nether fortress, it is generated from a grid-solved piece layout rather
 * than a single template; placement is handled by {@link MansionPlacer}
 * instead of the generic template path.
 */
public record WoodlandMansionStructure(StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by MansionPlacer; nothing to do through the template path.
    }
}
