package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.PlacedFeature;

public record RandomPatchConfiguration(PlacedFeature feature, int tries, int xzSpread, int ySpread)
        implements FeatureConfiguration {
    public static final Codec<RandomPatchConfiguration> CODEC = StructCodec.struct(
            "feature", Codec.RAW_VALUE, RandomPatchConfiguration::featureRaw,
            "tries", Codec.INT, RandomPatchConfiguration::tries,
            "xz_spread", Codec.INT, RandomPatchConfiguration::xzSpread,
            "y_spread", Codec.INT, RandomPatchConfiguration::ySpread,
            RandomPatchConfiguration::decode
    );

    private static Codec.RawValue featureRaw(RandomPatchConfiguration value) {
        throw new UnsupportedOperationException("Encoding is not supported");
    }

    private static RandomPatchConfiguration decode(Codec.RawValue featureRaw, int tries, int xzSpread, int ySpread) {
        var json = featureRaw.convertTo(Transcoder.JSON).orElseThrow();
        return new RandomPatchConfiguration(PlacedFeature.fromJson(json), tries, xzSpread, ySpread);
    }
}
