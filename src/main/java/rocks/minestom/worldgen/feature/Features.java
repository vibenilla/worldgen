package rocks.minestom.worldgen.feature;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.configurations.*;

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
    public static final SimpleBlockFeature SIMPLE_BLOCK = new SimpleBlockFeature();
    public static final RandomPatchFeature RANDOM_PATCH = new RandomPatchFeature();
    public static final NoOpFeature NO_OP = new NoOpFeature();

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

    private static final Codec<SimpleBlockConfiguredFeature> SIMPLE_BLOCK_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", SimpleBlockConfiguration.CODEC, SimpleBlockConfiguredFeature::config,
            SimpleBlockConfiguredFeature::new);

    private static final Codec<RandomPatchConfiguredFeature> RANDOM_PATCH_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", RandomPatchConfiguration.CODEC, RandomPatchConfiguredFeature::config,
            RandomPatchConfiguredFeature::new);

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
                var config = RANDOM_PATCH_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(FLOWER, config);
            }
            case "minecraft:simple_block" -> {
                var config = SIMPLE_BLOCK_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(SIMPLE_BLOCK, config);
            }
            case "minecraft:random_patch", "minecraft:no_bonemeal_flower" -> {
                var config = RANDOM_PATCH_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(RANDOM_PATCH, config);
            }
            case "minecraft:bamboo", "minecraft:basalt_pillar", "minecraft:block_column", "minecraft:blue_ice",
                    "minecraft:bonus_chest", "minecraft:coral_claw", "minecraft:coral_mushroom",
                    "minecraft:coral_tree", "minecraft:delta_feature", "minecraft:desert_well",
                    "minecraft:disk", "minecraft:end_gateway", "minecraft:end_island", "minecraft:fallen_tree",
                    "minecraft:fill_layer", "minecraft:forest_rock", "minecraft:fossil", "minecraft:geode",
                    "minecraft:glowstone_blob", "minecraft:huge_fungus", "minecraft:ice_spike",
                    "minecraft:iceberg", "minecraft:kelp", "minecraft:lake", "minecraft:monster_room",
                    "minecraft:no_op", "minecraft:ore", "minecraft:root_system", "minecraft:scattered_ore",
                    "minecraft:sculk_patch", "minecraft:sea_pickle", "minecraft:seagrass",
                    "minecraft:spring_feature", "minecraft:twisting_vines", "minecraft:vines",
                    "minecraft:weeping_vines" -> {
                yield new ConfiguredFeature<>(NO_OP, new NoneFeatureConfiguration());
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

    private record SimpleBlockConfiguredFeature(SimpleBlockConfiguration config) {
    }

    private record RandomPatchConfiguredFeature(RandomPatchConfiguration config) {
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
