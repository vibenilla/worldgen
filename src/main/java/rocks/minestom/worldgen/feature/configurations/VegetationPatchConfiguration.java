package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.feature.PlacedFeature;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.Set;

public record VegetationPatchConfiguration(
        Set<Key> replaceable,
        BlockStateProvider groundState,
        PlacedFeature vegetationFeature,
        boolean ceiling,
        IntProvider depth,
        float extraBottomBlockChance,
        int verticalRange,
        float vegetationChance,
        IntProvider xzRadius,
        float extraEdgeColumnChance,
        FeatureLoader loader
) implements FeatureConfiguration {

    public static VegetationPatchConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();
        var replaceableValue = object.get("replaceable").getAsString();
        var replaceable = replaceableValue.startsWith("#") && blockTags != null
                ? blockTags.blocks(Key.key(replaceableValue.substring(1)))
                : Set.of(Key.key(replaceableValue));

        return new VegetationPatchConfiguration(
                replaceable,
                BlockStateProviders.fromJson(object.get("ground_state")),
                PlacedFeature.fromJson(object.get("vegetation_feature")),
                "ceiling".equals(object.get("surface").getAsString()),
                IntProvider.fromJson(object.get("depth")),
                object.get("extra_bottom_block_chance").getAsFloat(),
                object.get("vertical_range").getAsInt(),
                object.get("vegetation_chance").getAsFloat(),
                IntProvider.fromJson(object.get("xz_radius")),
                object.get("extra_edge_column_chance").getAsFloat(),
                // The vegetation feature is usually a registry reference; keep the
                // loader that owns this parse so it can be resolved at place time.
                Features.currentLoader());
    }
}
