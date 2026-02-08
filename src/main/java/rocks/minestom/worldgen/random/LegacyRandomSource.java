package rocks.minestom.worldgen.random;

import rocks.minestom.worldgen.VMath;

import java.util.concurrent.atomic.AtomicLong;

public final class LegacyRandomSource implements RandomSource {
    private static final long MODULUS_MASK = 281474976710655L;
    private static final long MULTIPLIER = 25214903917L;
    private static final long INCREMENT = 11L;

    private final AtomicLong seed = new AtomicLong();

    public LegacyRandomSource(long seed) {
        this.setSeed(seed);
    }

    @Override
    public RandomSource fork() {
        return new LegacyRandomSource(this.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new LegacyPositionalRandomFactory(this.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.seed.set((seed ^ MULTIPLIER) & MODULUS_MASK);
    }

    private int next(int bits) {
        var seedValue = this.seed.get();
        var next = seedValue * MULTIPLIER + INCREMENT & MODULUS_MASK;
        this.seed.set(next);
        return (int) (next >> 48 - bits);
    }

    @Override
    public int nextInt() {
        return this.next(32);
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }

        if ((bound & bound - 1) == 0) {
            return (int) ((long) bound * (long) this.next(31) >> 31);
        }

        int bits;
        int value;
        do {
            bits = this.next(31);
            value = bits % bound;
        } while (bits - value + (bound - 1) < 0);
        return value;
    }

    @Override
    public long nextLong() {
        return (long) this.next(32) << 32 | (long) this.next(32) & 0xFFFFFFFFL;
    }

    @Override
    public boolean nextBoolean() {
        return this.next(1) != 0;
    }

    @Override
    public float nextFloat() {
        return (float) this.next(24) / (float) (1 << 24);
    }

    @Override
    public double nextDouble() {
        return (double) (((long) this.next(26) << 27) + (long) this.next(27)) / (double) (1L << 53);
    }

    public static final class LegacyPositionalRandomFactory implements PositionalRandomFactory {
        private final long seed;

        public LegacyPositionalRandomFactory(long seed) {
            this.seed = seed;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            var seed = VMath.getSeed(x, y, z);
            var mixed = seed ^ this.seed;
            return new LegacyRandomSource(mixed);
        }

        @Override
        public RandomSource fromHashOf(String value) {
            var hash = value.hashCode();
            return new LegacyRandomSource((long) hash ^ this.seed);
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new LegacyRandomSource(seed);
        }
    }
}
