package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.density.DensityFunction;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares every resolved noise-router channel against the vanilla server's
 * RandomState evaluation at scattered world positions. If these match, noise
 * loading, density-function parsing, and seeding are all correct.
 */
final class NoiseRouterParityTest {
    private static final long SEED = 123456789L;

    @Test
    void overworldRouterMatchesVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var settings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);
        var vanillaRouter = randomState.router();

        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), SEED);
        var ours = generators.overworldSettings();

        var random = new Random(42);
        for (var i = 0; i < 300; i++) {
            var x = random.nextInt(-100000, 100000);
            var y = random.nextInt(-64, 320);
            var z = random.nextInt(-100000, 100000);
            var vanillaContext = new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z);
            var ourContext = new DensityFunction.SinglePointContext(x, y, z);

            assertEquals(vanillaRouter.temperature().compute(vanillaContext),
                    ours.climateSampler().temperature().compute(ourContext), 0.0, "temperature at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.vegetation().compute(vanillaContext),
                    ours.climateSampler().humidity().compute(ourContext), 0.0, "vegetation at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.continents().compute(vanillaContext),
                    ours.climateSampler().continentalness().compute(ourContext), 0.0, "continents at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.erosion().compute(vanillaContext),
                    ours.climateSampler().erosion().compute(ourContext), 0.0, "erosion at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.depth().compute(vanillaContext),
                    ours.climateSampler().depth().compute(ourContext), 0.0, "depth at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.ridges().compute(vanillaContext),
                    ours.climateSampler().weirdness().compute(ourContext), 0.0, "ridges at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.finalDensity().compute(vanillaContext),
                    ours.finalDensity().compute(ourContext), 0.0, "finalDensity at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.barrierNoise().compute(vanillaContext),
                    ours.barrier().compute(ourContext), 0.0, "barrier at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.fluidLevelFloodednessNoise().compute(vanillaContext),
                    ours.fluidLevelFloodedness().compute(ourContext), 0.0, "floodedness at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.fluidLevelSpreadNoise().compute(vanillaContext),
                    ours.fluidLevelSpread().compute(ourContext), 0.0, "spread at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.lavaNoise().compute(vanillaContext),
                    ours.lava().compute(ourContext), 0.0, "lava at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.veinToggle().compute(vanillaContext),
                    ours.veinToggle().compute(ourContext), 0.0, "veinToggle at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.veinRidged().compute(vanillaContext),
                    ours.veinRidged().compute(ourContext), 0.0, "veinRidged at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.veinGap().compute(vanillaContext),
                    ours.veinGap().compute(ourContext), 0.0, "veinGap at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.preliminarySurfaceLevel().compute(vanillaContext),
                    ours.preliminarySurfaceLevel().compute(ourContext), 0.0, "preliminarySurface at " + x + "," + y + "," + z);
        }
    }
}
