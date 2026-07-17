package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

/** Port of vanilla's {@code NetherForestVegetationConfig}. */
public record NetherForestVegetationConfig(
        BlockStateProvider stateProvider,
        int spreadWidth,
        int spreadHeight
) implements FeatureConfiguration {
    public static final Codec<NetherForestVegetationConfig> CODEC = StructCodec.struct(
            "state_provider", BlockStateProviders.CODEC, NetherForestVegetationConfig::stateProvider,
            "spread_width", Codec.INT, NetherForestVegetationConfig::spreadWidth,
            "spread_height", Codec.INT, NetherForestVegetationConfig::spreadHeight,
            NetherForestVegetationConfig::new
    );
}
