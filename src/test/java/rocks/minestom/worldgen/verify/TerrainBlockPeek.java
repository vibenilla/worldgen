package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.terrain.TerrainData;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * Prints the pre-decoration (noise + surface + carvers) block per position in
 * a box, bypassing feature placement, by reflectively invoking
 * {@code WorldGenerator.terrainData}. Usage: seed dimension minX minY minZ
 * maxX maxY maxZ
 */
public final class TerrainBlockPeek {
    public static void main(String[] args) throws Exception {
        var seed = Long.parseLong(args[0]);
        var dimension = args[1];
        int minX = Integer.parseInt(args[2]), minY = Integer.parseInt(args[3]), minZ = Integer.parseInt(args[4]);
        int maxX = Integer.parseInt(args[5]), maxY = Integer.parseInt(args[6]), maxZ = Integer.parseInt(args[7]);

        MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), seed);
        var generator = switch (dimension) {
            case "nether" -> generators.nether();
            case "end" -> generators.end();
            default -> generators.overworld();
        };
        var settings = switch (dimension) {
            case "nether" -> generators.netherSettings();
            case "end" -> generators.endSettings();
            default -> generators.overworldSettings();
        };

        Method method = generator.getClass().getDeclaredMethod("terrainData", int.class, int.class);
        method.setAccessible(true);

        var settingsMinY = settings.minY();
        var height = settings.height();
        var terrainCache = new HashMap<Long, TerrainData>();

        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                var chunkX = Math.floorDiv(x, 16);
                var chunkZ = Math.floorDiv(z, 16);
                var chunkKey = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
                var data = terrainCache.computeIfAbsent(chunkKey, key -> {
                    try {
                        return (TerrainData) method.invoke(generator, chunkX, chunkZ);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });

                for (var y = minY; y <= maxY; y++) {
                    var yIndex = y - settingsMinY;
                    if (yIndex < 0 || yIndex >= height) {
                        continue;
                    }
                    var index = ((x & 15) * 16 + (z & 15)) * height + yIndex;
                    var block = data.blocks()[index];
                    System.out.println(x + " " + y + " " + z + " " + (block == null ? "air" : block.name()));
                }
            }
        }
        System.exit(0);
    }
}
