package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;
import java.util.HashMap;

/**
 * Position-level diff of tree-related blocks (logs, leaves, litter, vines,
 * bee nests) against the vanilla region files, for debugging tree parity.
 */
public final class TreeDiff {
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;

    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args.length > 0 ? args[0] : "data/vanilla-world/world/dimensions/minecraft/overworld");
        var datapackDir = Path.of(args.length > 1 ? args[1] : "data/mc/datapack");
        var seed = args.length > 2 ? Long.parseLong(args[2]) : 123456789L;
        var radius = args.length > 3 ? Integer.parseInt(args[3]) : 3;

        MinecraftServer.init();
        var generators = new WorldGenerators(datapackDir, seed);
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(generators.overworld());

        var reverse = Boolean.getBoolean("treediff.reverse");
        // Replicate the pregen's exact FEATURES completion order (see
        // VanillaComparison.decorationOrder): each scanline forceload
        // decorates its not-yet-decorated 3x3, x outer / z inner ascending.
        var pregenRadius = Integer.getInteger("treediff.pregenRadius", 16);
        for (var key : VanillaComparison.decorationOrder(pregenRadius)) {
            var loadX = (int) (key >> 32);
            var loadZ = key.intValue();
            var chunkX = reverse ? -loadX - 1 : loadX;
            instance.loadChunk(chunkX, loadZ).join();
        }

        var vanillaColumn = System.getProperty("treediff.vanillacolumn");
        if (vanillaColumn != null) {
            var parts = vanillaColumn.split(",");
            var columnX = Integer.parseInt(parts[0]);
            var columnZ = Integer.parseInt(parts[1]);
            var region = new RegionFile(worldDir.resolve("region")
                    .resolve("r." + Math.floorDiv(columnX >> 4, 32) + "." + Math.floorDiv(columnZ >> 4, 32) + ".mca"));
            var chunkTag = region.readChunk(columnX >> 4, columnZ >> 4);
            var vanillaChunk = chunkTag != null ? VanillaChunk.parse(chunkTag) : null;
            for (var y = 40; y < 100; y++) {
                var state = vanillaChunk != null ? vanillaChunk.block(columnX & 15, y, columnZ & 15) : "<no chunk>";
                if (state != null && !state.equals("minecraft:air")) {
                    System.out.println("VCOLUMN " + columnX + " " + y + " " + columnZ + " " + state);
                }
            }
            System.exit(0);
        }

        var regionCache = new HashMap<Long, RegionFile>();
        for (var chunkX = -radius - 2; chunkX < radius + 1; chunkX++) {
            for (var chunkZ = -radius - 2; chunkZ < radius; chunkZ++) {
                var regionX = Math.floorDiv(chunkX, 32);
                var regionZ = Math.floorDiv(chunkZ, 32);
                var regionKey = (long) regionX << 32 | (regionZ & 0xFFFFFFFFL);
                var region = regionCache.computeIfAbsent(regionKey, key -> {
                    try {
                        return new RegionFile(worldDir.resolve("region").resolve("r." + regionX + "." + regionZ + ".mca"));
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
                diffChunk(chunk, vanillaChunk, chunkX, chunkZ);
            }
        }

        System.exit(0);
    }

    private static void diffChunk(Chunk chunk, VanillaChunk vanillaChunk, int chunkX, int chunkZ) {
        var column = System.getProperty("treediff.column");
        if (column != null) {
            var parts = column.split(",");
            var columnX = Integer.parseInt(parts[0]);
            var columnZ = Integer.parseInt(parts[1]);
            if (chunkX == (columnX >> 4) && chunkZ == (columnZ >> 4)) {
                var columnMinY = Integer.getInteger("treediff.columnMinY", 55);
                var columnMaxY = Integer.getInteger("treediff.columnMaxY", 90);
                for (var y = columnMinY; y < columnMaxY; y++) {
                    System.out.printf("COLUMN %d %d %d | vanilla %s | ours %s%n",
                            columnX, y, columnZ,
                            vanillaChunk.block(columnX & 15, y, columnZ & 15),
                            canonical(chunk.getBlock(columnX & 15, y, columnZ & 15)));
                }
            }
        }
        if (Boolean.getBoolean("treediff.dist7")) {
            for (var z = 0; z < 16; z++) {
                for (var x = 0; x < 16; x++) {
                    for (var y = 60; y < 100; y++) {
                        var expected = vanillaChunk.block(x, y, z);
                        if (expected != null && expected.contains("leaves") && expected.contains("distance=7")) {
                            System.out.printf("VDIST7 %d %d %d %s%n", chunkX * 16 + x, y, chunkZ * 16 + z, expected);
                        }
                        var ours = canonical(chunk.getBlock(x, y, z));
                        if (ours.contains("leaves") && ours.contains("distance=7")) {
                            System.out.printf("ODIST7 %d %d %d %s%n", chunkX * 16 + x, y, chunkZ * 16 + z, ours);
                        }
                        if (expected != null && expected.startsWith("minecraft:leaf_litter")) {
                            var below = vanillaChunk.block(x, y - 1, z);
                            System.out.printf("VLITTERON %s%n", below == null ? "null" : below.split("\\[")[0]);
                        }
                        if (ours.startsWith("minecraft:leaf_litter")) {
                            var below = canonical(chunk.getBlock(x, y - 1, z));
                            System.out.printf("OLITTERON %s%n", below.split("\\[")[0]);
                        }
                    }
                }
            }
        }
        if (Boolean.getBoolean("treediff.stacked")) {
            for (var z = 0; z < 16; z++) {
                for (var x = 0; x < 16; x++) {
                    for (var y = 62; y < 110; y++) {
                        var expected = vanillaChunk.block(x, y, z);
                        var below = vanillaChunk.block(x, y - 1, z);
                        if (expected != null && below != null && expected.contains("_log[axis=y]")
                                && (below.contains("leaves") || below.equals("minecraft:air"))) {
                            System.out.printf("VSTACKED %d %d %d on %s%n", chunkX * 16 + x, y, chunkZ * 16 + z, below.split("\\[")[0]);
                        }
                        var ours = canonical(chunk.getBlock(x, y, z));
                        var oursBelow = canonical(chunk.getBlock(x, y - 1, z));
                        if (ours.contains("_log[axis=y]")
                                && (oursBelow.contains("leaves") || oursBelow.equals("minecraft:air"))) {
                            System.out.printf("OSTACKED %d %d %d on %s%n", chunkX * 16 + x, y, chunkZ * 16 + z, oursBelow.split("\\[")[0]);
                        }
                    }
                }
            }
        }
        if (Boolean.getBoolean("treediff.trunks")) {
            for (var z = 0; z < 16; z++) {
                for (var x = 0; x < 16; x++) {
                    for (var y = 60; y < 90; y++) {
                        var expected = vanillaChunk.block(x, y, z);
                        if (expected == null) {
                            continue;
                        }
                        if (expected.contains("_log[axis=y]")
                                && !(vanillaChunk.block(x, y - 1, z) != null && vanillaChunk.block(x, y - 1, z).contains("_log"))) {
                            System.out.printf("VTRUNK %d %d %d %s%n", chunkX * 16 + x, y, chunkZ * 16 + z, expected);
                        }
                        if (expected.startsWith("minecraft:leaf_litter")) {
                            System.out.printf("VLITTER %d %d %d %s%n", chunkX * 16 + x, y, chunkZ * 16 + z, expected);
                        }
                    }
                }
            }
        }

        for (var quartY = MIN_Y / 4; quartY < (MAX_Y + 1) / 4; quartY++) {
            for (var quartZ = 0; quartZ < 4; quartZ++) {
                for (var quartX = 0; quartX < 4; quartX++) {
                    var expected = vanillaChunk.biome(quartX, quartY, quartZ);
                    if (expected == null) {
                        continue;
                    }
                    var actual = chunk.getBiome(quartX * 4, quartY * 4, quartZ * 4).key().asString();
                    if (!expected.equals(actual)) {
                        System.out.printf("BIOME %d %d %d | expected %s | actual %s%n",
                                chunkX * 4 + quartX, quartY, chunkZ * 4 + quartZ, expected, actual);
                    }
                }
            }
        }

        for (var y = MIN_Y; y <= MAX_Y; y++) {
            for (var z = 0; z < 16; z++) {
                for (var x = 0; x < 16; x++) {
                    var expected = vanillaChunk.block(x, y, z);
                    if (expected == null) {
                        continue;
                    }

                    var actual = canonical(chunk.getBlock(x, y, z));
                    if (expected.equals(actual)) {
                        continue;
                    }

                    if (Boolean.getBoolean("treediff.all") || isTreeRelated(expected) || isTreeRelated(actual)) {
                        System.out.printf("%d %d %d | expected %s | actual %s%n",
                                chunkX * 16 + x, y, chunkZ * 16 + z, expected, actual);
                    }
                }
            }
        }
    }

    private static boolean isTreeRelated(String state) {
        return state.contains("leaves") || state.contains("_log") || state.contains("leaf_litter")
                || state.contains("bee_nest") || state.contains("vine");
    }

    private static String canonical(Block block) {
        return VanillaChunk.canonical(block.key().asString(), block.properties());
    }
}
