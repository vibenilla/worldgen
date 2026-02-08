package rocks.minestom.worldgen;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.generator.Generator;
import rocks.minestom.worldgen.biome.*;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.preset.FixedBiomeSourceSettings;
import rocks.minestom.worldgen.preset.MultiNoiseBiomeSourceSettings;
import rocks.minestom.worldgen.preset.TheEndBiomeSourceSettings;
import rocks.minestom.worldgen.preset.WorldPreset;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.placement.StructurePlacer;
import rocks.minestom.worldgen.surface.DataPackBiomeResolver;

import java.nio.file.Path;
import java.util.List;

public final class WorldGenerators {
    private final DataPack dataPack;
    private final DataPackBiomeResolver biomeResolver;
    private final FeatureLoader featureLoader;
    private final StructureLoader structureLoader;
    private final StructurePlacer overworldStructures;
    private final StructurePlacer netherStructures;
    private final NoiseGeneratorSettingsRuntime overworldSettings;
    private final BiomeSource overworldBiomes;
    private final NoiseGeneratorSettingsRuntime netherSettings;
    private final BiomeSource netherBiomes;
    private final NoiseGeneratorSettingsRuntime endSettings;
    private final BiomeSource endBiomes;
    private final long biomeZoomSeed;

    public WorldGenerators(Path rootPath, long seed) {
        this.dataPack = new DataPack(rootPath);
        this.biomeResolver = new DataPackBiomeResolver(this.dataPack);
        this.featureLoader = new FeatureLoader(this.dataPack);
        this.structureLoader = new StructureLoader(this.dataPack);
        this.overworldStructures = new StructurePlacer(
                this.structureLoader,
                this.featureLoader,
                List.of(
                        Key.key("minecraft:villages"),
                        Key.key("minecraft:igloos"),
                        Key.key("minecraft:shipwrecks"),
                        Key.key("minecraft:ruined_portals"),
                        Key.key("minecraft:ocean_ruins"),
                        Key.key("minecraft:pillager_outposts"),
                        Key.key("minecraft:ancient_cities"),
                        Key.key("minecraft:trail_ruins"),
                        Key.key("minecraft:trial_chambers")
                )
        );
        this.netherStructures = new StructurePlacer(
                this.structureLoader,
                this.featureLoader,
                List.of(
                        Key.key("minecraft:nether_fossils"),
                        Key.key("minecraft:nether_complexes")
                )
        );
        this.biomeZoomSeed = BiomeZoomer.obfuscateSeed(seed);
        var worldPresetJson = this.dataPack.readWorldPreset(Key.key("minecraft:normal"));
        var worldPreset = WorldPreset.CODEC.decode(Transcoder.JSON, worldPresetJson).orElseThrow();
        var loader = new NoiseGeneratorSettingsLoader(this.dataPack);

        var overworld = getDimension(worldPreset, Key.key("minecraft:overworld"));
        this.overworldSettings = loader.load(overworld.generator().settings(), seed);
        this.overworldBiomes = createBiomeSource(overworld.generator(), this.overworldSettings);

        var nether = getDimension(worldPreset, Key.key("minecraft:the_nether"));
        this.netherSettings = loader.load(nether.generator().settings(), seed);
        this.netherBiomes = createBiomeSource(nether.generator(), this.netherSettings);

        var end = getDimension(worldPreset, Key.key("minecraft:the_end"));
        this.endSettings = loader.load(end.generator().settings(), seed);
        this.endBiomes = createBiomeSource(end.generator(), this.endSettings);
    }

    public Generator overworld() {
        return new WorldGenerator(this.overworldSettings, this.overworldBiomes, this.biomeZoomSeed, this.biomeResolver, this.featureLoader, this.overworldStructures, false);
    }

    public Generator nether() {
        return new WorldGenerator(this.netherSettings, this.netherBiomes, this.biomeZoomSeed, this.biomeResolver, this.featureLoader, this.netherStructures, false);
    }

    public Generator end() {
        return new WorldGenerator(this.endSettings, this.endBiomes, this.biomeZoomSeed, this.biomeResolver, this.featureLoader, null, true);
    }

    public Path dataPackRoot() {
        return this.dataPack.rootPath();
    }

    public StructureLoader structureLoader() {
        return this.structureLoader;
    }

    public NoiseGeneratorSettingsRuntime overworldSettings() {
        return this.overworldSettings;
    }

    public BiomeSource overworldBiomes() {
        return this.overworldBiomes;
    }

    public NoiseGeneratorSettingsRuntime netherSettings() {
        return this.netherSettings;
    }

    public BiomeSource netherBiomes() {
        return this.netherBiomes;
    }

    public NoiseGeneratorSettingsRuntime endSettings() {
        return this.endSettings;
    }

    public BiomeSource endBiomes() {
        return this.endBiomes;
    }

    public long biomeZoomSeed() {
        return this.biomeZoomSeed;
    }

    static WorldPreset.Dimension getDimension(WorldPreset preset, Key dimension) {
        var result = preset.dimensions().get(dimension);
        if (result == null) {
            throw new IllegalStateException("World preset is missing dimension: " + dimension.asString());
        }
        return result;
    }

    private static BiomeSource createBiomeSource(WorldPreset.Generator generator, NoiseGeneratorSettingsRuntime settings) {
        var biomeSource = generator.biomeSource();

        if (biomeSource instanceof FixedBiomeSourceSettings(var biome)) {
            return new FixedBiomeSource(biome);
        }

        if (biomeSource instanceof MultiNoiseBiomeSourceSettings(var preset)) {
            var parameters = MultiNoiseBiomeSourceParameterList.preset(preset);
            return new MultiNoiseBiomeSource(settings.climateSampler(), parameters);
        }

        if (biomeSource instanceof TheEndBiomeSourceSettings) {
            return new TheEndBiomeSource(settings.climateSampler());
        }

        throw new IllegalStateException("Unsupported biome source: " + biomeSource.getClass().getName());
    }
}
