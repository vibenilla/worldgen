package rocks.minestom.worldgen.structure.mansion;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.terrain.TerrainGenerator;
import rocks.minestom.worldgen.verify.RegionFile;

import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Compares this library's mansion piece list against the pieces stored in a
 * vanilla world save for a given start chunk, replicating
 * {@code MansionPlacer.tryGenerate}'s seeding by hand since that logic is
 * private.
 *
 * <p>Usage: MansionPieceDump &lt;vanillaWorldDir&gt; &lt;datapackDir&gt; &lt;seed&gt; &lt;chunkX&gt; &lt;chunkZ&gt;
 */
public final class MansionPieceDump {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var datapackDir = Path.of(args[1]);
        var seed = Long.parseLong(args[2]);
        var chunkX = Integer.parseInt(args[3]);
        var chunkZ = Integer.parseInt(args[4]);

        var regionPath = worldDir.resolve("region")
                .resolve("r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca");
        var region = new RegionFile(regionPath);
        var chunkTag = region.readChunk(chunkX, chunkZ);
        var starts = chunkTag.getCompound("structures").getCompound("starts");
        CompoundBinaryTag start = null;
        for (var entry : starts) {
            if (entry.getKey().contains("mansion")) {
                start = (CompoundBinaryTag) entry.getValue();
            }
        }
        if (start == null) {
            System.out.println("No mansion start at chunk " + chunkX + "," + chunkZ);
            System.exit(1);
            return;
        }

        var vanillaPieces = new ArrayList<String>();
        for (var childTag : start.getList("Children")) {
            var child = (CompoundBinaryTag) childTag;
            vanillaPieces.add(child.getString("Template")
                    + " pos=" + child.getInt("TPX") + "," + child.getInt("TPY") + "," + child.getInt("TPZ")
                    + " rot=" + child.getString("Rot")
                    + " mi=" + child.getString("Mi"));
        }

        var generators = new WorldGenerators(datapackDir, seed);
        var settings = generators.overworldSettings();
        var randomStateSeed = settings.randomState().seed();

        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(randomStateSeed, chunkX, chunkZ);
        var rotation = Rotation.getRandom(random);
        var startPos = lowestYIn5by5BoxOffset7Blocks(chunkX, chunkZ, rotation, settings);
        System.out.println("startPos=" + startPos + " rotation=" + rotation);

        var wmPieces = MansionPiecePlacer.generateMansion(startPos, rotation, random);
        var ourPieces = new ArrayList<String>();
        for (var piece : wmPieces) {
            ourPieces.add(piece.templateName()
                    + " pos=" + piece.position().blockX() + "," + piece.position().blockY() + "," + piece.position().blockZ()
                    + " rot=" + piece.rotation()
                    + " mi=" + piece.mirror());
        }

        System.out.println("=== vanilla: " + vanillaPieces.size() + " pieces, ours: " + ourPieces.size() + " ===");
        var max = Math.max(vanillaPieces.size(), ourPieces.size());
        var matches = 0;
        var firstMismatch = -1;
        for (var index = 0; index < max; index++) {
            var vanilla = index < vanillaPieces.size() ? vanillaPieces.get(index) : "<none>";
            var ours = index < ourPieces.size() ? ourPieces.get(index) : "<none>";
            var same = vanilla.equals(ours);
            if (same) {
                matches++;
            } else if (firstMismatch < 0) {
                firstMismatch = index;
            }
            if (!same && (firstMismatch < 0 || index - firstMismatch < 30)) {
                System.out.printf("%s #%d%n  vanilla: %s%n  ours:    %s%n", same ? "OK " : "DIFF", index, vanilla, ours);
            }
        }
        System.out.println("=== matching in order: " + matches + "/" + max + " ===");
        System.exit(0);
    }

    private static BlockVec lowestYIn5by5BoxOffset7Blocks(int chunkX, int chunkZ, Rotation rotation,
            rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime settings) {
        var offsetX = 5;
        var offsetZ = 5;
        if (rotation == Rotation.CLOCKWISE_90) {
            offsetX = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            offsetX = -5;
            offsetZ = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            offsetZ = -5;
        }

        var blockX = (chunkX << 4) + 7;
        var blockZ = (chunkZ << 4) + 7;
        var a = worldSurfaceHeight(blockX, blockZ, settings);
        var b = worldSurfaceHeight(blockX, blockZ + offsetZ, settings);
        var c = worldSurfaceHeight(blockX + offsetX, blockZ, settings);
        var d = worldSurfaceHeight(blockX + offsetX, blockZ + offsetZ, settings);
        var lowest = Math.min(Math.min(a, b), Math.min(c, d)) - 1;
        return new BlockVec(blockX, lowest, blockZ);
    }

    private static int worldSurfaceHeight(int blockX, int blockZ, rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = new TerrainGenerator(settings).generate(chunkX, chunkZ);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() : solidTop + 1;
    }
}
