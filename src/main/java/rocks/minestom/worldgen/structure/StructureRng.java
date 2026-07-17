package rocks.minestom.worldgen.structure;

import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;

/**
 * Vanilla random helpers shared by the template system and jigsaw assembly.
 */
public final class StructureRng {
    private StructureRng() {
    }

    /**
     * Vanilla {@code Mth.getSeed}. The x scale intentionally multiplies in int
     * space (overflow wraps) before widening, matching vanilla bit-for-bit.
     */
    public static long getSeed(int x, int y, int z) {
        var seed = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        seed = seed * seed * 42317861L + seed * 11L;
        return seed >> 16;
    }

    /**
     * Vanilla {@code Util.shuffle}: Fisher-Yates drawing {@code nextInt(i)}
     * for i = size..2.
     */
    public static <T> void shuffle(List<T> list, RandomSource random) {
        for (var index = list.size(); index > 1; index--) {
            var swapIndex = random.nextInt(index);
            list.set(index - 1, list.set(swapIndex, list.get(index - 1)));
        }
    }

    /** Vanilla {@code Mth.randomBetweenInclusive}. */
    public static int randomBetweenInclusive(RandomSource random, int minInclusive, int maxInclusive) {
        return random.nextInt(maxInclusive - minInclusive + 1) + minInclusive;
    }
}
