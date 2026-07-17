package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.density.DensityFunction;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares the nether noise-router climate channels against the vanilla server's
 * RandomState evaluation at scattered world positions. The nether uses the legacy
 * random source and the dedicated nether/temperature and nether/vegetation noises,
 * so this covers the legacy nether biome noise wiring specifically.
 */
final class NetherRouterParityTest {
    private static final long SEED = 123456789L;

    @Test
    void netherRouterMatchesVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var settings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.NETHER).value();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);
        var vanillaRouter = randomState.router();

        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), SEED);
        var ours = generators.netherSettings();

        var random = new Random(42);
        for (var i = 0; i < 300; i++) {
            var x = random.nextInt(-100000, 100000);
            var y = random.nextInt(0, 128);
            var z = random.nextInt(-100000, 100000);
            var vanillaContext = new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z);
            var ourContext = new DensityFunction.SinglePointContext(x, y, z);

            assertEquals(vanillaRouter.temperature().compute(vanillaContext),
                    ours.climateSampler().temperature().compute(ourContext), 0.0, "temperature at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.vegetation().compute(vanillaContext),
                    ours.climateSampler().humidity().compute(ourContext), 0.0, "vegetation at " + x + "," + y + "," + z);
            assertEquals(vanillaRouter.finalDensity().compute(vanillaContext),
                    ours.finalDensity().compute(ourContext), 0.0, "finalDensity at " + x + "," + y + "," + z);
        }
    }

    @Test
    void netherBiomesMatchVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var settings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.NETHER).value();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);
        var vanillaSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(
                lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists.NETHER));

        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), SEED);
        var ourSource = generators.netherBiomes();

        var random = new Random(7);
        for (var i = 0; i < 2000; i++) {
            var quartX = random.nextInt(-25000, 25000);
            var quartY = random.nextInt(0, 32);
            var quartZ = random.nextInt(-25000, 25000);
            var vanillaBiome = vanillaSource.getNoiseBiome(quartX, quartY, quartZ, randomState.sampler())
                    .unwrapKey().orElseThrow().identifier().toString();
            var ourBiome = ourSource.biome(quartX, quartY, quartZ).asString();
            assertEquals(vanillaBiome, ourBiome, "biome at quart " + quartX + "," + quartY + "," + quartZ);
        }
    }
}
