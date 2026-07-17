package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.RandomState;
import rocks.minestom.worldgen.datapack.DataPack;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares this library's positional random factories and vertical-gradient
 * condition evaluation against the unobfuscated vanilla server classes for
 * the deepslate and bedrock_floor surface rule gradients.
 */
final class VerticalGradientParityTest {
    private static final long SEED = 123456789L;

    @Test
    void positionalFactoriesMatchVanillaForGradientNames() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var settings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value();
        var vanillaRandomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);

        var dataPack = new DataPack(Path.of("data/mc/datapack"));
        var ourRandomState = new RandomState(dataPack, SEED, false);

        for (var name : new String[] {"minecraft:deepslate", "minecraft:bedrock_floor"}) {
            var vanillaFactory = vanillaRandomState.getOrCreateRandomFactory(net.minecraft.resources.Identifier.parse(name));
            var ourFactory = ourRandomState.getOrCreateRandomFactory(Key.key(name));

            var random = new Random(name.hashCode());
            for (var i = 0; i < 2000; i++) {
                var x = random.nextInt(-1000, 1000);
                var y = random.nextInt(-80, 20);
                var z = random.nextInt(-1000, 1000);

                var vanillaSource = vanillaFactory.at(x, y, z);
                var ourSource = ourFactory.at(x, y, z);

                assertEquals(vanillaSource.nextFloat(), ourSource.nextFloat(),
                        "nextFloat at " + name + " " + x + "," + y + "," + z);
            }
        }
    }

    @Test
    void verticalGradientChanceMatchesVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var settings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value();
        var vanillaRandomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);

        var dataPack = new DataPack(Path.of("data/mc/datapack"));
        var ourRandomState = new RandomState(dataPack, SEED, false);

        // deepslate gradient: true_at_and_below y=0, false_at_and_above y=8
        checkGradient(vanillaRandomState, ourRandomState, "minecraft:deepslate", 0, 8, -1000, 1000);

        // bedrock_floor gradient: true_at_and_below above_bottom 0 (-64), false_at_and_above above_bottom 5 (-59)
        checkGradient(vanillaRandomState, ourRandomState, "minecraft:bedrock_floor", -64, -59, -1000, 1000);
    }

    private void checkGradient(net.minecraft.world.level.levelgen.RandomState vanillaRandomState,
                                RandomState ourRandomState, String name, int trueY, int falseY,
                                int negBound, int posBound) {
        var vanillaFactory = vanillaRandomState.getOrCreateRandomFactory(net.minecraft.resources.Identifier.parse(name));
        var ourFactory = ourRandomState.getOrCreateRandomFactory(Key.key(name));

        var random = new Random(name.hashCode() ^ 0xABCDEFL);
        var mismatches = 0;
        var total = 0;
        for (var i = 0; i < 5000; i++) {
            var x = random.nextInt(negBound, posBound);
            var y = trueY + 1 + random.nextInt(falseY - trueY - 1);
            var z = random.nextInt(negBound, posBound);

            var chance = map((double) y, (double) trueY, (double) falseY, 1.0, 0.0);

            var vanillaResult = (double) vanillaFactory.at(x, y, z).nextFloat() < chance;
            var ourResult = (double) ourFactory.at(x, y, z).nextFloat() < chance;

            total++;
            if (vanillaResult != ourResult) {
                mismatches++;
            }
        }

        assertEquals(0, mismatches, name + " mismatches out of " + total);
    }

    private static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        var delta = (value - inMin) / (inMax - inMin);
        return outMin + delta * (outMax - outMin);
    }
}
