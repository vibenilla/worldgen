package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.ruletest.RuleTest;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.ArrayList;
import java.util.List;

public record OreConfiguration(
        List<TargetBlockState> targetStates,
        int size,
        float discardChanceOnAirExposure
) implements FeatureConfiguration {

    public static OreConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("OreConfiguration must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var size = obj.get("size").getAsInt();
        var discardChanceOnAirExposure = obj.has("discard_chance_on_air_exposure")
                ? obj.get("discard_chance_on_air_exposure").getAsFloat()
                : 0.0F;

        var targets = new ArrayList<TargetBlockState>();
        for (var targetElement : obj.getAsJsonArray("targets")) {
            var targetObj = targetElement.getAsJsonObject();
            var target = RuleTest.fromJson(targetObj.get("target"), blockTags);
            var state = BlockCodec.CODEC.decode(Transcoder.JSON, targetObj.get("state")).orElseThrow();
            targets.add(new TargetBlockState(target, state));
        }

        return new OreConfiguration(List.copyOf(targets), size, discardChanceOnAirExposure);
    }

    public record TargetBlockState(RuleTest target, Block state) {
    }
}
