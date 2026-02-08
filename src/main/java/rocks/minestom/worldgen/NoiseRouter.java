package rocks.minestom.worldgen;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

/**
 * Defines the named density-function channels that act as the backbone signals for world generation.
 * These channels are the shared inputs used to derive climate values, carve large-scale landmasses,
 * and produce the final density field that decides where solid terrain exists.
 */
public record NoiseRouter(
        Codec.RawValue temperature,
        Codec.RawValue vegetation,
        Codec.RawValue continents,
        Codec.RawValue erosion,
        Codec.RawValue depth,
        Codec.RawValue ridges,
        Codec.RawValue finalDensity
) {
    public static final Codec<NoiseRouter> CODEC = StructCodec.struct(
            "temperature", Codec.RAW_VALUE, NoiseRouter::temperature,
            "vegetation", Codec.RAW_VALUE, NoiseRouter::vegetation,
            "continents", Codec.RAW_VALUE, NoiseRouter::continents,
            "erosion", Codec.RAW_VALUE, NoiseRouter::erosion,
            "depth", Codec.RAW_VALUE, NoiseRouter::depth,
            "ridges", Codec.RAW_VALUE, NoiseRouter::ridges,
            "final_density", Codec.RAW_VALUE, NoiseRouter::finalDensity,
            NoiseRouter::new);
}
