package rocks.minestom.worldgen.feature;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.feature.placement.PlacementModifier;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;

public record PlacedFeature(Key feature, ConfiguredFeature<?> inlineFeature, List<PlacementModifier> placement) {

    public static PlacedFeature fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("PlacedFeature must be a JSON object");
        }

        var object = json.getAsJsonObject();
        var featureElement = object.get("feature");
        Key featureKey = null;
        ConfiguredFeature<?> inlineConfiguredFeature = null;

        if (featureElement.isJsonPrimitive()) {
            featureKey = Key.key(featureElement.getAsString());
        } else if (featureElement.isJsonObject()) {
            inlineConfiguredFeature = Features.parseConfiguredFeature(featureElement.getAsJsonObject());
        }

        var placementModifiers = List.<PlacementModifier>of();
        if (object.has("placement") && object.get("placement").isJsonArray()) {
            placementModifiers = PlacementModifiers.parse(object.getAsJsonArray("placement"));
        }

        return new PlacedFeature(featureKey, inlineConfiguredFeature, placementModifiers);
    }

    public ConfiguredFeature<?> configuredFeature(FeatureLoader loader) {
        if (this.inlineFeature != null) {
            return this.inlineFeature;
        }

        if (this.feature == null) {
            return null;
        }

        if (loader == null) {
            return null;
        }

        return loader.getConfiguredFeature(this.feature);
    }

    public List<BlockVec> getPositions(PlacementContext context, RandomSource random, BlockVec origin) {
        if (this.placement.isEmpty()) {
            return List.of(origin);
        }

        return PlacementModifiers.apply(this.placement, context, random, origin);
    }
}
