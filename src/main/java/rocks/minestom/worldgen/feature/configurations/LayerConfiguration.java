package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

public record LayerConfiguration(int height, Block state) implements FeatureConfiguration {

    public static LayerConfiguration fromJson(JsonElement json) {
        var obj = json.getAsJsonObject();
        var height = obj.get("height").getAsInt();
        var state = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("state")).orElseThrow();
        return new LayerConfiguration(height, state);
    }
}
