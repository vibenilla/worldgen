package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

public record SimpleBlockConfiguration(BlockStateProvider toPlace, boolean scheduleTick) implements FeatureConfiguration {
    public static final Codec<SimpleBlockConfiguration> CODEC = StructCodec.struct(
            "to_place", BlockStateProviders.CODEC, SimpleBlockConfiguration::toPlace,
            "schedule_tick", Codec.BOOLEAN.optional(false), SimpleBlockConfiguration::scheduleTick,
            SimpleBlockConfiguration::new);
}
