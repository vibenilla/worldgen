package rocks.minestom.worldgen.random;

public final class Xoroshiro128PlusPlus {
    private long seedLo;
    private long seedHi;

    public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed128bit) {
        this(seed128bit.seedLo(), seed128bit.seedHi());
    }

    public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo;
        this.seedHi = seedHi;

        if ((this.seedLo | this.seedHi) == 0L) {
            this.seedLo = RandomSupport.GOLDEN_RATIO_64;
            this.seedHi = RandomSupport.SILVER_RATIO_64;
        }
    }

    public long nextLong() {
        var low = this.seedLo;
        var high = this.seedHi;
        var result = Long.rotateLeft(low + high, 17) + low;
        high ^= low;
        this.seedLo = Long.rotateLeft(low, 49) ^ high ^ high << 21;
        this.seedHi = Long.rotateLeft(high, 28);
        return result;
    }
}
