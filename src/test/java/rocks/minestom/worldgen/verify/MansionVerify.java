package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.WorldGenerator;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Scans a vanilla world save for woodland mansion structure starts within a
 * chunk range, compares against this library's locateStructure result, and
 * runs a block-level diff over the mansion bounding box.
 *
 * <p>Usage: MansionVerify &lt;vanillaWorldDir&gt; &lt;datapackDir&gt; &lt;seed&gt;
 * &lt;minChunkX&gt; &lt;minChunkZ&gt; &lt;maxChunkX&gt; &lt;maxChunkZ&gt;
 */
public final class MansionVerify {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var datapackDir = Path.of(args[1]);
        var seed = Long.parseLong(args[2]);
        var minChunkX = Integer.parseInt(args[3]);
        var minChunkZ = Integer.parseInt(args[4]);
        var maxChunkX = Integer.parseInt(args[5]);
        var maxChunkZ = Integer.parseInt(args[6]);

        System.out.println("=== scanning vanilla structure starts for mansion ===");
        var regions = new HashMap<Long, RegionFile>();
        Integer mansionChunkX = null;
        Integer mansionChunkZ = null;
        CompoundBinaryTag mansionStart = null;
        for (var chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (var chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var regionKey = ((long) Math.floorDiv(chunkX, 32) << 32) | (Math.floorDiv(chunkZ, 32) & 0xffffffffL);
                var finalChunkX = chunkX;
                var finalChunkZ = chunkZ;
                var region = regions.computeIfAbsent(regionKey, unused -> {
                    try {
                        return new RegionFile(worldDir.resolve("region").resolve(
                                "r." + Math.floorDiv(finalChunkX, 32) + "." + Math.floorDiv(finalChunkZ, 32) + ".mca"));
                    } catch (Exception exception) {
                        return null;
                    }
                });
                if (region == null) {
                    continue;
                }
                var chunkTag = region.readChunk(chunkX, chunkZ);
                if (chunkTag == null) {
                    continue;
                }
                var starts = chunkTag.getCompound("structures").getCompound("starts");
                for (var entry : starts) {
                    if (!entry.getKey().contains("mansion")) {
                        continue;
                    }
                    var start = (CompoundBinaryTag) entry.getValue();
                    if ("INVALID".equals(start.getString("id", "INVALID"))) {
                        continue;
                    }
                    System.out.println(entry.getKey() + " starts at chunk " + chunkX + "," + chunkZ
                            + " children=" + start.getList("Children").size()
                            + " BB=" + java.util.Arrays.toString(start.getIntArray("BB")));
                    mansionChunkX = chunkX;
                    mansionChunkZ = chunkZ;
                    mansionStart = start;
                }
            }
        }

        if (mansionStart == null) {
            System.out.println("No mansion start found in scanned chunk range ["
                    + minChunkX + ".." + maxChunkX + "] x [" + minChunkZ + ".." + maxChunkZ + "]");
        }

        System.out.println("=== our locateStructure result ===");
        var generators = new WorldGenerators(datapackDir, seed);
        var worldGenerator = (WorldGenerator) generators.overworld();
        var centerChunkX = (minChunkX + maxChunkX) / 2;
        var centerChunkZ = (minChunkZ + maxChunkZ) / 2;
        var located = worldGenerator.locateStructure(Key.key("minecraft:mansion"),
                centerChunkX * 16, centerChunkZ * 16, Math.max(maxChunkX - minChunkX, maxChunkZ - minChunkZ) + 4);
        System.out.println("locateStructure(minecraft:mansion) around chunk " + centerChunkX + "," + centerChunkZ
                + " -> " + located);

        if (mansionStart == null) {
            System.exit(0);
            return;
        }

        var minX = Integer.MAX_VALUE;
        var minY = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        var maxX = Integer.MIN_VALUE;
        var maxY = Integer.MIN_VALUE;
        var maxZ = Integer.MIN_VALUE;
        for (var childTag : mansionStart.getList("Children")) {
            var child = (CompoundBinaryTag) childTag;
            var childBB = child.getIntArray("BB");
            if (childBB.length != 6) {
                continue;
            }
            minX = Math.min(minX, childBB[0]);
            minY = Math.min(minY, childBB[1]);
            minZ = Math.min(minZ, childBB[2]);
            maxX = Math.max(maxX, childBB[3]);
            maxY = Math.max(maxY, childBB[4]);
            maxZ = Math.max(maxZ, childBB[5]);
        }
        System.out.println("Mansion bounding box: [" + minX + "," + minY + "," + minZ + "] .. ["
                + maxX + "," + maxY + "," + maxZ + "]");

