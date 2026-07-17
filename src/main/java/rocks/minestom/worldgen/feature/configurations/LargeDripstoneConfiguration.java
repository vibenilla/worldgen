package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.Set;

public record LargeDripstoneConfiguration(
        Set<Key> replaceableBlocks,
        int floorToCeilingSearchRange,
        IntProvider columnRadius,
        FloatProvider heightScale,
        float maxColumnRadiusToCaveHeightRatio,
        FloatProvider stalactiteBluntness,
        FloatProvider stalagmiteBluntness,
        FloatProvider windSpeed,
        int minRadiusForWind,
        float minBluntnessForWind
) implements FeatureConfiguration {

    public static LargeDripstoneConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("LargeDripstoneConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        return new LargeDripstoneConfiguration(
                BlockSets.parse(obj.get("replaceable_blocks"), blockTags),
                obj.has("floor_to_ceiling_search_range") ? obj.get("floor_to_ceiling_search_range").getAsInt() : 30,
                IntProvider.fromJson(obj.get("column_radius")),
                FloatProvider.fromJson(obj.get("height_scale")),
                obj.get("max_column_radius_to_cave_height_ratio").getAsFloat(),
                FloatProvider.fromJson(obj.get("stalactite_bluntness")),
                FloatProvider.fromJson(obj.get("stalagmite_bluntness")),
                FloatProvider.fromJson(obj.get("wind_speed")),
                obj.get("min_radius_for_wind").getAsInt(),
                obj.get("min_bluntness_for_wind").getAsFloat());
    }
}
