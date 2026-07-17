package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Port of vanilla {@code GeodeBlockSettings}: the block state providers and
 * block sets used to paint a geode's layers and inner crystal placements.
 */
public record GeodeBlockSettings(
        BlockStateProvider fillingProvider,
        BlockStateProvider innerLayerProvider,
        BlockStateProvider alternateInnerLayerProvider,
        BlockStateProvider middleLayerProvider,
        BlockStateProvider outerLayerProvider,
        List<Block> innerPlacements,
        Set<Key> cannotReplace,
        Set<Key> invalidBlocks
) {
    /** Vanilla's {@code #minecraft:features_cannot_replace} block tag (26.2 contents). */
    public static final Set<String> FEATURES_CANNOT_REPLACE = Set.of(
            "minecraft:bedrock", "minecraft:spawner", "minecraft:chest", "minecraft:end_portal_frame",
            "minecraft:reinforced_deepslate", "minecraft:trial_spawner", "minecraft:vault");

    /** Vanilla's {@code #minecraft:geode_invalid_blocks} block tag (26.2 contents). */
    public static final Set<String> GEODE_INVALID_BLOCKS = Set.of(
            "minecraft:bedrock", "minecraft:water", "minecraft:lava", "minecraft:ice",
            "minecraft:packed_ice", "minecraft:blue_ice");

    public static GeodeBlockSettings fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("GeodeBlockSettings must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var innerPlacements = new java.util.ArrayList<Block>();
        for (var element : obj.getAsJsonArray("inner_placements")) {
            innerPlacements.add(BlockCodec.CODEC.decode(Transcoder.JSON, element).orElseThrow());
        }

        return new GeodeBlockSettings(
                BlockStateProviders.fromJson(obj.get("filling_provider")),
                BlockStateProviders.fromJson(obj.get("inner_layer_provider")),
                BlockStateProviders.fromJson(obj.get("alternate_inner_layer_provider")),
                BlockStateProviders.fromJson(obj.get("middle_layer_provider")),
                BlockStateProviders.fromJson(obj.get("outer_layer_provider")),
                List.copyOf(innerPlacements),
                parseBlockSet(obj.get("cannot_replace"), blockTags, FEATURES_CANNOT_REPLACE),
                parseBlockSet(obj.get("invalid_blocks"), blockTags, GEODE_INVALID_BLOCKS));
    }

    private static Set<Key> parseBlockSet(JsonElement json, BlockTagManager blockTags, Set<String> hardcodedFallback) {
        if (blockTags == null && json.isJsonPrimitive() && json.getAsString().startsWith("#")) {
            return hardcodedFallback.stream().map(Key::key).collect(Collectors.toUnmodifiableSet());
        }

        return BlockSets.parse(json, blockTags);
    }
}
