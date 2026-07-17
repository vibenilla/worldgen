package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.feature.valueproviders.UniformIntProvider;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Port of vanilla {@code GeodeConfiguration}: the full configuration for
 * {@code minecraft:geode}, combining the block, layer and crack settings with
 * the placement shape parameters.
 */
public record GeodeConfiguration(
        GeodeBlockSettings geodeBlockSettings,
        GeodeLayerSettings geodeLayerSettings,
        GeodeCrackSettings geodeCrackSettings,
        double usePotentialPlacementsChance,
        double useAlternateLayer0Chance,
        boolean placementsRequireLayer0Alternate,
        IntProvider outerWallDistance,
        IntProvider distributionPoints,
        IntProvider pointOffset,
        int minGenOffset,
        int maxGenOffset,
        double noiseMultiplier,
        int invalidBlocksThreshold
) implements FeatureConfiguration {

    public static GeodeConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("GeodeConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var blockSettings = GeodeBlockSettings.fromJson(obj.get("blocks"), blockTags);
        var layerSettings = obj.has("layers") ? GeodeLayerSettings.fromJson(obj.get("layers")) : GeodeLayerSettings.DEFAULT;
        var crackSettings = obj.has("crack") ? GeodeCrackSettings.fromJson(obj.get("crack")) : GeodeCrackSettings.DEFAULT;
        return new GeodeConfiguration(
                blockSettings,
                layerSettings,
                crackSettings,
                obj.has("use_potential_placements_chance") ? obj.get("use_potential_placements_chance").getAsDouble() : 0.35,
                obj.has("use_alternate_layer0_chance") ? obj.get("use_alternate_layer0_chance").getAsDouble() : 0.0,
                !obj.has("placements_require_layer0_alternate") || obj.get("placements_require_layer0_alternate").getAsBoolean(),
                obj.has("outer_wall_distance") ? IntProvider.fromJson(obj.get("outer_wall_distance")) : new UniformIntProvider(4, 5),
                obj.has("distribution_points") ? IntProvider.fromJson(obj.get("distribution_points")) : new UniformIntProvider(3, 4),
                obj.has("point_offset") ? IntProvider.fromJson(obj.get("point_offset")) : new UniformIntProvider(1, 2),
                obj.has("min_gen_offset") ? obj.get("min_gen_offset").getAsInt() : -16,
                obj.has("max_gen_offset") ? obj.get("max_gen_offset").getAsInt() : 16,
                obj.has("noise_multiplier") ? obj.get("noise_multiplier").getAsDouble() : 0.05,
                obj.get("invalid_blocks_threshold").getAsInt());
    }
}
