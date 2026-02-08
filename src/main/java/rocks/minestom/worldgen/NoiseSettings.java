package rocks.minestom.worldgen;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

/**
 * Specifies the vertical bounds and noise cell sizing that control terrain scale.
 * These values define the height range of the world and the resolution at which
 * noise is sampled, shaping both terrain detail and generation cost.
 */
public record NoiseSettings(int minY, int height, int sizeHorizontal, int sizeVertical) {
    public static final Codec<NoiseSettings> CODEC = StructCodec.struct(
            "min_y", Codec.INT, NoiseSettings::minY,
            "height", Codec.INT, NoiseSettings::height,
            "size_horizontal", Codec.INT, NoiseSettings::sizeHorizontal,
            "size_vertical", Codec.INT, NoiseSettings::sizeVertical,
            NoiseSettings::new);
}
