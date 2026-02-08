package rocks.minestom.worldgen;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.instance.block.Block;

/**
 * Defines the per-dimension knobs that establish the overall terrain character.
 * This ties together the terrain noise layout, base blocks and fluids, sea level,
 * and surface rules so the dimension has a consistent identity.
 */
public record NoiseGeneratorSettings(
        boolean legacyRandomSource,
        NoiseSettings noise,
        int seaLevel,
        Block defaultBlock,
        Block defaultFluid,
        NoiseRouter noiseRouter,
        Codec.RawValue surfaceRule
) {
    public static final Codec<NoiseGeneratorSettings> CODEC = StructCodec.struct(
            "legacy_random_source", Codec.BOOLEAN, NoiseGeneratorSettings::legacyRandomSource,
            "noise", NoiseSettings.CODEC, NoiseGeneratorSettings::noise,
            "sea_level", Codec.INT, NoiseGeneratorSettings::seaLevel,
            "default_block", BlockCodec.CODEC, NoiseGeneratorSettings::defaultBlock,
            "default_fluid", BlockCodec.CODEC, NoiseGeneratorSettings::defaultFluid,
            "noise_router", NoiseRouter.CODEC, NoiseGeneratorSettings::noiseRouter,
            "surface_rule", Codec.RAW_VALUE, NoiseGeneratorSettings::surfaceRule,
            NoiseGeneratorSettings::new);
}
