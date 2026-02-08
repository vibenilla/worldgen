package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.density.DensityFunction;

public final class TheEndBiomeSource implements BiomeSource {
    private static final Key THE_END = Key.key("minecraft:the_end");
    private static final Key END_HIGHLANDS = Key.key("minecraft:end_highlands");
    private static final Key END_MIDLANDS = Key.key("minecraft:end_midlands");
    private static final Key SMALL_END_ISLANDS = Key.key("minecraft:small_end_islands");
    private static final Key END_BARRENS = Key.key("minecraft:end_barrens");

    private final ClimateSampler climateSampler;
    private final DensityFunction.Context context;

    public TheEndBiomeSource(ClimateSampler climateSampler) {
        this.climateSampler = climateSampler;
        this.context = new SinglePointContext();
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
            ((SinglePointContext) this.context).setBlock(centerBlockX, blockY, centerBlockZ);
            var erosion = this.climateSampler.erosion().compute(this.context);

            if (erosion > 0.25D) {
                return END_HIGHLANDS;
            } else if (erosion >= -0.0625D) {
                return END_MIDLANDS;
            } else {
                return erosion < -0.21875D ? SMALL_END_ISLANDS : END_BARRENS;
            }
        }
    }

    private static final class SinglePointContext implements DensityFunction.Context {
        private int blockX;
        private int blockY;
        private int blockZ;

        private void setBlock(int blockX, int blockY, int blockZ) {
            this.blockX = blockX;
            this.blockY = blockY;
            this.blockZ = blockZ;
        }

        @Override
        public int blockX() {
            return this.blockX;
        }

        @Override
        public int blockY() {
            return this.blockY;
        }

        @Override
        public int blockZ() {
            return this.blockZ;
        }
    }
}
