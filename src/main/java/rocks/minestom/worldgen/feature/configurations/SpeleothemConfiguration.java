package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.Set;

public record SpeleothemConfiguration(
        Block baseBlock,
        Block pointedBlock,
        Set<Key> replaceableBlocks,
        float chanceOfTallerGeneration,
        float chanceOfDirectionalSpread,
        float chanceOfSpreadRadius2,
        float chanceOfSpreadRadius3
) implements FeatureConfiguration {

    public static SpeleothemConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("SpeleothemConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var baseBlock = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("base_block")).orElseThrow();
        var pointedBlock = BlockCodec.CODEC.decode(Transcoder.JSON, obj.get("pointed_block")).orElseThrow();
        var replaceableBlocks = BlockSets.parse(obj.get("replaceable_blocks"), blockTags);
        var chanceOfTallerGeneration = obj.has("chance_of_taller_generation")
                ? obj.get("chance_of_taller_generation").getAsFloat()
                : 0.2F;
        var chanceOfDirectionalSpread = obj.has("chance_of_directional_spread")
                ? obj.get("chance_of_directional_spread").getAsFloat()
                : 0.7F;
        var chanceOfSpreadRadius2 = obj.has("chance_of_spread_radius2")
                ? obj.get("chance_of_spread_radius2").getAsFloat()
                : 0.5F;
        var chanceOfSpreadRadius3 = obj.has("chance_of_spread_radius3")
                ? obj.get("chance_of_spread_radius3").getAsFloat()
                : 0.5F;

        return new SpeleothemConfiguration(baseBlock, pointedBlock, replaceableBlocks,
                chanceOfTallerGeneration, chanceOfDirectionalSpread, chanceOfSpreadRadius2, chanceOfSpreadRadius3);
    }
}
