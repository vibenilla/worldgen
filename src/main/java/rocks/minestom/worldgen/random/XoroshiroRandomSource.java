package rocks.minestom.worldgen.random;

import rocks.minestom.worldgen.VMath;

public final class XoroshiroRandomSource implements RandomSource {
    private static final float FLOAT_UNIT = 5.9604645E-8F;
    private static final double DOUBLE_UNIT = 1.110223E-16F;

    private Xoroshiro128PlusPlus randomNumberGenerator;

    public XoroshiroRandomSource(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    public XoroshiroRandomSource(long seedLo, long seedHi) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(seedLo, seedHi);
    }

    @Override
    public RandomSource fork() {
        return new XoroshiroRandomSource(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public PositionalRandomFactory forkPositional() {
        return new XoroshiroPositionalRandomFactory(this.randomNumberGenerator.nextLong(), this.randomNumberGenerator.nextLong());
    }

    @Override
    public void setSeed(long seed) {
        this.randomNumberGenerator = new Xoroshiro128PlusPlus(RandomSupport.upgradeSeedTo128bit(seed));
    }

    @Override
    public int nextInt() {
        return (int) this.randomNumberGenerator.nextLong();
    }

    @Override
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("Bound must be positive");
        }

        var value = Integer.toUnsignedLong(this.nextInt());
        var product = value * (long) bound;
        var low = product & 0xFFFFFFFFL;
        if (low < (long) bound) {
            for (var threshold = Integer.remainderUnsigned(~bound + 1, bound); low < (long) threshold; low = product & 0xFFFFFFFFL) {
                value = Integer.toUnsignedLong(this.nextInt());
                product = value * (long) bound;
            }
        }

        return (int) (product >> 32);
    }

    @Override
    public long nextLong() {
        return this.randomNumberGenerator.nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return (this.randomNumberGenerator.nextLong() & 1L) != 0L;
    }

    @Override
    public float nextFloat() {
        return (float) this.nextBits(24) * FLOAT_UNIT;
    }

    @Override
    public double nextDouble() {
        return (double) this.nextBits(53) * DOUBLE_UNIT;
    }

    private long nextBits(int bitCount) {
        return this.randomNumberGenerator.nextLong() >>> 64 - bitCount;
    }

    public static final class XoroshiroPositionalRandomFactory implements PositionalRandomFactory {
        private final long seedLo;
        private final long seedHi;

        public XoroshiroPositionalRandomFactory(long seedLo, long seedHi) {
            this.seedLo = seedLo;
            this.seedHi = seedHi;
        }

        @Override
        public RandomSource at(int x, int y, int z) {
            var seed = VMath.getSeed(x, y, z);
            var low = seed ^ this.seedLo;
            return new XoroshiroRandomSource(low, this.seedHi);
        }

        @Override
        public RandomSource fromHashOf(String value) {
            var seed128bit = RandomSupport.seedFromHashOf(value);
            var mixed = seed128bit.xor(this.seedLo, this.seedHi);
            return new XoroshiroRandomSource(mixed.seedLo(), mixed.seedHi());
        }

        @Override
        public RandomSource fromSeed(long seed) {
            return new XoroshiroRandomSource(seed ^ this.seedLo, seed ^ this.seedHi);
        }
    }
}
