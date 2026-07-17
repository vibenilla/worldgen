package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/** Port of vanilla's {@code HugeFungusConfiguration}. */
public record HugeFungusConfiguration(
        Block validBaseState,
        Block stemState,
        Block hatState,
        Block decorState,
        PlacementModifiers.BlockPredicate replaceableBlocks,
        boolean planted
) implements FeatureConfiguration {

    public static HugeFungusConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();
        return new HugeFungusConfiguration(
                BlockCodec.CODEC.decode(Transcoder.JSON, object.get("valid_base_block")).orElseThrow(),
                BlockCodec.CODEC.decode(Transcoder.JSON, object.get("stem_state")).orElseThrow(),
                BlockCodec.CODEC.decode(Transcoder.JSON, object.get("hat_state")).orElseThrow(),
                BlockCodec.CODEC.decode(Transcoder.JSON, object.get("decor_state")).orElseThrow(),
                PlacementModifiers.parseBlockPredicate(object.getAsJsonObject("replaceable_blocks"), blockTags),
                object.has("planted") && object.get("planted").getAsBoolean());
    }
}
