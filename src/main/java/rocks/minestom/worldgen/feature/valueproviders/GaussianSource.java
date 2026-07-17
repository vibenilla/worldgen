package rocks.minestom.worldgen.feature.valueproviders;

import rocks.minestom.worldgen.random.RandomSource;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Marsaglia polar gaussian, mirroring vanilla's
 * {@code net.minecraft.world.level.levelgen.MarsagliaPolarGaussian}. Vanilla
 * attaches one gaussian state to each random source instance and the feature
 * random ({@code WorldgenRandom}) never resets it, so the spare deviate is
 * cached per random source here as well to keep the random call order intact.
 */
public final class GaussianSource {
    private static final Map<RandomSource, GaussianSource> SOURCES = Collections.synchronizedMap(new WeakHashMap<>());

    private double nextDeviate;
    private boolean hasNextDeviate;

    private GaussianSource() {
    }

    public static double nextGaussian(RandomSource random) {
        return SOURCES.computeIfAbsent(random, source -> new GaussianSource()).sample(random);
    }

    private double sample(RandomSource random) {
        if (this.hasNextDeviate) {
            this.hasNextDeviate = false;
            return this.nextDeviate;
        }

        while (true) {
            var x = 2.0D * random.nextDouble() - 1.0D;
            var y = 2.0D * random.nextDouble() - 1.0D;
            var radiusSquared = x * x + y * y;
            if (radiusSquared < 1.0D && radiusSquared != 0.0D) {
                var multiplier = Math.sqrt(-2.0D * Math.log(radiusSquared) / radiusSquared);
                this.nextDeviate = y * multiplier;
                this.hasNextDeviate = true;
                return x * multiplier;
            }
        }
    }
}
