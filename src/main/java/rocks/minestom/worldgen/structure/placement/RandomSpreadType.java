package rocks.minestom.worldgen.structure.placement;

import rocks.minestom.worldgen.random.RandomSource;

public enum RandomSpreadType {
    LINEAR,
    TRIANGULAR;

    public int sample(RandomSource random, int bound) {
        if (bound <= 0) {
            return 0;
        }

        return switch (this) {
            case LINEAR -> random.nextInt(bound);
            case TRIANGULAR -> (random.nextInt(bound) + random.nextInt(bound)) / 2;
        };
    }
}
