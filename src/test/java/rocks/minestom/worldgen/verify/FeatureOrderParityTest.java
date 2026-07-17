package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the deterministic feature ordering (FeatureSorter) against the
 * vanilla server's own computation, using the server jar on the classpath.
 */
final class FeatureOrderParityTest {

    @Test
    void overworldFeatureOrderMatchesVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var presets = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);

        var vanillaSteps = net.minecraft.world.level.biome.FeatureSorter.buildFeaturesPerStep(
                List.copyOf(biomeSource.possibleBiomes()),
                biome -> biome.value().getGenerationSettings().features(),
                true);

        var placedFeatures = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE);

        // Project side
        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), 123456789L);
        var ourBiomes = generators.overworldBiomes().possibleBiomes();
        var ourSteps = generators.featureLoader().featuresPerStep(ourBiomes);

        assertEquals(vanillaSteps.size(), ourSteps.size(), "step count");
        for (var step = 0; step < vanillaSteps.size(); step++) {
            var vanillaFeatures = vanillaSteps.get(step).features().stream()
                    .map(feature -> placedFeatures.listElements()
                            .filter(holder -> holder.value() == feature)
                            .findFirst()
                            .map(holder -> holder.key().identifier().toString())
                            .orElse("<inline>"))
                    .toList();
            var ourFeatures = ourSteps.get(step).features().stream().map(Key::asString).toList();
            assertEquals(vanillaFeatures, ourFeatures, "step " + step);
        }
    }
}
