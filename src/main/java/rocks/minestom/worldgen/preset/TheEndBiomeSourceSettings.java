package rocks.minestom.worldgen.preset;

import net.minestom.server.codec.StructCodec;

public record TheEndBiomeSourceSettings() implements BiomeSourceSettings {
    public static final StructCodec<TheEndBiomeSourceSettings> CODEC = StructCodec.struct(TheEndBiomeSourceSettings::new);
}
