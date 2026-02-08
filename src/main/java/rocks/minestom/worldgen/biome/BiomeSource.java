package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;

/**
 * Chooses a biome for a coordinate based on climate sampling inputs.
 * This is the source of biome identity for terrain and features, translating
 * climate signals into concrete biome selections across the world.
 */
public interface BiomeSource {
    Key biome(int quartX, int quartY, int quartZ);
}
