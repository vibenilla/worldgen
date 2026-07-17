package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.density.DensityFunction;

import java.util.List;

public final class TheEndBiomeSource implements BiomeSource {
    private static final Key THE_END = Key.key("minecraft:the_end");
    private static final Key END_HIGHLANDS = Key.key("minecraft:end_highlands");
    private static final Key END_MIDLANDS = Key.key("minecraft:end_midlands");
    private static final Key SMALL_END_ISLANDS = Key.key("minecraft:small_end_islands");
    private static final Key END_BARRENS = Key.key("minecraft:end_barrens");

    private final ClimateSampler climateSampler;

    public TheEndBiomeSource(ClimateSampler climateSampler) {
        this.climateSampler = climateSampler;
    }

    @Override
    public List<Key> possibleBiomes() {
        return List.of(THE_END, END_HIGHLANDS, END_MIDLANDS, SMALL_END_ISLANDS, END_BARRENS);
    }

    @Override
    public Key biome(int quartX, int quartY, int quartZ) {
        var blockX = quartX << 2;
        var blockY = quartY << 2;
        var blockZ = quartZ << 2;
        var sectionX = blockX >> 4;
        var sectionZ = blockZ >> 4;

        if ((long) sectionX * (long) sectionX + (long) sectionZ * (long) sectionZ <= 4096L) {
            return THE_END;
        } else {
            var centerBlockX = (sectionX * 2 + 1) * 8;
            var centerBlockZ = (sectionZ * 2 + 1) * 8;
            var context = new DensityFunction.SinglePointContext(centerBlockX, blockY, centerBlockZ);
            var erosion = this.climateSampler.erosion().compute(context);

            if (erosion > 0.25D) {
                return END_HIGHLANDS;
            } else if (erosion >= -0.0625D) {
                return END_MIDLANDS;
            } else {
                return erosion < -0.21875D ? SMALL_END_ISLANDS : END_BARRENS;
            }
        }
    }

}
