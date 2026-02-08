package rocks.minestom.worldgen.preset;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public record MultiNoiseBiomeSourceSettings(Key preset) implements BiomeSourceSettings {
    public static final StructCodec<MultiNoiseBiomeSourceSettings> CODEC = StructCodec.struct(
            "preset", Codec.KEY, MultiNoiseBiomeSourceSettings::preset,
            MultiNoiseBiomeSourceSettings::new
    );
}
