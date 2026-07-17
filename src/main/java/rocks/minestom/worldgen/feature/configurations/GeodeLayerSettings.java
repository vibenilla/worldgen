package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;

/**
 * Port of vanilla {@code GeodeLayerSettings}: the inverse-square-root
 * thresholds separating the filling, inner, middle and outer geode layers.
 */
public record GeodeLayerSettings(double filling, double innerLayer, double middleLayer, double outerLayer) {
    public static final GeodeLayerSettings DEFAULT = new GeodeLayerSettings(1.7, 2.2, 3.2, 4.2);

    public static GeodeLayerSettings fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("GeodeLayerSettings must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        return new GeodeLayerSettings(
                obj.has("filling") ? obj.get("filling").getAsDouble() : DEFAULT.filling(),
                obj.has("inner_layer") ? obj.get("inner_layer").getAsDouble() : DEFAULT.innerLayer(),
                obj.has("middle_layer") ? obj.get("middle_layer").getAsDouble() : DEFAULT.middleLayer(),
                obj.has("outer_layer") ? obj.get("outer_layer").getAsDouble() : DEFAULT.outerLayer());
    }
}
