package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record ClampedNormalIntProvider(float mean, float deviation, int minInclusive, int maxInclusive)
        implements IntProvider {
    public static final Codec<ClampedNormalIntProvider> CODEC = StructCodec.struct(
            "mean", Codec.FLOAT, ClampedNormalIntProvider::mean,
            "deviation", Codec.FLOAT, ClampedNormalIntProvider::deviation,
            "min_inclusive", Codec.INT, ClampedNormalIntProvider::minInclusive,
            "max_inclusive", Codec.INT, ClampedNormalIntProvider::maxInclusive,
            ClampedNormalIntProvider::new);

    @Override
    public Key type() {
        return Key.key("minecraft:clamped_normal");
    }

    @Override
    public int sample(RandomSource random) {
        var gaussianValue = nextGaussian(random) * this.deviation + this.mean;
        var clamped = Math.max(this.minInclusive, Math.min(this.maxInclusive, gaussianValue));
        return (int) clamped;
    }

    private static double nextGaussian(RandomSource random) {
        var valueOne = random.nextDouble();
        var valueTwo = random.nextDouble();
        var magnitude = Math.sqrt(-2.0D * Math.log(Math.max(1.0E-7D, valueOne)));
        return magnitude * Math.cos(2.0D * Math.PI * valueTwo);
    }
}
