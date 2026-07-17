package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public record BlockBlobConfiguration(
        Block state,
        PlacementModifiers.BlockPredicate canPlaceOn
) implements FeatureConfiguration {

    public static BlockBlobConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("BlockBlobConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var state = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("state")).orElseThrow();
        var canPlaceOn = PlacementModifiers.parseBlockPredicate(obj.getAsJsonObject("can_place_on"), blockTags);
        return new BlockBlobConfiguration(state, canPlaceOn);
    }
}
