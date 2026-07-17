package rocks.minestom.worldgen.structure.stronghold;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.TerrainAdjustment;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The procedural stronghold structure ({@code minecraft:stronghold}). Like
 * the mineshaft and fortress, it is generated piece by piece rather than
 * from templates; placement is handled by {@link StrongholdPlacer} instead
 * of the generic template path.
 */
public record StrongholdStructure(StructureBiomes biomes) implements Structure {
    @Override
    public TerrainAdjustment terrainAdaptation() {
        return TerrainAdjustment.BURY;
    }

    @Override
    public void place(StructurePlaceContext context) {
        // Handled by StrongholdPlacer; nothing to do through the template path.
    }
}
