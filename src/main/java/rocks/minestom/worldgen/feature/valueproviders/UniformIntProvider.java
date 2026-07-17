package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record UniformIntProvider(int minInclusive, int maxInclusive) implements IntProvider {
    public static final Codec<UniformIntProvider> CODEC = StructCodec.struct(
            "min_inclusive", Codec.INT, UniformIntProvider::minInclusive,
            "max_inclusive", Codec.INT, UniformIntProvider::maxInclusive,
            UniformIntProvider::new
    );

    @Override
    public Key type() {
        return Key.key("minecraft:uniform");
    }

    @Override
    public int minValue() {
        return this.minInclusive;
    }

    @Override
    public int maxValue() {
        return this.maxInclusive;
    }

    @Override
    public int sample(RandomSource random) {
        // No min==max shortcut: vanilla always consumes one draw here
        return this.minInclusive + random.nextInt(this.maxInclusive - this.minInclusive + 1);
    }
}

