package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record TrapezoidIntProvider(int minInclusive, int maxInclusive, int plateau) implements IntProvider {
    public static final Codec<TrapezoidIntProvider> CODEC = StructCodec.struct(
            "min", Codec.INT, TrapezoidIntProvider::minInclusive,
            "max", Codec.INT, TrapezoidIntProvider::maxInclusive,
            "plateau", Codec.INT, TrapezoidIntProvider::plateau,
            TrapezoidIntProvider::new
    );

    @Override
    public Key type() {
        return Key.key("minecraft:trapezoid");
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
        if (this.plateau == 0 && this.maxInclusive == -this.minInclusive) {
            return random.nextInt(this.maxInclusive + 1) - random.nextInt(this.maxInclusive + 1);
        }

        var range = this.maxInclusive - this.minInclusive;
        if (this.plateau == range) {
            return this.minInclusive + random.nextInt(range + 1);
        }

        var plateauStart = (range - this.plateau) / 2;
        var plateauEnd = range - plateauStart;
        return this.minInclusive + random.nextInt(plateauEnd + 1) + random.nextInt(plateauStart + 1);
    }
}
