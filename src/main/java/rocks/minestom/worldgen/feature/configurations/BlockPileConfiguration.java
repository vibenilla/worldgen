package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

public record BlockPileConfiguration(BlockStateProvider stateProvider) implements FeatureConfiguration {
    public static final Codec<BlockPileConfiguration> CODEC = StructCodec.struct(
            "state_provider", BlockStateProviders.CODEC, BlockPileConfiguration::stateProvider,
            BlockPileConfiguration::new
    );
}
