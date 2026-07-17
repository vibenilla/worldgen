package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.Set;

public record SpeleothemClusterConfiguration(
        Block baseBlock,
        Block pointedBlock,
        Set<Key> replaceableBlocks,
        int floorToCeilingSearchRange,
        IntProvider height,
        IntProvider radius,
        int maxStalagmiteStalactiteHeightDiff,
        int heightDeviation,
        IntProvider speleothemBlockLayerThickness,
        FloatProvider density,
        FloatProvider wetness,
        float chanceOfSpeleothemAtMaxDistanceFromCenter,
        int maxDistanceFromEdgeAffectingChanceOfSpeleothem,
        int maxDistanceFromCenterAffectingHeightBias,
        Set<Key> baseStoneBlocks
) implements FeatureConfiguration {

    public static SpeleothemClusterConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("SpeleothemClusterConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var baseBlock = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("base_block")).orElseThrow();
        var pointedBlock = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("pointed_block")).orElseThrow();
        var replaceableBlocks = BlockSets.parse(obj.get("replaceable_blocks"), blockTags);

        return new SpeleothemClusterConfiguration(
                baseBlock,
                pointedBlock,
                replaceableBlocks,
                obj.get("floor_to_ceiling_search_range").getAsInt(),
                IntProvider.fromJson(obj.get("height")),
                IntProvider.fromJson(obj.get("radius")),
                obj.get("max_stalagmite_stalactite_height_diff").getAsInt(),
                obj.get("height_deviation").getAsInt(),
                IntProvider.fromJson(obj.get("speleothem_block_layer_thickness")),
                FloatProvider.fromJson(obj.get("density")),
                FloatProvider.fromJson(obj.get("wetness")),
                obj.get("chance_of_speleothem_at_max_distance_from_center").getAsFloat(),
                obj.get("max_distance_from_edge_affecting_chance_of_speleothem").getAsInt(),
                obj.get("max_distance_from_center_affecting_height_bias").getAsInt(),
                BlockSets.resolveTag(Key.key("minecraft:base_stone_overworld"), blockTags));
    }
}
