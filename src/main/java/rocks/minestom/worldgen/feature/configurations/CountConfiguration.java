package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

public record CountConfiguration(IntProvider count) implements FeatureConfiguration {

    public static CountConfiguration fromJson(JsonElement json) {
        return new CountConfiguration(IntProvider.fromJson(json.getAsJsonObject().get("count")));
    }
}
