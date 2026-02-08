package rocks.minestom.worldgen.random;

public interface RandomSource {
    RandomSource fork();

    PositionalRandomFactory forkPositional();

    void setSeed(long seed);

    int nextInt();

    int nextInt(int bound);

    long nextLong();

    boolean nextBoolean();

    float nextFloat();

    double nextDouble();

    default void consumeCount(int count) {
        for (var index = 0; index < count; index++) {
            this.nextLong();
        }
    }
}
