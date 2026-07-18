package rocks.minestom.worldgen.structure.oceanruin;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The ocean ruin structure ({@code minecraft:ocean_ruin_cold} and
 * {@code minecraft:ocean_ruin_warm}, type {@code minecraft:ocean_ruin}).
 * Unlike the generic {@link rocks.minestom.worldgen.structure.SimpleStructure}
 * template path, ocean ruins draw a large/cluster roll and compute each
 * piece's height individually from the surrounding floor, so placement is
 * handled by {@link OceanRuinPlacer} instead.
 */
public record OceanRuinStructure(
        StructureBiomes biomes,
        BiomeTemp biomeTemp,
        float largeProbability,
        float clusterProbability
) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by OceanRuinPlacer; nothing to do through the template path.
    }

    public enum BiomeTemp {
        WARM,
        COLD
    }
}
