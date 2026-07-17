package rocks.minestom.worldgen.structure.scattered;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The desert pyramid, jungle temple and swamp hut structures
 * ({@code minecraft:desert_pyramid}, {@code minecraft:jungle_temple},
 * {@code minecraft:swamp_hut}). Like the mineshaft and fortress, these are
 * generated piece by piece rather than from templates; placement is handled
 * by {@link ScatteredFeaturePlacer} instead of the generic template path.
 */
public record ScatteredFeatureStructure(ScatteredFeatureKind kind, StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by ScatteredFeaturePlacer; nothing to do through the template path.
    }
}
