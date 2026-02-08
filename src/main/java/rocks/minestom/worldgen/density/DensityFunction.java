package rocks.minestom.worldgen.density;

/**
 * A scalar field sampled across the world to drive terrain and climate decisions.
 * Density functions are the fundamental building blocks for landmasses, caves,
 * biome climate parameters, and the final solid/air decision.
 */
public interface DensityFunction {
    double compute(Context context);

    interface Context {
        int blockX();

        int blockY();

        int blockZ();
    }
}
