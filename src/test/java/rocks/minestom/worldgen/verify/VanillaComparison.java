package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Generates chunks with this library and compares them block-by-block and
 * biome-by-biome against a vanilla-generated world, printing an accuracy report.
 *
 * <p>Usage: VanillaComparison &lt;vanillaWorldDir&gt; &lt;datapackDir&gt; &lt;seed&gt; &lt;chunkRadius&gt;
 */
public final class VanillaComparison {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var datapackDir = Path.of(args[1]);
        var seed = Long.parseLong(args[2]);
        var radius = Integer.parseInt(args[3]);

        MinecraftServer.init();
        var generators = new WorldGenerators(datapackDir, seed);
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        var dimension = System.getProperty("compare.dimension", "overworld");
        rocks.minestom.worldgen.biome.BiomeSource sourceBiomes;
        int minY;
        int maxY;
        switch (dimension) {
            case "nether" -> {
                instance.setGenerator(generators.nether());
                sourceBiomes = generators.netherBiomes();
                minY = 0;
                maxY = 255;
            }
            case "end" -> {
                instance.setGenerator(generators.end());
                sourceBiomes = generators.endBiomes();
                minY = 0;
                maxY = 255;
            }
            default -> {
                instance.setGenerator(generators.overworld());
                sourceBiomes = generators.overworldBiomes();
                minY = -64;
                maxY = 319;
            }
        }

        var regionCache = new HashMap<Long, RegionFile>();
        var stats = new Stats();
        var startTime = System.nanoTime();
        var generationNanos = 0L;

        // Generate one extra ring so neighbor decorations (tree canopies etc.)
        // spill into the compared chunks like they do in vanilla
        var sequential = Boolean.getBoolean("compare.sequential");
        var generationStart = System.nanoTime();
        var loadFutures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<?>>();
        // Replicate the ground-truth pregen's effective decoration order: the
        // vanilla world was made with strictly sequential single-chunk
        // forceloads in scanline order over [-16,15]^2, and the chunk-status
        // ladder decorates ring-2 of each forceloaded chunk (FULL needs LIGHT
        // ring-1 needs FEATURES ring-2). So chunk (X,Z) is decorated when the
        // first forceload whose ring-2 square covers it runs — z-major batches
        // in the west band, scanline elsewhere. Dense-canopy biomes couple
        // neighboring chunks through OCEAN_FLOOR heightmaps and would_survive,
        // so the decoration ORDER (which neighbors already wrote canopies)
        // must match vanilla's or a chunk-to-chunk cascade seeds at the edges.
        var pregenRadius = Integer.getInteger("compare.pregenRadius", 16);
        for (var key : decorationOrder(pregenRadius)) {
            var chunkX = (int) (key >> 32);
            var chunkZ = key.intValue();
            var future = instance.loadChunk(chunkX, chunkZ);
            if (sequential) {
                future.join();
            }
            loadFutures.add(future);
        }
        java.util.concurrent.CompletableFuture.allOf(loadFutures.toArray(java.util.concurrent.CompletableFuture[]::new)).join();
        generationNanos = System.nanoTime() - generationStart;

