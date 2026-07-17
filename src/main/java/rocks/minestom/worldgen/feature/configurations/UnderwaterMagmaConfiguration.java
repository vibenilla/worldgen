package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

/** Port of vanilla's {@code UnderwaterMagmaConfiguration}. */
public record UnderwaterMagmaConfiguration(
        int floorSearchRange,
        int placementRadiusAroundFloor,
        float placementProbabilityPerValidPosition
) implements FeatureConfiguration {

    public static UnderwaterMagmaConfiguration fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        return new UnderwaterMagmaConfiguration(
                object.get("floor_search_range").getAsInt(),
                object.get("placement_radius_around_floor").getAsInt(),
                object.get("placement_probability_per_valid_position").getAsFloat());
    }
}
