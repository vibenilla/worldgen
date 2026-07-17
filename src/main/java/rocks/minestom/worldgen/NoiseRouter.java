package rocks.minestom.worldgen;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

/**
 * Defines the named density-function channels that act as the backbone signals for world generation.
 * These channels are the shared inputs used to derive climate values, carve large-scale landmasses,
 * and produce the final density field that decides where solid terrain exists.
 */
public record NoiseRouter(
        Codec.RawValue barrier,
        Codec.RawValue fluidLevelFloodedness,
        Codec.RawValue fluidLevelSpread,
        Codec.RawValue lava,
        Codec.RawValue temperature,
        Codec.RawValue vegetation,
        Codec.RawValue continents,
        Codec.RawValue erosion,
        Codec.RawValue depth,
        Codec.RawValue ridges,
        Codec.RawValue preliminarySurfaceLevel,
        Codec.RawValue finalDensity,
        Codec.RawValue veinToggle,
        Codec.RawValue veinRidged,
        Codec.RawValue veinGap
) {
    public static final Codec<NoiseRouter> CODEC = StructCodec.struct(
            "barrier", Codec.RAW_VALUE, NoiseRouter::barrier,
            "fluid_level_floodedness", Codec.RAW_VALUE, NoiseRouter::fluidLevelFloodedness,
            "fluid_level_spread", Codec.RAW_VALUE, NoiseRouter::fluidLevelSpread,
            "lava", Codec.RAW_VALUE, NoiseRouter::lava,
            "temperature", Codec.RAW_VALUE, NoiseRouter::temperature,
            "vegetation", Codec.RAW_VALUE, NoiseRouter::vegetation,
            "continents", Codec.RAW_VALUE, NoiseRouter::continents,
            "erosion", Codec.RAW_VALUE, NoiseRouter::erosion,
            "depth", Codec.RAW_VALUE, NoiseRouter::depth,
            "ridges", Codec.RAW_VALUE, NoiseRouter::ridges,
            "preliminary_surface_level", Codec.RAW_VALUE, NoiseRouter::preliminarySurfaceLevel,
            "final_density", Codec.RAW_VALUE, NoiseRouter::finalDensity,
            "vein_toggle", Codec.RAW_VALUE, NoiseRouter::veinToggle,
            "vein_ridged", Codec.RAW_VALUE, NoiseRouter::veinRidged,
            "vein_gap", Codec.RAW_VALUE, NoiseRouter::veinGap,
            NoiseRouter::new);
}
