package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class FeatureLoader {
    private static final Codec<BiomeFeatures> BIOME_FEATURES_CODEC = StructCodec.struct(
            "features", Codec.KEY.list().list().optional(List.of()), BiomeFeatures::features,
            BiomeFeatures::new
    );

    private final DataPack dataPack;
    private final BlockTagManager blockTags;
    private final Map<Key, ConfiguredFeature<?>> configuredFeatureCache;
    private final Map<Key, PlacedFeature> placedFeatureCache;
    private final Map<Key, List<List<Key>>> biomeFeatureCache;
    private final Map<Key, Set<Key>> biomeFeatureSetCache;
    private final Map<List<Key>, List<FeatureSorter.StepFeatureData>> featuresPerStepCache;

    public DataPack dataPack() {
        return this.dataPack;
    }

    public BlockTagManager blockTags() {
        return this.blockTags;
    }

    public FeatureLoader(DataPack dataPack) {
        this.dataPack = dataPack;
        this.blockTags = new BlockTagManager(dataPack.rootPath());
        this.configuredFeatureCache = new ConcurrentHashMap<>();
        this.placedFeatureCache = new ConcurrentHashMap<>();
        this.biomeFeatureCache = new ConcurrentHashMap<>();
        this.biomeFeatureSetCache = new ConcurrentHashMap<>();
        this.featuresPerStepCache = new ConcurrentHashMap<>();
    }

    /**
     * Deterministic global feature ordering per decoration step for the given
     * biome set, mirroring vanilla's FeatureSorter result. Cached per biome list.
     */
    public List<FeatureSorter.StepFeatureData> featuresPerStep(List<Key> possibleBiomes) {
        return this.featuresPerStepCache.computeIfAbsent(possibleBiomes,
                biomes -> FeatureSorter.buildFeaturesPerStep(biomes, this::getBiomeFeatures));
    }

    /**
     * Whether the biome's feature lists contain the placed feature, at any step.
     */
    public boolean biomeHasFeature(Key biomeId, Key placedFeatureId) {
        var featureSet = this.biomeFeatureSetCache.computeIfAbsent(biomeId, biome -> {
            var set = new HashSet<Key>();
            for (var step : this.getBiomeFeatures(biome)) {
                set.addAll(step);
            }
            return set;
        });
        return featureSet.contains(placedFeatureId);
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
            var previousLoader = Features.currentLoader();
            Features.currentLoader(this);
            try {
                return Features.parseConfiguredFeature(json, this.blockTags);
            } finally {
                Features.currentLoader(previousLoader);
            }
        } catch (Exception exception) {
            return null;
        }
    }

    private PlacedFeature loadPlacedFeature(Key id) {
        try {
            var json = this.dataPack.readPlacedFeature(id);
            // Expose the tag manager so placement block predicates
            // (matching_block_tag) can resolve their tags while parsing, and
            // the loader so nested feature references can be captured.
            var previousBlockTags = PlacementModifiers.currentBlockTags();
            var previousLoader = Features.currentLoader();
            PlacementModifiers.currentBlockTags(this.blockTags);
            Features.currentLoader(this);
            try {
                return PlacedFeature.fromJson(json);
            } finally {
                PlacementModifiers.currentBlockTags(previousBlockTags);
                Features.currentLoader(previousLoader);
            }
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
