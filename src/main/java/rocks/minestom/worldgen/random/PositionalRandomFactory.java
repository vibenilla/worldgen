package rocks.minestom.worldgen.random;

public interface PositionalRandomFactory {
    RandomSource fromHashOf(String value);

    RandomSource fromSeed(long seed);

    RandomSource at(int x, int y, int z);
}
