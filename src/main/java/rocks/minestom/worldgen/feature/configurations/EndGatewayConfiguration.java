package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

import java.util.Optional;

/**
 * Exact port of vanilla {@code EndGatewayConfiguration}. Holds the optional
 * exit position used to route a teleporting entity to another gateway or the
 * overworld spawn, and whether that exit position is exact or should be
 * treated as an approximate search origin. The exit position is only ever
 * consumed by the gateway block entity, which this codebase does not model;
 * see {@link rocks.minestom.worldgen.feature.EndGatewayFeature} for details.
 */
public record EndGatewayConfiguration(Optional<BlockVec> exit, boolean exact) implements FeatureConfiguration {
    public static EndGatewayConfiguration fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        var exact = object.has("exact") && object.get("exact").getAsBoolean();
        if (!object.has("exit")) {
            return new EndGatewayConfiguration(Optional.empty(), exact);
        }

        var exitArray = object.getAsJsonArray("exit");
        var exit = new BlockVec(
                exitArray.get(0).getAsInt(),
                exitArray.get(1).getAsInt(),
                exitArray.get(2).getAsInt());
        return new EndGatewayConfiguration(Optional.of(exit), exact);
    }
}
