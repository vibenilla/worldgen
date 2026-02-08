package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.datapack.DataPack;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FeatureLoader {
    private static final Codec<BiomeFeatures> BIOME_FEATURES_CODEC = StructCodec.struct(
            "features", Codec.KEY.list().list().optional(List.of()), BiomeFeatures::features,
            BiomeFeatures::new
    );

    private final DataPack dataPack;
    private final Map<Key, ConfiguredFeature<?>> configuredFeatureCache;
    private final Map<Key, PlacedFeature> placedFeatureCache;
    private final Map<Key, List<List<Key>>> biomeFeatureCache;

    public FeatureLoader(DataPack dataPack) {
        this.dataPack = dataPack;
        this.configuredFeatureCache = new ConcurrentHashMap<>();
        this.placedFeatureCache = new ConcurrentHashMap<>();
        this.biomeFeatureCache = new ConcurrentHashMap<>();
    }

    public ConfiguredFeature<?> getConfiguredFeature(Key id) {
        return this.configuredFeatureCache.computeIfAbsent(id, this::loadConfiguredFeature);
    }

    public PlacedFeature getPlacedFeature(Key id) {
        return this.placedFeatureCache.computeIfAbsent(id, this::loadPlacedFeature);
    }

    public List<List<Key>> getBiomeFeatures(Key biomeId) {
        return this.biomeFeatureCache.computeIfAbsent(biomeId, this::loadBiomeFeatures);
    }

    private ConfiguredFeature<?> loadConfiguredFeature(Key id) {
        try {
            var json = this.dataPack.readConfiguredFeature(id);
            return Features.parseConfiguredFeature(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private PlacedFeature loadPlacedFeature(Key id) {
        try {
            var json = this.dataPack.readPlacedFeature(id);
            return PlacedFeature.CODEC.decode(Transcoder.JSON, json).orElseThrow();
        } catch (Exception exception) {
            return null;
        }
    }

    private List<List<Key>> loadBiomeFeatures(Key biomeId) {
        try {
            var json = this.dataPack.readBiome(biomeId);
            return BIOME_FEATURES_CODEC.decode(Transcoder.JSON, json).orElseThrow().features();
        } catch (Exception exception) {
            return List.of();
        }
    }

    private record BiomeFeatures(List<List<Key>> features) {
    }
}
