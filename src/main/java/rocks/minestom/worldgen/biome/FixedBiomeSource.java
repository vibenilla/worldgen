package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;

public record FixedBiomeSource(Key biome) implements BiomeSource {
    @Override
    public Key biome(int quartX, int quartY, int quartZ) {
        return this.biome;
    }
}

