package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.random.RandomSource;

public record ConstantIntProvider(int value) implements IntProvider {
    @Override
    public Key type() {
        return Key.key("minecraft:constant");
    }

    @Override
    public int minValue() {
        return this.value;
    }

    @Override
    public int maxValue() {
        return this.value;
    }

    @Override
    public int sample(RandomSource random) {
        return this.value;
    }
}

