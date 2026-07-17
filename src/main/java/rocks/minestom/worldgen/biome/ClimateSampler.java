package rocks.minestom.worldgen.biome;

import rocks.minestom.worldgen.density.DensityFunction;

public final class ClimateSampler {
    private final DensityFunction temperature;
    private final DensityFunction humidity;
    private final DensityFunction continentalness;
    private final DensityFunction erosion;
    private final DensityFunction depth;
    private final DensityFunction weirdness;

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
    }

    public DensityFunction temperature() {
        return this.temperature;
    }

    public DensityFunction humidity() {
        return this.humidity;
    }

    public DensityFunction continentalness() {
        return this.continentalness;
    }

    public DensityFunction weirdness() {
        return this.weirdness;
    }

    public DensityFunction erosion() {
        return this.erosion;
    }

    public DensityFunction depth() {
        return this.depth;
    }

    public Climate.TargetPoint sample(int quartX, int quartY, int quartZ) {
        // Context must be per-call: chunks generate concurrently and a shared
        // mutable context races across threads
        var context = new DensityFunction.SinglePointContext(quartX << 2, quartY << 2, quartZ << 2);
        return this.sample(context);
    }

    /**
     * Samples with a caller-managed context, allowing column-cached evaluation
     * during chunk biome fills.
     */
    public Climate.TargetPoint sample(DensityFunction.Context context) {
        return Climate.target(
                (float) this.temperature.compute(context),
                (float) this.humidity.compute(context),
                (float) this.continentalness.compute(context),
                (float) this.erosion.compute(context),
                (float) this.depth.compute(context),
                (float) this.weirdness.compute(context)
        );
    }
}
