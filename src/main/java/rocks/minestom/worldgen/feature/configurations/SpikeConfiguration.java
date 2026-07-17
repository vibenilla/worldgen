package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Configuration for {@code minecraft:spike}, the generic version of the old
 * ice spike feature.
 */
public record SpikeConfiguration(
        Block state,
        PlacementModifiers.BlockPredicate canPlaceOn,
        PlacementModifiers.BlockPredicate canReplace
) implements FeatureConfiguration {

    public static SpikeConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("SpikeConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var state = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("state")).orElseThrow();
        var canPlaceOn = PlacementModifiers.parseBlockPredicate(obj.getAsJsonObject("can_place_on"), blockTags);
        var canReplace = PlacementModifiers.parseBlockPredicate(obj.getAsJsonObject("can_replace"), blockTags);
        return new SpikeConfiguration(state, canPlaceOn, canReplace);
    }
}
