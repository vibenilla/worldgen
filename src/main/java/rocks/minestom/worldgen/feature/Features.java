package rocks.minestom.worldgen.feature;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.configurations.*;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.ArrayList;
import java.util.List;

public final class Features {
    private Features() {
    }

    public static final TreeFeature TREE = new TreeFeature();
    public static final FallenTreeFeature FALLEN_TREE = new FallenTreeFeature();
    public static final MultifaceGrowthFeature MULTIFACE_GROWTH = new MultifaceGrowthFeature();
    public static final HugeBrownMushroomFeature HUGE_BROWN_MUSHROOM = new HugeBrownMushroomFeature();
    public static final HugeRedMushroomFeature HUGE_RED_MUSHROOM = new HugeRedMushroomFeature();
    public static final BlockPileFeature BLOCK_PILE = new BlockPileFeature();
    public static final ChorusPlantFeature CHORUS_PLANT = new ChorusPlantFeature();
    public static final EndPlatformFeature END_PLATFORM = new EndPlatformFeature();
    public static final EndSpikeFeature END_SPIKE = new EndSpikeFeature();
    public static final FlowerFeature FLOWER = new FlowerFeature();
    public static final SimpleBlockFeature SIMPLE_BLOCK = new SimpleBlockFeature();
    public static final RandomPatchFeature RANDOM_PATCH = new RandomPatchFeature();
    public static final SimpleRandomSelectorFeature SIMPLE_RANDOM_SELECTOR = new SimpleRandomSelectorFeature();
    public static final DiskFeature DISK = new DiskFeature();
    public static final BlockColumnFeature BLOCK_COLUMN = new BlockColumnFeature();
    public static final VegetationPatchFeature VEGETATION_PATCH = new VegetationPatchFeature();
    public static final WaterloggedVegetationPatchFeature WATERLOGGED_VEGETATION_PATCH = new WaterloggedVegetationPatchFeature();
    public static final KelpFeature KELP = new KelpFeature();
    public static final SeagrassFeature SEAGRASS = new SeagrassFeature();
    public static final OreFeature ORE = new OreFeature();
    public static final ScatteredOreFeature SCATTERED_ORE = new ScatteredOreFeature();
    public static final NoOpFeature NO_OP = new NoOpFeature();
    public static final BlockBlobFeature BLOCK_BLOB = new BlockBlobFeature();
    public static final SpikeFeature SPIKE = new SpikeFeature();
    public static final SpeleothemFeature SPELEOTHEM = new SpeleothemFeature();
    public static final SpeleothemClusterFeature SPELEOTHEM_CLUSTER = new SpeleothemClusterFeature();
    public static final LargeDripstoneFeature LARGE_DRIPSTONE = new LargeDripstoneFeature();
    public static final LakeFeature LAKE = new LakeFeature();
    public static final MonsterRoomFeature MONSTER_ROOM = new MonsterRoomFeature();

