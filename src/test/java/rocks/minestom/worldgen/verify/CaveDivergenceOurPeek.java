package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;

/**
 * Prints our own generated blocks in a small box, for the given seed, using
 * the real generator pipeline (loads the surrounding pregen ring so
 * neighbor-dependent decoration is realistic). Usage: seed minX minY minZ
 * maxX maxY maxZ
 */
public final class CaveDivergenceOurPeek {
    public static void main(String[] args) throws Exception {
        var seed = Long.parseLong(args[0]);
        int minX = Integer.parseInt(args[1]), minY = Integer.parseInt(args[2]), minZ = Integer.parseInt(args[3]);
        int maxX = Integer.parseInt(args[4]), maxY = Integer.parseInt(args[5]), maxZ = Integer.parseInt(args[6]);

        MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), seed);
        var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setGenerator(generators.overworld());

        var minChunkX = Math.floorDiv(minX, 16) - 2;
        var maxChunkX = Math.floorDiv(maxX, 16) + 2;
        var minChunkZ = Math.floorDiv(minZ, 16) - 2;
        var maxChunkZ = Math.floorDiv(maxZ, 16) + 2;
        var futures = new java.util.ArrayList<java.util.concurrent.CompletableFuture<?>>();
        for (var chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (var chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                futures.add(instance.loadChunk(chunkX, chunkZ));
            }
        }
        java.util.concurrent.CompletableFuture.allOf(futures.toArray(java.util.concurrent.CompletableFuture[]::new)).join();

        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var block = instance.getBlock(x, y, z);
                    System.out.println(x + "," + y + "," + z + " " + block.key().asString() + block.properties());
                }
            }
        }
        System.exit(0);
    }
}
