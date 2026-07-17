package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.structure.placement.ConcentricRingsPlacement;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compares the ported concentric rings stronghold placement against the real
 * vanilla ring calculation for the full 128-position stronghold set.
 */
final class ConcentricRingsParityTest {
    private static final long SEED = 123456789L;

    @Test
    void strongholdRingPositionsMatchVanilla() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var presets = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);
        var noiseSettings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD).value();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                noiseSettings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), SEED);
        var vanillaPositions = vanillaRingPositions(biomeSource, randomState.sampler());

        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), SEED);
        var ourPlacement = new ConcentricRingsPlacement(128, 32, 3, Key.key("minecraft:stronghold_biased_to"));

        var computeMethod = findComputeMethod();
        @SuppressWarnings("unchecked")
        var ourPositions = (java.util.Set<Long>) computeMethod.invoke(ourPlacement, SEED,
                generators.overworldBiomes(), generators.structureLoader().biomeTags());

        var vanillaSet = new HashSet<Long>();
        for (var position : vanillaPositions) {
            vanillaSet.add(((long) position.x() & 0xFFFFFFFFL) | ((long) position.z() << 32));
        }

        assertEquals(vanillaSet, ourPositions, "ring chunk positions");
        assertTrue(vanillaSet.size() > 100, "vanilla should produce many distinct ring chunks");
    }

    /**
     * Vanilla {@code ChunkGeneratorStructureState.generateRingPositions} for the
     * stronghold set (count 128, distance 32, spread 3), run synchronously with
     * real vanilla random and biome search. The preferred-biomes predicate is
     * read from the datapack tag JSON because the isolated registry lookup
     * cannot dereference worldgen biome tags.
     */
    private static List<net.minecraft.world.level.ChunkPos> vanillaRingPositions(
            net.minecraft.world.level.biome.BiomeSource biomeSource,
            net.minecraft.world.level.biome.Climate.Sampler sampler) throws Exception {
        var tagJson = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                Path.of("data/mc/datapack/data/minecraft/tags/worldgen/biome/stronghold_biased_to.json")));
        var allowedNames = new HashSet<String>();
        for (var element : tagJson.getAsJsonObject().getAsJsonArray("values")) {
            allowedNames.add(element.getAsString());
        }
        java.util.function.Predicate<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> allowed =
                holder -> allowedNames.contains(holder.unwrapKey().orElseThrow().identifier().toString());

        var count = 128;
        var distance = 32;
        var spread = 3;
        var positions = new java.util.ArrayList<net.minecraft.world.level.ChunkPos>(count);
        var random = new net.minecraft.world.level.levelgen.LegacyRandomSource(0L);
        random.setSeed(SEED);
        var angle = random.nextDouble() * Math.PI * 2.0;
        var positionInCircle = 0;
        var circle = 0;

        for (var index = 0; index < count; index++) {
            var ringDistance = (double) (4 * distance + distance * circle * 6)
                    + (random.nextDouble() - 0.5) * distance * 2.5;
            var initialX = (int) Math.round(Math.cos(angle) * ringDistance);
            var initialZ = (int) Math.round(Math.sin(angle) * ringDistance);
            var searchRandom = random.fork();
            var found = biomeSource.findBiomeHorizontal(
                    net.minecraft.core.SectionPos.sectionToBlockCoord(initialX, 8), 0,
                    net.minecraft.core.SectionPos.sectionToBlockCoord(initialZ, 8), 112,
                    allowed, searchRandom, sampler);
            if (found != null) {
                var position = found.getFirst();
                positions.add(new net.minecraft.world.level.ChunkPos(
                        net.minecraft.core.SectionPos.blockToSectionCoord(position.getX()),
                        net.minecraft.core.SectionPos.blockToSectionCoord(position.getZ())));
            } else {
                positions.add(new net.minecraft.world.level.ChunkPos(initialX, initialZ));
            }

            angle += (Math.PI * 2) / spread;
            if (++positionInCircle == spread) {
                circle++;
                positionInCircle = 0;
                spread += 2 * spread / (circle + 1);
                spread = Math.min(spread, count - index);
                angle += random.nextDouble() * Math.PI * 2.0;
            }
        }

        return positions;
    }

    private static Method findComputeMethod() throws NoSuchMethodException {
        var method = ConcentricRingsPlacement.class.getDeclaredMethod(
                "computePositions", long.class, rocks.minestom.worldgen.biome.BiomeSource.class,
                rocks.minestom.worldgen.structure.context.BiomeTagManager.class);
        method.setAccessible(true);
        return method;
    }
}
