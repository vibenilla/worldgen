package rocks.minestom.worldgen.random;

/**
 * Random source used for chunk decoration. Derives values with the legacy
 * bit-extraction algebra while pulling bits from a wrapped (usually Xoroshiro)
 * source, and provides the decoration/feature seeding scheme vanilla uses to
 * make feature placement deterministic per chunk, step, and feature index.
 */
public final class WorldgenRandom implements RandomSource {
    private final RandomSource randomSource;

    public WorldgenRandom(RandomSource randomSource) {
        this.randomSource = randomSource;
    }

    public long setDecorationSeed(long seed, int minBlockX, int minBlockZ) {
        this.setSeed(seed);
        var xScale = this.nextLong() | 1L;
        var zScale = this.nextLong() | 1L;
        var result = minBlockX * xScale + minBlockZ * zScale ^ seed;
        this.setSeed(result);
        return result;
    }

    public void setFeatureSeed(long decorationSeed, int index, int step) {
        this.setSeed(decorationSeed + index + 10000L * step);
    }

    /**
     * Vanilla's carver/large-feature seeding: unlike the decoration seed the
     * scales are not forced odd and the mix uses xor instead of addition.
     */
    public void setLargeFeatureSeed(long seed, int chunkX, int chunkZ) {
        this.setSeed(seed);
        var xScale = this.nextLong();
        var zScale = this.nextLong();
        var result = chunkX * xScale ^ chunkZ * zScale ^ seed;
        this.setSeed(result);
    }

    private int next(int bits) {
        // Vanilla WorldgenRandom pulls bits straight from a legacy source
        // (one LCG step per call) instead of composing them from nextLong.
        if (this.randomSource instanceof LegacyRandomSource legacySource) {
            return legacySource.next(bits);
        }
        return (int) (this.randomSource.nextLong() >>> 64 - bits);
    }

    @Override
    public RandomSource fork() {
        return this.randomSource.fork();
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return this.randomSource.forkPositional();
    }

    @Override
    public void setSeed(long seed) {
        this.randomSource.setSeed(seed);
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
        var upper = this.next(32);
        var lower = this.next(32);
        return ((long) upper << 32) + lower;
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
}
