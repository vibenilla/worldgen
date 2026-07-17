package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

/** Port of vanilla {@code ColumnFeatureConfiguration} (basalt columns). */
public record ColumnFeatureConfiguration(
        IntProvider reach,
        IntProvider height
) implements FeatureConfiguration {

    public static ColumnFeatureConfiguration fromJson(JsonElement json) {
        var obj = json.getAsJsonObject();
        return new ColumnFeatureConfiguration(
                IntProvider.fromJson(obj.get("reach")),
                IntProvider.fromJson(obj.get("height")));
    }
}
