package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.terrain.TerrainData;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Dumps the pre-decoration (noise + surface + carvers) floor height per
 * column for a box, bypassing feature placement entirely, by reflectively
 * invoking {@code WorldGenerator.terrainData}. The floor height is the
 * topmost SOLID or SOLID_OTHER block with AIR/FLUID directly above it,
 * searched downward from maxY. Usage: seed minX maxX minZ maxZ minY maxY
 */
public final class CaveDivergenceTerrainPeek {
    public static void main(String[] args) throws Exception {
        var seed = Long.parseLong(args[0]);
        var minX = Integer.parseInt(args[1]);
        var maxX = Integer.parseInt(args[2]);
        var minZ = Integer.parseInt(args[3]);
        var maxZ = Integer.parseInt(args[4]);
        var minY2 = Integer.parseInt(args[5]);
        var maxY2 = Integer.parseInt(args[6]);

        MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), seed);
        var generator = generators.overworld();

        Method method = generator.getClass().getDeclaredMethod("terrainData", int.class, int.class);
        method.setAccessible(true);

        var minY = generators.overworldSettings().minY();
        var height = generators.overworldSettings().height();

        var terrainCache = new HashMap<Long, TerrainData>();

        for (var z = minZ; z <= maxZ; z++) {
            var row = new StringBuilder();
            row.append(z).append(' ');
            for (var x = minX; x <= maxX; x++) {
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

                var startX = chunkX * 16;
                var startZ = chunkZ * 16;
                var localX = x - startX;
                var localZ = z - startZ;

                Integer floor = null;
                for (var y = maxY2; y >= minY2; y--) {
                    var yIndex = y - minY;
                    var maskIndex = (localX * 16 + localZ) * height + yIndex;
                    var state = data.stoneMask()[maskIndex];
                    if (state == TerrainData.SOLID || state == TerrainData.SOLID_OTHER) {
                        floor = y;
                        break;
                    }
                }
                row.append(floor == null ? "." : floor).append(' ');
            }
            System.out.println(row);
        }
        System.exit(0);
    }
}
