package rocks.minestom.worldgen.surface;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.world.biome.Biome;
import rocks.minestom.worldgen.datapack.DataPack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DataPackBiomeResolver implements BiomeResolver {
    private final DataPack dataPack;
    private final Map<Key, Float> temperatureCache;

    public DataPackBiomeResolver(DataPack dataPack) {
        this.dataPack = dataPack;
        this.temperatureCache = new ConcurrentHashMap<>();
    }

    @Override
    public float temperature(Key biome) {
        return this.temperatureCache.computeIfAbsent(biome, this::readTemperature);
    }

    private float readTemperature(Key biome) {
        var json = this.dataPack.readBiome(biome);
        var decoded = Biome.REGISTRY_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        return decoded.temperature();
    }
}

