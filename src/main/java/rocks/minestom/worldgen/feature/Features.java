package rocks.minestom.worldgen.feature;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.configurations.BlockPileConfiguration;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.feature.configurations.SpikeConfiguration;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;

import java.util.List;

public final class Features {
    private Features() {
    }

    public static final TreeFeature TREE = new TreeFeature();
    public static final BlockPileFeature BLOCK_PILE = new BlockPileFeature();
    public static final ChorusPlantFeature CHORUS_PLANT = new ChorusPlantFeature();
    public static final EndPlatformFeature END_PLATFORM = new EndPlatformFeature();
    public static final EndSpikeFeature END_SPIKE = new EndSpikeFeature();
    public static final FlowerFeature FLOWER = new FlowerFeature();

    private static final Codec<TreeConfiguredFeature> TREE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", TreeConfiguration.CODEC, TreeConfiguredFeature::config,
            TreeConfiguredFeature::new);

    private static final Codec<NoneConfiguredFeature> NONE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", NoneFeatureConfiguration.CODEC, NoneConfiguredFeature::config,
            NoneConfiguredFeature::new);

    private static final Codec<BlockPileConfiguredFeature> BLOCK_PILE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", BlockPileConfiguration.CODEC, BlockPileConfiguredFeature::config,
            BlockPileConfiguredFeature::new);

    private static final Codec<SpikeConfiguredFeature> SPIKE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", SpikeConfiguration.CODEC, SpikeConfiguredFeature::config,
            SpikeConfiguredFeature::new);

    private static final Codec<RandomSelectorConfiguredFeature> RANDOM_SELECTOR_CONFIGURED_FEATURE_CODEC = StructCodec
            .struct(
                    "config", RandomSelectorConfiguredFeatureConfig.CODEC, RandomSelectorConfiguredFeature::config,
                    RandomSelectorConfiguredFeature::new);

    public static ConfiguredFeature<?> parseConfiguredFeature(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("ConfiguredFeature must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var typeStr = obj.get("type").getAsString();

        return switch (typeStr) {
            case "minecraft:tree" -> {
                var config = TREE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(TREE, config);
            }
            case "minecraft:random_selector" -> {
                var decoded = RANDOM_SELECTOR_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow()
                        .config();
                yield new ConfiguredFeature<>(new RandomSelectorFeature(decoded.defaultFeature(), decoded.features()),
                        null);
            }
            case "minecraft:block_pile" -> {
                var config = BLOCK_PILE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(BLOCK_PILE, config);
            }
            case "minecraft:chorus_plant" -> {
                var config = NONE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(CHORUS_PLANT, config);
            }
            case "minecraft:end_platform" -> {
                var config = NONE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(END_PLATFORM, config);
            }
            case "minecraft:end_spike" -> {
                var config = SPIKE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(END_SPIKE, config);
            }
            case "minecraft:flower" -> {
                // Flower feature config is complex (uses nested placed_feature with noise
                // providers)
                // For now, we use a NoneFeatureConfiguration placeholder
                yield new ConfiguredFeature<>(FLOWER, null);
            }
            default -> null;
        };
    }

    private record TreeConfiguredFeature(TreeConfiguration config) {
    }

    private record NoneConfiguredFeature(NoneFeatureConfiguration config) {
    }

    private record BlockPileConfiguredFeature(BlockPileConfiguration config) {
    }

    private record SpikeConfiguredFeature(SpikeConfiguration config) {
    }

    private record RandomSelectorConfiguredFeature(RandomSelectorConfiguredFeatureConfig config) {
    }

    private record RandomSelectorConfiguredFeatureConfig(Key defaultFeature,
            List<RandomSelectorFeature.WeightedFeature> features) {
        private static final Codec<RandomSelectorFeature.WeightedFeature> WEIGHTED_FEATURE_CODEC = StructCodec.struct(
                "chance", Codec.FLOAT, RandomSelectorFeature.WeightedFeature::chance,
                "feature", Codec.KEY, RandomSelectorFeature.WeightedFeature::featureId,
                RandomSelectorFeature.WeightedFeature::new);

        private static final Codec<RandomSelectorConfiguredFeatureConfig> CODEC = StructCodec.struct(
                "default", Codec.KEY, RandomSelectorConfiguredFeatureConfig::defaultFeature,
                "features", WEIGHTED_FEATURE_CODEC.list(), RandomSelectorConfiguredFeatureConfig::features,
                RandomSelectorConfiguredFeatureConfig::new);
    }
}
