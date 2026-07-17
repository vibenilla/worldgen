package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public record LakeConfiguration(
        BlockStateProvider fluid,
        BlockStateProvider barrier,
        PlacementModifiers.BlockPredicate canPlaceFeature,
        PlacementModifiers.BlockPredicate canReplaceWithAirOrFluid,
        PlacementModifiers.BlockPredicate canReplaceWithBarrier
) implements FeatureConfiguration {

    public static LakeConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("LakeConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        return new LakeConfiguration(
                BlockStateProviders.fromJson(obj.get("fluid")),
                BlockStateProviders.fromJson(obj.get("barrier")),
                PlacementModifiers.parseBlockPredicate(obj.getAsJsonObject("can_place_feature"), blockTags),
                PlacementModifiers.parseBlockPredicate(obj.getAsJsonObject("can_replace_with_air_or_fluid"), blockTags),
                PlacementModifiers.parseBlockPredicate(obj.getAsJsonObject("can_replace_with_barrier"), blockTags));
    }
}
