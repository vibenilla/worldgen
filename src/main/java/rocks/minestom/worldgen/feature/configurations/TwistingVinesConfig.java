package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

/** Port of vanilla's {@code TwistingVinesConfig}. */
public record TwistingVinesConfig(
        int spreadWidth,
        int spreadHeight,
        int maxHeight
) implements FeatureConfiguration {
    public static final Codec<TwistingVinesConfig> CODEC = StructCodec.struct(
            "spread_width", Codec.INT, TwistingVinesConfig::spreadWidth,
            "spread_height", Codec.INT, TwistingVinesConfig::spreadHeight,
            "max_height", Codec.INT, TwistingVinesConfig::maxHeight,
            TwistingVinesConfig::new
    );
}
