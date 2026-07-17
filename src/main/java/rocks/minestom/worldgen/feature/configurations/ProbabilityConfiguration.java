package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

public record ProbabilityConfiguration(float probability) implements FeatureConfiguration {

    public static ProbabilityConfiguration fromJson(JsonElement json) {
        return new ProbabilityConfiguration(json.getAsJsonObject().get("probability").getAsFloat());
    }
}
