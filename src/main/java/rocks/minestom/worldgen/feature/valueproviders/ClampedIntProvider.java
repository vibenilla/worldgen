package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record ClampedIntProvider(IntProvider source, int minInclusive, int maxInclusive) implements IntProvider {
    public static final Codec<ClampedIntProvider> CODEC = StructCodec.struct(
            "source", IntProvider.CODEC, ClampedIntProvider::source,
            "min_inclusive", Codec.INT, ClampedIntProvider::minInclusive,
            "max_inclusive", Codec.INT, ClampedIntProvider::maxInclusive,
            ClampedIntProvider::new);

    @Override
    public Key type() {
        return Key.key("minecraft:clamped");
    }

    @Override
    public int sample(RandomSource random) {
        var sampled = this.source.sample(random);
        if (sampled < this.minInclusive) {
            return this.minInclusive;
        }

        if (sampled > this.maxInclusive) {
            return this.maxInclusive;
        }

        return sampled;
    }
}
