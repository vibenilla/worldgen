package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/** Port of vanilla's {@code HugeMushroomFeatureConfiguration}. */
public record HugeMushroomFeatureConfiguration(
        BlockStateProvider capProvider,
        BlockStateProvider stemProvider,
        PlacementModifiers.BlockPredicate canPlaceOn,
        int foliageRadius
) implements FeatureConfiguration {

    public static HugeMushroomFeatureConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();
        return new HugeMushroomFeatureConfiguration(
                BlockStateProviders.fromJson(object.get("cap_provider")),
                BlockStateProviders.fromJson(object.get("stem_provider")),
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("can_place_on"), blockTags),
                object.has("foliage_radius") ? object.get("foliage_radius").getAsInt() : 2);
    }
}
