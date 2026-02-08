package rocks.minestom.worldgen;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.biome.ClimateSampler;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.surface.SurfaceRules;
import rocks.minestom.worldgen.surface.SurfaceSystem;

public final class NoiseGeneratorSettingsLoader {
    private final DataPack dataPack;

    public NoiseGeneratorSettingsLoader(DataPack dataPack) {
        this.dataPack = dataPack;
    }

    public NoiseGeneratorSettingsRuntime load(Key id, long seed) {
        var json = this.dataPack.readNoiseSettings(id);
        var settings = NoiseGeneratorSettings.CODEC.decode(Transcoder.JSON, json).orElseThrow();

        var randomState = new RandomState(this.dataPack, seed, settings.legacyRandomSource());
        var densityResolver = new DensityFunctionResolver(this.dataPack, randomState);
        var finalDensityJson = settings.noiseRouter().finalDensity().convertTo(Transcoder.JSON).orElseThrow();
        var finalDensity = densityResolver.codec().decode(Transcoder.JSON, finalDensityJson).orElseThrow();

        var climateSampler = new ClimateSampler(
                decodeDensity(densityResolver, settings.noiseRouter().temperature()),
                decodeDensity(densityResolver, settings.noiseRouter().vegetation()),
                decodeDensity(densityResolver, settings.noiseRouter().continents()),
                decodeDensity(densityResolver, settings.noiseRouter().erosion()),
                decodeDensity(densityResolver, settings.noiseRouter().depth()),
                decodeDensity(densityResolver, settings.noiseRouter().ridges())
        );

        var surfaceRuleJson = settings.surfaceRule().convertTo(Transcoder.JSON).orElseThrow();
        var surfaceRule = SurfaceRules.CODEC.decode(Transcoder.JSON, surfaceRuleJson).orElseThrow();

        var minY = settings.noise().minY();
        var height = settings.noise().height();
        var cellWidth = settings.noise().sizeHorizontal() * 4;
        var cellHeight = settings.noise().sizeVertical() * 4;
        var seaLevel = settings.seaLevel();
        var defaultBlock = settings.defaultBlock();
        var defaultFluid = settings.defaultFluid();
        var surfaceSystem = new SurfaceSystem(randomState, defaultBlock, seaLevel, randomState.getOrCreateRandomFactory(Key.key("minecraft:surface")));

        return new NoiseGeneratorSettingsRuntime(
                minY,
                height,
                cellWidth,
                cellHeight,
                seaLevel,
                defaultBlock,
                defaultFluid,
                finalDensity,
                climateSampler,
                randomState,
                surfaceSystem,
                surfaceRule
        );
    }

    private static DensityFunction decodeDensity(DensityFunctionResolver resolver, Codec.RawValue value) {
        var json = value.convertTo(Transcoder.JSON).orElseThrow();
        return resolver.codec().decode(Transcoder.JSON, json).orElseThrow();
    }
}
