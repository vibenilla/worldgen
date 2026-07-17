package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

public record DiskConfiguration(
        BlockStateProvider stateProvider,
        PlacementModifiers.BlockPredicate target,
        IntProvider radius,
        int halfHeight
) implements FeatureConfiguration {

    public static DiskConfiguration fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        return new DiskConfiguration(
                BlockStateProviders.fromJson(object.get("state_provider")),
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("target")),
                IntProvider.fromJson(object.get("radius")),
                object.get("half_height").getAsInt());
    }
}
