package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.noise.NormalNoise;
import rocks.minestom.worldgen.random.LegacyRandomSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class LegacyNetherNoiseProbe {
    private static final long SEED = 123456789L;

    @Test
    void legacyNetherBiomeNoiseMatchesVanilla() {
        var vanillaNoise = net.minecraft.world.level.levelgen.synth.NormalNoise.createLegacyNetherBiome(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(SEED),
                new net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters(-7, 1.0, 1.0));
        var ourNoise = NormalNoise.createLegacyNetherBiome(
                new LegacyRandomSource(SEED),
                new NormalNoise.NoiseParameters(-7, new double[]{1.0, 1.0}));

        for (var i = 0; i < 100; i++) {
            var x = (i * 7919.0) * 0.25 - 12000.0;
            var z = (i * 104729.0) * 0.25 - 90000.0;
            assertEquals(vanillaNoise.getValue(x, 0.0, z), ourNoise.getValue(x, 0.0, z), 0.0, "value at " + x + ",0," + z);
        }
    }
}
