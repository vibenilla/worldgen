package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.PlacedFeature;

import java.util.ArrayList;
import java.util.List;

public record SimpleRandomSelectorConfiguration(List<PlacedFeature> features) implements FeatureConfiguration {
    public static final Codec<SimpleRandomSelectorConfiguration> CODEC = StructCodec.struct(
            "features", Codec.RAW_VALUE.list(), SimpleRandomSelectorConfiguration::featuresRaw,
            SimpleRandomSelectorConfiguration::decode
    );

    private static List<Codec.RawValue> featuresRaw(SimpleRandomSelectorConfiguration configuration) {
        throw new UnsupportedOperationException("Encoding is not supported");
    }

    private static SimpleRandomSelectorConfiguration decode(List<Codec.RawValue> featuresRaw) {
        var parsedFeatures = new ArrayList<PlacedFeature>();
        for (var featureRaw : featuresRaw) {
            var featureJson = featureRaw.convertTo(Transcoder.JSON).orElseThrow();
            parsedFeatures.add(PlacedFeature.fromJson(featureJson));
        }

        return new SimpleRandomSelectorConfiguration(List.copyOf(parsedFeatures));
    }
}
