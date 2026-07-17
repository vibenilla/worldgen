package rocks.minestom.worldgen.structure.scattered;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The buried treasure structure ({@code minecraft:buried_treasure}): a
 * single fixed-position piece, generated procedurally rather than from a
 * template; placement is handled by {@link ScatteredFeaturePlacer} instead
 * of the generic template path.
 */
public record BuriedTreasureStructure(StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by ScatteredFeaturePlacer; nothing to do through the template path.
    }
}
