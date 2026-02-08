package rocks.minestom.worldgen.feature.configurations;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.EndSpikeFeature;
import rocks.minestom.worldgen.feature.FeatureConfiguration;

import java.util.List;

public record SpikeConfiguration(boolean crystalInvulnerable, List<EndSpikeFeature.EndSpike> spikes) implements FeatureConfiguration {
    public static final Codec<SpikeConfiguration> CODEC = StructCodec.struct(
            "crystal_invulnerable", Codec.BOOLEAN.optional(false), SpikeConfiguration::crystalInvulnerable,
            "spikes", EndSpikeFeature.EndSpike.CODEC.list(), SpikeConfiguration::spikes,
            SpikeConfiguration::new
    );
}
