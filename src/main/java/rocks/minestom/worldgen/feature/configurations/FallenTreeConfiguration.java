package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorators;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

import java.util.List;

public record FallenTreeConfiguration(
        BlockStateProvider trunkProvider,
        IntProvider logLength,
        List<TreeDecorator> stumpDecorators,
        List<TreeDecorator> logDecorators
) implements FeatureConfiguration {
    public static final Codec<FallenTreeConfiguration> CODEC = StructCodec.struct(
            "trunk_provider", BlockStateProviders.CODEC, FallenTreeConfiguration::trunkProvider,
            "log_length", IntProvider.CODEC, FallenTreeConfiguration::logLength,
            "stump_decorators", TreeDecorators.CODEC.list().optional(List.of()), FallenTreeConfiguration::stumpDecorators,
            "log_decorators", TreeDecorators.CODEC.list().optional(List.of()), FallenTreeConfiguration::logDecorators,
            FallenTreeConfiguration::new
    );
}
