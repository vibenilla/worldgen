package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

/** Port of vanilla {@code DeltaFeatureConfiguration}. */
public record DeltaFeatureConfiguration(
        Block contents,
        Block rim,
        IntProvider size,
        IntProvider rimSize
) implements FeatureConfiguration {

    public static DeltaFeatureConfiguration fromJson(JsonElement json) {
        var obj = json.getAsJsonObject();
        return new DeltaFeatureConfiguration(
                BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("contents")).orElseThrow(),
                BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("rim")).orElseThrow(),
                IntProvider.fromJson(obj.get("size")),
                IntProvider.fromJson(obj.get("rim_size")));
    }
}
