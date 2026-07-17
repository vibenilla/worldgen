package rocks.minestom.worldgen.surface;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.datapack.DataPack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DataPackBiomeResolver implements BiomeResolver {
    private final DataPack dataPack;
    private final Map<Key, Climate> climateCache;

    public DataPackBiomeResolver(DataPack dataPack) {
        this.dataPack = dataPack;
        this.climateCache = new ConcurrentHashMap<>();
    }

    @Override
    public float temperature(Key biome) {
        return this.climate(biome).temperature();
    }

    @Override
    public boolean hasPrecipitation(Key biome) {
        return this.climate(biome).hasPrecipitation();
    }

    @Override
    public boolean frozenTemperatureModifier(Key biome) {
        return this.climate(biome).frozenTemperatureModifier();
    }

    private Climate climate(Key biome) {
        return this.climateCache.computeIfAbsent(biome, this::readClimate);
    }

    private Climate readClimate(Key biome) {
        var json = this.dataPack.readBiome(biome).getAsJsonObject();
        var temperatureModifier = json.has("temperature_modifier") ? json.get("temperature_modifier").getAsString() : "none";
        return new Climate(
                json.get("temperature").getAsFloat(),
                json.get("has_precipitation").getAsBoolean(),
                temperatureModifier.equals("frozen"));
    }

    private record Climate(float temperature, boolean hasPrecipitation, boolean frozenTemperatureModifier) {
    }
}
