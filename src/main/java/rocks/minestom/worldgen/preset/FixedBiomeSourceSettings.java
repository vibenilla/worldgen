package rocks.minestom.worldgen.preset;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public record FixedBiomeSourceSettings(Key biome) implements BiomeSourceSettings {
    public static final StructCodec<FixedBiomeSourceSettings> CODEC = StructCodec.struct(
            "biome", Codec.KEY, FixedBiomeSourceSettings::biome,
            FixedBiomeSourceSettings::new
    );
}