        System.out.println("=== block diff over mansion bounding box ===");
        MinecraftServer.init();
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(generators.overworld());

        var minBoxChunkX = minX >> 4;
        var maxBoxChunkX = maxX >> 4;
        var minBoxChunkZ = minZ >> 4;
        var maxBoxChunkZ = maxZ >> 4;
        for (var chunkX = minBoxChunkX - 2; chunkX <= maxBoxChunkX + 2; chunkX++) {
            for (var chunkZ = minBoxChunkZ - 2; chunkZ <= maxBoxChunkZ + 2; chunkZ++) {
                instance.loadChunk(chunkX, chunkZ).join();
            }
        }

        Map<String, Integer> mismatches = new TreeMap<>();
        var total = 0L;
        var matched = 0L;
        var printed = 0;
        var chunkMismatchCounts = new TreeMap<String, Integer>();
        for (var chunkX = minBoxChunkX; chunkX <= maxBoxChunkX; chunkX++) {
            for (var chunkZ = minBoxChunkZ; chunkZ <= maxBoxChunkZ; chunkZ++) {
                var regionKey = ((long) Math.floorDiv(chunkX, 32) << 32) | (Math.floorDiv(chunkZ, 32) & 0xffffffffL);
                var finalChunkX = chunkX;
                var finalChunkZ = chunkZ;
                var region = regions.computeIfAbsent(regionKey, unused -> {
                    try {
                        return new RegionFile(worldDir.resolve("region").resolve(
                                "r." + Math.floorDiv(finalChunkX, 32) + "." + Math.floorDiv(finalChunkZ, 32) + ".mca"));
                    } catch (Exception exception) {
                        return null;
                    }
                });
                if (region == null) {
                    continue;
                }
                var chunkTag = region.readChunk(chunkX, chunkZ);
                if (chunkTag == null) {
                    continue;
                }
                var vanillaChunk = VanillaChunk.parse(chunkTag);
                if (vanillaChunk == null) {
                    continue;
                }
                var chunk = instance.loadChunk(chunkX, chunkZ).join();
                var chunkMismatchCount = 0;

                for (var y = minY; y <= maxY; y++) {
                    for (var localZ = 0; localZ < 16; localZ++) {
                        var worldZ = chunkZ * 16 + localZ;
                        if (worldZ < minZ || worldZ > maxZ) {
                            continue;
                        }
                        for (var localX = 0; localX < 16; localX++) {
                            var worldX = chunkX * 16 + localX;
                            if (worldX < minX || worldX > maxX) {
                                continue;
                            }
                            var expected = vanillaChunk.block(localX, y, localZ);
                            if (expected == null) {
                                continue;
                            }
                            var actual = canonical(chunk.getBlock(localX, y, localZ));
                            total++;
                            if (expected.equals(actual)) {
                                matched++;
                            } else {
                                chunkMismatchCount++;
                                mismatches.merge(strip(expected) + " -> " + strip(actual), 1, Integer::sum);
                                var filter = System.getProperty("mansion.diffFilter");
                                var matchesFilter = filter == null || expected.contains(filter) || actual.contains(filter);
                                var printLimit = Integer.getInteger("mansion.diffLimit", 60);
                                if (matchesFilter && printed < printLimit) {
                                    printed++;
                                    System.out.printf("DIFF %d,%d,%d %s -> %s%n", worldX, y, worldZ, expected, actual);
                                }
                            }
                        }
                    }
                }
                if (chunkMismatchCount > 0) {
                    chunkMismatchCounts.put(chunkX + "," + chunkZ, chunkMismatchCount);
                }
            }
        }

        System.out.printf("Mansion box accuracy: %d / %d (%.4f%%)%n", matched, total, 100.0 * matched / Math.max(total, 1));
        System.out.println("--- Top block mismatches (expected -> actual) ---");
        mismatches.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(40)
                .forEach(entry -> System.out.printf("%10d  %s%n", entry.getValue(), entry.getKey()));
        System.out.println("--- Per-chunk mismatch counts ---");
        chunkMismatchCounts.forEach((chunkKey, count) -> System.out.printf("%10d  chunk %s%n", count, chunkKey));
        System.exit(0);
    }

    private static String strip(String state) {
        var bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    private static String canonical(Block block) {
        return VanillaChunk.canonical(block.key().asString(), block.properties());
    }
}