        for (var chunkX = -radius; chunkX < radius; chunkX++) {
            for (var chunkZ = -radius; chunkZ < radius; chunkZ++) {
                var regionX = Math.floorDiv(chunkX, 32);
                var regionZ = Math.floorDiv(chunkZ, 32);
                var regionKey = (long) regionX << 32 | (regionZ & 0xFFFFFFFFL);
                var region = regionCache.computeIfAbsent(regionKey, key -> {
                    var regionPath = worldDir.resolve("region").resolve("r." + regionX + "." + regionZ + ".mca");
                    try {
                        return new RegionFile(regionPath);
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
                var beforeMismatch = stats.totalBlocks - stats.matchedBlocks;
                compareChunk(chunk, vanillaChunk, stats, minY, maxY);
                var chunkMismatch = stats.totalBlocks - stats.matchedBlocks - beforeMismatch;
                if (chunkMismatch > 0) {
                    stats.chunkMismatches.put(chunkX + "," + chunkZ, chunkMismatch);
                }
                compareSourceBiomes(sourceBiomes, chunkX, chunkZ, vanillaChunk, stats, minY, maxY);
            }
        }

        var totalSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
        stats.generatedChunks = loadFutures.size();
        stats.print(generationNanos / 1_000_000.0, totalSeconds);
        System.exit(0);
    }

    /**
     * The exact FEATURES-stage completion order of the pregen ground truth,
     * derived from vanilla's ChunkGenerationTask/ChunkPyramid: the step to
     * FULL accumulates dependencies [SPAWN, INITIALIZE_LIGHT, CARVERS,
     * BIOMES, STRUCTURE_STARTS x8], so the FEATURES layer of a FULL target
     * covers radius 1 (INITIALIZE_LIGHT at distance 1 is the outermost
     * status that is or-after FEATURES). ChunkGenerationTask.scheduleLayer
     * iterates the layer square x outer, z inner, both ascending, and the
     * single worker executes the queued steps in that order, skipping chunks
     * already decorated. Sequential single-chunk forceloads in scanline
     * order over [-pregenRadius, pregenRadius)^2 therefore decorate
     * [-pregenRadius-1, pregenRadius]^2, with the three westernmost columns
     * advancing together as x-major z-triples and every later column in
     * plain z-ascending order.
     */
    static java.util.List<Long> decorationOrder(int pregenRadius) {
        var done = new java.util.HashSet<Long>();
        var order = new java.util.ArrayList<Long>();
        for (var forceX = -pregenRadius; forceX < pregenRadius; forceX++) {
            for (var forceZ = -pregenRadius; forceZ < pregenRadius; forceZ++) {
                // A forceload ticket (level 31) makes chunks at chebyshev
                // distance <= 2 FULL targets and distance-3 chunks
                // INITIALIZE_LIGHT targets. Tasks pop by priority (= ticket
                // level, closest first). A FULL-target task's FEATURES layer
                // covers radius 1 (x outer / z inner ascending); an
                // INITIALIZE_LIGHT-target task decorates only itself.
                for (var distance = 0; distance <= 3; distance++) {
                    for (var taskX = forceX - distance; taskX <= forceX + distance; taskX++) {
                        for (var taskZ = forceZ - distance; taskZ <= forceZ + distance; taskZ++) {
                            if (Math.max(Math.abs(taskX - forceX), Math.abs(taskZ - forceZ)) != distance) {
                                continue;
                            }

                            var layerRadius = distance <= 2 ? 1 : 0;
                            for (var chunkX = taskX - layerRadius; chunkX <= taskX + layerRadius; chunkX++) {
                                for (var chunkZ = taskZ - layerRadius; chunkZ <= taskZ + layerRadius; chunkZ++) {
                                    var key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
                                    if (done.add(key)) {
                                        order.add(key);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return order;
    }

    private static void compareChunk(Chunk chunk, VanillaChunk vanillaChunk, Stats stats, int minY, int maxY) {
        stats.chunks++;
        for (var y = minY; y <= maxY; y++) {
            for (var z = 0; z < 16; z++) {
                for (var x = 0; x < 16; x++) {
                    var expected = vanillaChunk.block(x, y, z);
                    if (expected == null) {
                        continue;
                    }

                    var actual = canonical(chunk.getBlock(x, y, z));
                    stats.totalBlocks++;
                    if (expected.equals(actual)) {
                        stats.matchedBlocks++;
                    } else {
                        stats.recordMismatch(expected, actual, y);
                        var diffBlock = System.getProperty("compare.diffblock", "");
                        if (!diffBlock.isEmpty() && (expected.contains(diffBlock) || actual.contains(diffBlock))
                                && stats.diffPrinted < 25) {
                            stats.diffPrinted++;
                            System.out.printf("DIFF %d,%d,%d expected=%s actual=%s%n",
                                    chunk.getChunkX() * 16 + x, y, chunk.getChunkZ() * 16 + z, expected, actual);
                        }
                    }
                }
            }
        }

        for (var quartY = Math.floorDiv(minY, 4); quartY < Math.floorDiv(maxY + 1, 4); quartY++) {
            for (var quartZ = 0; quartZ < 4; quartZ++) {
                for (var quartX = 0; quartX < 4; quartX++) {
                    var expected = vanillaChunk.biome(quartX, quartY, quartZ);
                    if (expected == null) {
                        continue;
                    }

                    var actual = chunk.getBiome(quartX * 4, quartY * 4, quartZ * 4).key().asString();
                    stats.totalBiomes++;
                    if (expected.equals(actual)) {
                        stats.matchedBiomes++;
                    } else {
                        stats.biomeMismatches.merge(expected + " -> " + actual, 1L, Long::sum);
                        if (Boolean.getBoolean("compare.biomediffpos")) {
                            System.out.printf("BIOMEDIFF chunk=%d,%d quart=%d,%d,%d expected=%s actual=%s%n",
                                    chunk.getChunkX(), chunk.getChunkZ(), quartX, quartY, quartZ, expected, actual);
                        }
                    }
                }
            }
        }
    }

    private static String canonical(Block block) {
        return VanillaChunk.canonical(block.key().asString(), block.properties());
    }

    private static void compareSourceBiomes(rocks.minestom.worldgen.biome.BiomeSource source, int chunkX, int chunkZ,
            VanillaChunk vanillaChunk, Stats stats, int minY, int maxY) {
        for (var quartY = Math.floorDiv(minY, 4); quartY < Math.floorDiv(maxY + 1, 4); quartY++) {
            for (var quartZ = 0; quartZ < 4; quartZ++) {
                for (var quartX = 0; quartX < 4; quartX++) {
                    var expected = vanillaChunk.biome(quartX, quartY, quartZ);
                    if (expected == null) {
                        continue;
                    }

                    var actual = source.biome(chunkX * 4 + quartX, quartY, chunkZ * 4 + quartZ).asString();
                    stats.totalSourceBiomes++;
                    if (expected.equals(actual)) {
                        stats.matchedSourceBiomes++;
                    }
                }
            }
        }
    }

    private static final class Stats {
        int chunks;
        int generatedChunks;
        int diffPrinted;
        long totalBlocks;
        long matchedBlocks;
        long totalBiomes;
        long matchedBiomes;
        long totalSourceBiomes;
        long matchedSourceBiomes;
        final Map<String, Long> blockMismatches = new HashMap<>();
        final Map<Integer, Long> mismatchesByBand = new TreeMap<>();
        final Map<String, Long> biomeMismatches = new HashMap<>();
        final Map<String, Long> chunkMismatches = new HashMap<>();

        void recordMismatch(String expected, String actual, int y) {
            this.blockMismatches.merge(strip(expected) + " -> " + strip(actual), 1L, Long::sum);
            this.mismatchesByBand.merge(Math.floorDiv(y, 16) * 16, 1L, Long::sum);
        }

        private static String strip(String state) {
            var bracket = state.indexOf('[');
            return bracket < 0 ? state : state.substring(0, bracket);
        }

        void print(double generationMillis, double totalSeconds) {
            System.out.println("==================================================");
            System.out.printf("Chunks compared: %d%n", this.chunks);
            System.out.printf("Block accuracy:  %d / %d (%.4f%%)%n",
                    this.matchedBlocks, this.totalBlocks, 100.0 * this.matchedBlocks / Math.max(1, this.totalBlocks));
            System.out.printf("Biome accuracy:  %d / %d (%.4f%%)%n",
                    this.matchedBiomes, this.totalBiomes, 100.0 * this.matchedBiomes / Math.max(1, this.totalBiomes));
            System.out.printf("Source biome accuracy: %d / %d (%.4f%%)%n",
                    this.matchedSourceBiomes, this.totalSourceBiomes,
                    100.0 * this.matchedSourceBiomes / Math.max(1, this.totalSourceBiomes));
            System.out.printf("Generation time: %.1f ms total, %.2f ms/chunk (%d chunks)%n",
                    generationMillis, generationMillis / Math.max(1, this.generatedChunks), this.generatedChunks);
            System.out.printf("Wall time:       %.1f s%n", totalSeconds);

            System.out.println("--- Top block mismatches (expected -> actual) ---");
            this.blockMismatches.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(30)
                    .forEach(entry -> System.out.printf("%10d  %s%n", entry.getValue(), entry.getKey()));

            System.out.println("--- Mismatches by Y band ---");
            this.mismatchesByBand.forEach((band, count) -> System.out.printf("y=%4d..%4d  %d%n", band, band + 15, count));

            System.out.println("--- Worst chunks ---");
            this.chunkMismatches.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(10)
                    .forEach(entry -> System.out.printf("%10d  chunk %s%n", entry.getValue(), entry.getKey()));

            System.out.println("--- Top biome mismatches ---");
            this.biomeMismatches.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(15)
                    .forEach(entry -> System.out.printf("%10d  %s%n", entry.getValue(), entry.getKey()));
        }
    }
}
