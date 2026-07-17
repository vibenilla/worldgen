package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

/**
 * Configuration for {@code minecraft:iceberg}, mirroring vanilla's
 * {@code BlockStateConfiguration}: a single block state used for the bulk of
 * the iceberg.
 */
public record IcebergConfiguration(Block state) implements FeatureConfiguration {

    public static IcebergConfiguration fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("IcebergConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var state = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("state")).orElseThrow();
        return new IcebergConfiguration(state);
    }
}
