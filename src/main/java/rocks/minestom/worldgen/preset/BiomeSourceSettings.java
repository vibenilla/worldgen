package rocks.minestom.worldgen.preset;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public sealed interface BiomeSourceSettings permits FixedBiomeSourceSettings, MultiNoiseBiomeSourceSettings, TheEndBiomeSourceSettings {
    StructCodec<BiomeSourceSettings> CODEC = Codec.KEY.<BiomeSourceSettings>unionType(
            "type",
            type -> switch (type.asString()) {
                case "minecraft:fixed" -> FixedBiomeSourceSettings.CODEC;
                case "minecraft:multi_noise" -> MultiNoiseBiomeSourceSettings.CODEC;
                case "minecraft:the_end" -> TheEndBiomeSourceSettings.CODEC;
                default -> throw new IllegalArgumentException("Unknown biome source type: " + type.asString());
            },
            settings -> {
                throw new UnsupportedOperationException("Encoding is not supported");
            }
    );
}

