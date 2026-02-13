package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record BiasedToBottomIntProvider(int minInclusive, int maxInclusive) implements IntProvider {
    public static final Codec<BiasedToBottomIntProvider> CODEC = StructCodec.struct(
            "min_inclusive", Codec.INT, BiasedToBottomIntProvider::minInclusive,
            "max_inclusive", Codec.INT, BiasedToBottomIntProvider::maxInclusive,
            BiasedToBottomIntProvider::new);

    @Override
    public Key type() {
        return Key.key("minecraft:biased_to_bottom");
    }

    @Override
    public int sample(RandomSource random) {
        if (this.maxInclusive <= this.minInclusive) {
            return this.minInclusive;
        }

        return this.minInclusive + random.nextInt(random.nextInt(this.maxInclusive - this.minInclusive + 1) + 1);
    }
}
