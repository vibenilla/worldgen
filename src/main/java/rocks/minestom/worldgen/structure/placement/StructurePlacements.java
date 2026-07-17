package rocks.minestom.worldgen.structure.placement;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.structure.StructurePlacement;

public final class StructurePlacements {
    private static final Codec<RandomSpreadPlacementData> RANDOM_SPREAD_CODEC = StructCodec.struct(
            "spacing", Codec.INT, RandomSpreadPlacementData::spacing,
            "separation", Codec.INT, RandomSpreadPlacementData::separation,
            "salt", Codec.INT, RandomSpreadPlacementData::salt,
            "spread_type", Codec.STRING.optional("linear"), RandomSpreadPlacementData::spreadType,
            "frequency", Codec.FLOAT.optional(1.0f), RandomSpreadPlacementData::frequency,
            "frequency_reduction_method", Codec.STRING.optional("default"), RandomSpreadPlacementData::frequencyReductionMethod,
            RandomSpreadPlacementData::new
    );

    private StructurePlacements() {
    }

    public static StructurePlacement parsePlacement(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Structure placement must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var type = obj.get("type").getAsString();

        if (type.equals("minecraft:random_spread")) {
            var decoded = RANDOM_SPREAD_CODEC.decode(Transcoder.JSON, json).orElseThrow();
            var spreadType = parseSpreadType(decoded.spreadType());
            var frequencyReduction = FrequencyReduction.fromName(decoded.frequencyReductionMethod());
            return new RandomSpreadPlacement(decoded.spacing(), decoded.separation(), decoded.salt(), spreadType,
                    decoded.frequency(), frequencyReduction);
        }

        return null;
    }

    private static RandomSpreadType parseSpreadType(String value) {
        if (value == null) {
            return RandomSpreadType.LINEAR;
        }

        return switch (value) {
            case "triangular" -> RandomSpreadType.TRIANGULAR;
            default -> RandomSpreadType.LINEAR;
        };
    }

    private record RandomSpreadPlacementData(int spacing, int separation, int salt, String spreadType,
                                             float frequency, String frequencyReductionMethod) {
    }
}
