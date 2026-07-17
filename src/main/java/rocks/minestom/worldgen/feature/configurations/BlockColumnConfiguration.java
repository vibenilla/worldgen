package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.ArrayList;
import java.util.List;

public record BlockColumnConfiguration(
        List<Layer> layers,
        int directionY,
        PlacementModifiers.BlockPredicate allowedPlacement,
        boolean prioritizeTip
) implements FeatureConfiguration {

    public record Layer(IntProvider height, BlockStateProvider state) {
    }

    public static BlockColumnConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();
        var layers = new ArrayList<Layer>();
        for (var layerElement : object.getAsJsonArray("layers")) {
            var layer = layerElement.getAsJsonObject();
            layers.add(new Layer(
                    IntProvider.fromJson(layer.get("height")),
                    BlockStateProviders.fromJson(layer.get("provider"))));
        }

        return new BlockColumnConfiguration(
                List.copyOf(layers),
                "down".equals(object.get("direction").getAsString()) ? -1 : 1,
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("allowed_placement"), blockTags),
                object.get("prioritize_tip").getAsBoolean());
    }
}
