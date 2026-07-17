package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

public record ConstantFloatProvider(float value) implements FloatProvider {
    public static final Codec<ConstantFloatProvider> CODEC = StructCodec.struct(
            "value", Codec.FLOAT, ConstantFloatProvider::value,
            ConstantFloatProvider::new);

    @Override
    public Key type() {
        return Key.key("minecraft:constant");
    }

    @Override
    public float sample(RandomSource random) {
        return this.value;
    }
}
