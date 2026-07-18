package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.biome.BiomeZoomer;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares the biome zoomer against vanilla's BiomeManager zoom at scattered
 * positions, both backed by the same climate stack.
 */
final class ZoomParityTest {
    private static final long SEED = 123456789L;

    @Test
    void zoomedBiomesMatchVanillaTargetSeedChunk() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var targetSeed = -1063252586L;
        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var presets = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var vanillaSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);
        var settings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), targetSeed);

        var vanillaManager = new net.minecraft.world.level.biome.BiomeManager(
                (x, y, z) -> vanillaSource.getNoiseBiome(x, y, z, randomState.sampler()),
                net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(targetSeed));

        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), targetSeed);
        var zoomer = new BiomeZoomer(generators.overworldBiomes(), BiomeZoomer.obfuscateSeed(targetSeed));

        var mismatches = 0;
        var total = 0;
        var printed = 0;
        for (var x = -96; x < -80; x++) {
            for (var z = 48; z < 64; z++) {
                for (var y = -60; y < 0; y++) {
                    total++;
                    var vanilla = vanillaManager.getBiome(new net.minecraft.core.BlockPos(x, y, z))
                            .unwrapKey().orElseThrow().identifier().toString();
                    var ours = zoomer.biome(x, y, z).asString();
                    if (!vanilla.equals(ours)) {
                        mismatches++;
                        if (printed < 30) {
                            System.out.println("ZOOMDIFF " + x + "," + y + "," + z + " vanilla=" + vanilla + " ours=" + ours);
                            printed++;
                        }
                    }
                }
            }
        }
        System.out.println("ZOOM total=" + total + " mismatches=" + mismatches);
    }

    @Test
    void zoomedBiomesMatchVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var presets = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var vanillaSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);
        var settings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                settings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);

        var vanillaManager = new net.minecraft.world.level.biome.BiomeManager(
                (x, y, z) -> vanillaSource.getNoiseBiome(x, y, z, randomState.sampler()),
                net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(SEED));

        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), SEED);
        var zoomer = new BiomeZoomer(generators.overworldBiomes(), BiomeZoomer.obfuscateSeed(SEED));

        var random = new Random(7);
        for (var i = 0; i < 2000; i++) {
            var x = random.nextInt(-2000, 2000);
            var y = random.nextInt(-64, 320);
            var z = random.nextInt(-2000, 2000);
            var vanilla = vanillaManager.getBiome(new net.minecraft.core.BlockPos(x, y, z))
                    .unwrapKey().orElseThrow().identifier().toString();
            var ours = zoomer.biome(x, y, z).asString();
            assertEquals(vanilla, ours, "zoomed biome at " + x + "," + y + "," + z);
        }
    }
}
