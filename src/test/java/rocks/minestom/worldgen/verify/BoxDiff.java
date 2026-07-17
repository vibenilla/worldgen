package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Compares generated blocks against a vanilla world within a bounding box.
 *
 * <p>Usage: BoxDiff &lt;worldDir&gt; &lt;datapack&gt; &lt;seed&gt; &lt;minX&gt; &lt;minY&gt; &lt;minZ&gt; &lt;maxX&gt; &lt;maxY&gt; &lt;maxZ&gt;
 */
public final class BoxDiff {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var datapackDir = Path.of(args[1]);
        var seed = Long.parseLong(args[2]);
        var minX = Integer.parseInt(args[3]);
        var minY = Integer.parseInt(args[4]);
        var minZ = Integer.parseInt(args[5]);
        var maxX = Integer.parseInt(args[6]);
        var maxY = Integer.parseInt(args[7]);
        var maxZ = Integer.parseInt(args[8]);

        MinecraftServer.init();
        var generators = new WorldGenerators(datapackDir, seed);
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(generators.overworld());

        var minChunkX = minX >> 4;
        var maxChunkX = maxX >> 4;
        var minChunkZ = minZ >> 4;
        var maxChunkZ = maxZ >> 4;
        // generate with a margin ring for cross-chunk decoration
        for (var chunkX = minChunkX - 2; chunkX <= maxChunkX + 2; chunkX++) {
            for (var chunkZ = minChunkZ - 2; chunkZ <= maxChunkZ + 2; chunkZ++) {
                instance.loadChunk(chunkX, chunkZ).join();
            }
        }

        var regions = new HashMap<Long, RegionFile>();
        Map<String, Integer> mismatches = new TreeMap<>();
        var total = 0L;
        var matched = 0L;
        var printed = 0;
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
                var vanillaChunk = VanillaChunk.parse(chunkTag);
                if (vanillaChunk == null) {
                    continue;
                }
                var chunk = instance.loadChunk(chunkX, chunkZ).join();

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
                                mismatches.merge(expected + " -> " + actual, 1, Integer::sum);
                                if (printed < 40) {
                                    printed++;
                                    System.out.printf("DIFF %d,%d,%d %s -> %s%n", worldX, y, worldZ, expected, actual);
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.printf("Box accuracy: %d / %d (%.4f%%)%n", matched, total, 100.0 * matched / Math.max(total, 1));
        mismatches.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(30)
                .forEach(entry -> System.out.printf("%10d  %s%n", entry.getValue(), entry.getKey()));
        System.exit(0);
    }

    private static String canonical(Block block) {
        return VanillaChunk.canonical(block.key().asString(), block.properties());
    }
}
