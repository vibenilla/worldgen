package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.EndSpikeFeature;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

import java.util.List;

public record EndSpikeConfiguration(boolean crystalInvulnerable, List<EndSpikeFeature.EndSpike> spikes) implements FeatureConfiguration {
    public static final Codec<EndSpikeConfiguration> CODEC = StructCodec.struct(
            "crystal_invulnerable", Codec.BOOLEAN.optional(false), EndSpikeConfiguration::crystalInvulnerable,
            "spikes", EndSpikeFeature.EndSpike.CODEC.list(), EndSpikeConfiguration::spikes,
            EndSpikeConfiguration::new
    );
}
