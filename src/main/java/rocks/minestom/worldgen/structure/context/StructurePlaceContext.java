package rocks.minestom.worldgen.structure.context;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.PositionalRandomFactory;
import rocks.minestom.worldgen.structure.loader.StructureLoader;

public record StructurePlaceContext(
        GenerationUnitAdapter level,
        NoiseGeneratorSettingsRuntime settings,
        StructureLoader structureLoader,
        FeatureLoader featureLoader,
        BlockVec start,
        PositionalRandomFactory randomFactory
) {
}
