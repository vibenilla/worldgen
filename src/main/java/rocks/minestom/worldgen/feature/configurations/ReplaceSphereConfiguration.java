package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

/** Port of vanilla {@code ReplaceSphereConfiguration} (netherrack replace blobs). */
public record ReplaceSphereConfiguration(
        Block targetState,
        Block replaceState,
        IntProvider radius
) implements FeatureConfiguration {

    public static ReplaceSphereConfiguration fromJson(JsonElement json) {
        var obj = json.getAsJsonObject();
        return new ReplaceSphereConfiguration(
                BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("target")).orElseThrow(),
                BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("state")).orElseThrow(),
                IntProvider.fromJson(obj.get("radius")));
    }
}
