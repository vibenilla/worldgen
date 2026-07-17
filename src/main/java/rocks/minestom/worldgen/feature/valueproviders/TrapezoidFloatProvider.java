package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record TrapezoidFloatProvider(float min, float max, float plateau) implements FloatProvider {
    public static final Codec<TrapezoidFloatProvider> CODEC = StructCodec.struct(
            "min", Codec.FLOAT, TrapezoidFloatProvider::min,
            "max", Codec.FLOAT, TrapezoidFloatProvider::max,
            "plateau", Codec.FLOAT, TrapezoidFloatProvider::plateau,
            TrapezoidFloatProvider::new);

    @Override
    public Key type() {
        return Key.key("minecraft:trapezoid");
    }

    @Override
    public float sample(RandomSource random) {
        var range = this.max - this.min;
        var plateauStart = (range - this.plateau) / 2.0F;
        var plateauEnd = range - plateauStart;
        return this.min + random.nextFloat() * plateauEnd + random.nextFloat() * plateauStart;
    }
}