    private static final Codec<TreeConfiguredFeature> TREE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", TreeConfiguration.CODEC, TreeConfiguredFeature::config,
            TreeConfiguredFeature::new);

    private static final Codec<FallenTreeConfiguredFeature> FALLEN_TREE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", FallenTreeConfiguration.CODEC, FallenTreeConfiguredFeature::config,
            FallenTreeConfiguredFeature::new);

    private static final Codec<NoneConfiguredFeature> NONE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", NoneFeatureConfiguration.CODEC, NoneConfiguredFeature::config,
            NoneConfiguredFeature::new);

    private static final Codec<BlockPileConfiguredFeature> BLOCK_PILE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", BlockPileConfiguration.CODEC, BlockPileConfiguredFeature::config,
            BlockPileConfiguredFeature::new);

    private static final Codec<EndSpikeConfiguredFeature> END_SPIKE_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", EndSpikeConfiguration.CODEC, EndSpikeConfiguredFeature::config,
            EndSpikeConfiguredFeature::new);

    private static final Codec<SimpleBlockConfiguredFeature> SIMPLE_BLOCK_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", SimpleBlockConfiguration.CODEC, SimpleBlockConfiguredFeature::config,
            SimpleBlockConfiguredFeature::new);

    private static final Codec<RandomPatchConfiguredFeature> RANDOM_PATCH_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", RandomPatchConfiguration.CODEC, RandomPatchConfiguredFeature::config,
            RandomPatchConfiguredFeature::new);

    private static final Codec<SimpleRandomSelectorConfiguredFeature> SIMPLE_RANDOM_SELECTOR_CONFIGURED_FEATURE_CODEC = StructCodec.struct(
            "config", SimpleRandomSelectorConfiguration.CODEC, SimpleRandomSelectorConfiguredFeature::config,
            SimpleRandomSelectorConfiguredFeature::new);

    /**
     * Feature loader owning the parse currently in progress, so configurations
     * holding by-reference placed features (vegetation patches) can resolve
     * them at place time.
     */
    private static final ThreadLocal<FeatureLoader> CURRENT_LOADER = new ThreadLocal<>();

    public static FeatureLoader currentLoader() {
        return CURRENT_LOADER.get();
    }

    public static void currentLoader(FeatureLoader loader) {
        if (loader == null) {
            CURRENT_LOADER.remove();
        } else {
            CURRENT_LOADER.set(loader);
        }
    }

    public static ConfiguredFeature<?> parseConfiguredFeature(JsonElement json) {
        return parseConfiguredFeature(json, null);
    }

    public static ConfiguredFeature<?> parseConfiguredFeature(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("ConfiguredFeature must be a JSON object");
        }

        // Nested parses (inline placed features, state provider rules) resolve
        // block tags through this thread-local; inherit it when the caller did
        // not pass a manager explicitly.
        var previousBlockTags = PlacementModifiers.currentBlockTags();
        var effectiveBlockTags = blockTags != null ? blockTags : previousBlockTags;
        PlacementModifiers.currentBlockTags(effectiveBlockTags);
        try {
            return parseConfiguredFeature(json.getAsJsonObject(), effectiveBlockTags);
        } finally {
            PlacementModifiers.currentBlockTags(previousBlockTags);
        }
    }

    private static ConfiguredFeature<?> parseConfiguredFeature(com.google.gson.JsonObject obj, BlockTagManager blockTags) {
        var typeStr = obj.get("type").getAsString();

        return switch (typeStr) {
            case "minecraft:tree" -> {
                var config = TREE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(TREE, config);
            }
            case "minecraft:fallen_tree" -> {
                var config = FALLEN_TREE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(FALLEN_TREE, config);
            }
            case "minecraft:multiface_growth" -> {
                var config = MultifaceGrowthConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(MULTIFACE_GROWTH, config);
            }
            case "minecraft:huge_brown_mushroom" -> {
                var config = HugeMushroomFeatureConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(HUGE_BROWN_MUSHROOM, config);
            }
            case "minecraft:huge_red_mushroom" -> {
                var config = HugeMushroomFeatureConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(HUGE_RED_MUSHROOM, config);
            }
            case "minecraft:random_selector" -> {
                var configObj = obj.getAsJsonObject("config");
                var features = new ArrayList<RandomSelectorFeature.WeightedFeature>();
                for (var entryElement : configObj.getAsJsonArray("features")) {
                    var entry = entryElement.getAsJsonObject();
                    features.add(new RandomSelectorFeature.WeightedFeature(
                            entry.get("chance").getAsFloat(),
                            parsePlacedFeature(entry.get("feature"))));
                }
                yield new ConfiguredFeature<>(
                        new RandomSelectorFeature(parsePlacedFeature(configObj.get("default")), List.copyOf(features)),
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
                var config = END_SPIKE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
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
            case "minecraft:simple_random_selector" -> {
                var config = SIMPLE_RANDOM_SELECTOR_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(SIMPLE_RANDOM_SELECTOR, config);
            }
            case "minecraft:kelp" -> {
                var config = NONE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(KELP, config);
            }
            case "minecraft:seagrass" -> {
                var config = ProbabilityConfiguration.fromJson(obj.get("config"));
                yield new ConfiguredFeature<>(SEAGRASS, config);
            }
            case "minecraft:block_column" -> {
                var config = BlockColumnConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(BLOCK_COLUMN, config);
            }
            case "minecraft:disk" -> {
                var config = DiskConfiguration.fromJson(obj.get("config"));
                yield new ConfiguredFeature<>(DISK, config);
            }
            case "minecraft:ore" -> {
                var config = OreConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(ORE, config);
            }
            case "minecraft:scattered_ore" -> {
                var config = OreConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(SCATTERED_ORE, config);
            }
            case "minecraft:block_blob" -> {
                var config = BlockBlobConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(BLOCK_BLOB, config);
            }
            case "minecraft:spike" -> {
                var config = SpikeConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(SPIKE, config);
            }
            case "minecraft:speleothem" -> {
                var config = SpeleothemConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(SPELEOTHEM, config);
            }
            case "minecraft:large_dripstone" -> {
                var config = LargeDripstoneConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(LARGE_DRIPSTONE, config);
            }
            case "minecraft:speleothem_cluster" -> {
                var config = SpeleothemClusterConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(SPELEOTHEM_CLUSTER, config);
            }
            case "minecraft:lake" -> {
                var config = LakeConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(LAKE, config);
            }
            case "minecraft:vegetation_patch" -> {
                var config = VegetationPatchConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(VEGETATION_PATCH, config);
            }
            case "minecraft:waterlogged_vegetation_patch" -> {
                var config = VegetationPatchConfiguration.fromJson(obj.get("config"), blockTags);
                yield new ConfiguredFeature<>(WATERLOGGED_VEGETATION_PATCH, config);
            }
            case "minecraft:monster_room" -> {
                var config = NONE_CONFIGURED_FEATURE_CODEC.decode(Transcoder.JSON, obj).orElseThrow().config();
                yield new ConfiguredFeature<>(MONSTER_ROOM, config);
            }
            case "minecraft:weighted_random_selector" -> {
                var features = new ArrayList<WeightedRandomSelectorFeature.WeightedPlacedFeature>();
                for (var entryElement : obj.getAsJsonObject("config").getAsJsonArray("features")) {
                    var entry = entryElement.getAsJsonObject();
                    features.add(new WeightedRandomSelectorFeature.WeightedPlacedFeature(
                            parsePlacedFeature(entry.get("data")),
                            entry.get("weight").getAsInt()));
                }
                yield new ConfiguredFeature<>(new WeightedRandomSelectorFeature(List.copyOf(features)), null);
            }
            case "minecraft:random_boolean_selector" -> {
                var configObj = obj.getAsJsonObject("config");
                yield new ConfiguredFeature<>(new RandomBooleanSelectorFeature(
                        parsePlacedFeature(configObj.get("feature_true")),
                        parsePlacedFeature(configObj.get("feature_false"))), null);
            }
            case "minecraft:sequence" -> {
                var features = new ArrayList<PlacedFeature>();
                for (var featureElement : obj.getAsJsonObject("config").getAsJsonArray("features")) {
                    features.add(parsePlacedFeature(featureElement));
                }
                yield new ConfiguredFeature<>(new SequenceFeature(List.copyOf(features)), null);
            }
            case "minecraft:bamboo", "minecraft:basalt_pillar", "minecraft:blue_ice",
                    "minecraft:bonus_chest", "minecraft:coral_claw", "minecraft:coral_mushroom",
                    "minecraft:coral_tree", "minecraft:delta_feature", "minecraft:desert_well",
                    "minecraft:end_gateway", "minecraft:end_island",
                    "minecraft:fill_layer", "minecraft:forest_rock", "minecraft:fossil", "minecraft:geode",
                    "minecraft:glowstone_blob", "minecraft:huge_fungus", "minecraft:ice_spike",
                    "minecraft:iceberg",
                    "minecraft:no_op", "minecraft:root_system",
                    "minecraft:sculk_patch", "minecraft:sea_pickle",
                    "minecraft:spring_feature", "minecraft:twisting_vines", "minecraft:vines",
                    "minecraft:weeping_vines" -> {
                yield new ConfiguredFeature<>(NO_OP, new NoneFeatureConfiguration());
            }
            default -> null;
        };
    }

    /**
     * Parses a nested placed feature entry: either an inline placed feature
     * object or a registry reference string.
     */
    private static PlacedFeature parsePlacedFeature(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return new PlacedFeature(Key.key(json.getAsString()), null, List.of());
        }

        return PlacedFeature.fromJson(json);
    }

    private record TreeConfiguredFeature(TreeConfiguration config) {
    }

    private record FallenTreeConfiguredFeature(FallenTreeConfiguration config) {
    }

    private record NoneConfiguredFeature(NoneFeatureConfiguration config) {
    }

    private record BlockPileConfiguredFeature(BlockPileConfiguration config) {
    }

    private record EndSpikeConfiguredFeature(EndSpikeConfiguration config) {
    }

    private record SimpleBlockConfiguredFeature(SimpleBlockConfiguration config) {
    }

    private record RandomPatchConfiguredFeature(RandomPatchConfiguration config) {
    }

    private record SimpleRandomSelectorConfiguredFeature(SimpleRandomSelectorConfiguration config) {
    }
}
