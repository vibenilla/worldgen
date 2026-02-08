package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

public record NoneFeatureConfiguration() implements FeatureConfiguration {
    public static final Codec<NoneFeatureConfiguration> CODEC = StructCodec.struct(NoneFeatureConfiguration::new);
}
