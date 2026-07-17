package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record UniformFloatProvider(float minInclusive, float maxExclusive) implements FloatProvider {
    public static final Codec<UniformFloatProvider> CODEC = StructCodec.struct(
            "min_inclusive", Codec.FLOAT, UniformFloatProvider::minInclusive,
            "max_exclusive", Codec.FLOAT, UniformFloatProvider::maxExclusive,
            UniformFloatProvider::new);

    @Override
    public Key type() {
        return Key.key("minecraft:uniform");
    }

    @Override
    public float sample(RandomSource random) {
        return random.nextFloat() * (this.maxExclusive - this.minInclusive) + this.minInclusive;
    }
}
