package rocks.minestom.worldgen.biome;

import rocks.minestom.worldgen.density.DensityFunction;

public final class ClimateSampler {
    private final DensityFunction temperature;
    private final DensityFunction humidity;
    private final DensityFunction continentalness;
    private final DensityFunction erosion;
    private final DensityFunction depth;
    private final DensityFunction weirdness;
    private final SinglePointContext context;

    public ClimateSampler(
            DensityFunction temperature,
            DensityFunction humidity,
            DensityFunction continentalness,
            DensityFunction erosion,
            DensityFunction depth,
            DensityFunction weirdness
    ) {
        this.temperature = temperature;
        this.humidity = humidity;
        this.continentalness = continentalness;
        this.erosion = erosion;
        this.depth = depth;
        this.weirdness = weirdness;
        this.context = new SinglePointContext();
    }

    public DensityFunction erosion() {
        return this.erosion;
    }

    public Climate.TargetPoint sample(int quartX, int quartY, int quartZ) {
        var blockX = quartX << 2;
        var blockY = quartY << 2;
        var blockZ = quartZ << 2;
        this.context.setBlock(blockX, blockY, blockZ);

        return Climate.target(
                (float) this.temperature.compute(this.context),
                (float) this.humidity.compute(this.context),
                (float) this.continentalness.compute(this.context),
                (float) this.erosion.compute(this.context),
                (float) this.depth.compute(this.context),
                (float) this.weirdness.compute(this.context)
        );
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
