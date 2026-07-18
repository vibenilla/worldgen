package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.endcity.EndCityPieces;
import rocks.minestom.worldgen.structure.endcity.EndCityStructure;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Path;

/**
 * Reproduces our engine's {@code EndCityStructure.findGenerationPoint} and
 * {@link EndCityPieces} assembly for a given start chunk, outside of
 * {@code StructurePlacer}, so its piece list and bounding boxes can be
 * diffed against the vanilla structure-start NBT for the same chunk.
 */
public final class EndCityOurPieceDump {
    public static void main(String[] args) throws Exception {
        var datapackDir = Path.of(args[0]);
        var seed = Long.parseLong(args[1]);
        var chunkX = Integer.parseInt(args[2]);
        var chunkZ = Integer.parseInt(args[3]);

        var generators = new WorldGenerators(datapackDir, seed);
        var settings = generators.endSettings();
        var biomeZoomer = new BiomeZoomer(generators.endBiomes(), generators.biomeZoomSeed());
        var structureLoader = generators.structureLoader();
        var endCity = (EndCityStructure) structureLoader.getStructure(Key.key("minecraft:end_city"));

        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(settings.randomState().seed(), chunkX, chunkZ);
        var rotation = Rotation.getRandom(random);

        var offsetX = 5;
        var offsetZ = 5;
        switch (rotation) {
            case CLOCKWISE_90 -> offsetX = -5;
            case CLOCKWISE_180 -> {
                offsetX = -5;
                offsetZ = -5;
            }
            case COUNTERCLOCKWISE_90 -> offsetZ = -5;
            case NONE -> {
            }
        }

        var blockX = (chunkX << 4) + 7;
        var blockZ = (chunkZ << 4) + 7;
        System.out.println("corner a (" + blockX + "," + blockZ + ")=" + worldSurfaceHeight(blockX, blockZ, settings));
        System.out.println("corner b (" + blockX + "," + (blockZ + offsetZ) + ")=" + worldSurfaceHeight(blockX, blockZ + offsetZ, settings));
        System.out.println("corner c (" + (blockX + offsetX) + "," + blockZ + ")=" + worldSurfaceHeight(blockX + offsetX, blockZ, settings));
        System.out.println("corner d (" + (blockX + offsetX) + "," + (blockZ + offsetZ) + ")=" + worldSurfaceHeight(blockX + offsetX, blockZ + offsetZ, settings));

        var lowestY = getLowestY(blockX, blockZ, offsetX, offsetZ, settings);
        System.out.println("rotation=" + rotation + " lowestY=" + lowestY);
        if (lowestY < 60) {
            System.out.println("REJECTED: lowestY < 60");
            System.exit(0);
        }

        var startPos = new BlockVec(blockX, lowestY, blockZ);
        var biome = biomeZoomer.source().biome(startPos.blockX() >> 2, startPos.blockY() >> 2, startPos.blockZ() >> 2);
        System.out.println("biome at start=" + biome);
        if (endCity == null || !endCity.biomes().matches(biome, structureLoader.biomeTags())) {
            System.out.println("REJECTED: biome mismatch");
            System.exit(0);
        }

        var pieces = EndCityPieces.startHouseTower(structureLoader, startPos, rotation, random);
        System.out.println("piece count=" + pieces.size());
        var index = 0;
        for (var piece : pieces) {
            System.out.println(index + " " + piece.templateKey + " pos=" + piece.position
                    + " rot=" + piece.rotation + " BB=" + piece.boundingBox);
            index++;
        }
        System.exit(0);
    }

    private static int getLowestY(int blockX, int blockZ, int offsetX, int offsetZ,
            rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime settings) {
        var a = worldSurfaceHeight(blockX, blockZ, settings);
        var b = worldSurfaceHeight(blockX, blockZ + offsetZ, settings);
        var c = worldSurfaceHeight(blockX + offsetX, blockZ, settings);
        var d = worldSurfaceHeight(blockX + offsetX, blockZ + offsetZ, settings);
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static int worldSurfaceHeight(int blockX, int blockZ,
            rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = new TerrainGenerator(settings).generate(chunkX, chunkZ);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() : solidTop + 1;
    }
}
