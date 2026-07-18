package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;

/**
 * Compares our biome source against the vanilla ground-truth world at a
 * quart position. Usage: vanillaWorldDir seed quartX quartY quartZ
 */
public final class CaveDivergenceBiomePeek {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var seed = Long.parseLong(args[1]);
        var quartX = Integer.parseInt(args[2]);
        var quartY = Integer.parseInt(args[3]);
        var quartZ = Integer.parseInt(args[4]);

        MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), seed);
        var biomeSource = generators.overworldBiomes();
        var ourBiome = biomeSource.biome(quartX, quartY, quartZ);

        var chunkX = Math.floorDiv(quartX, 4);
        var chunkZ = Math.floorDiv(quartZ, 4);
        var region = new RegionFile(worldDir.resolve("region")
                .resolve("r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca"));
        var chunk = VanillaChunk.parse(region.readChunk(chunkX, chunkZ));
        var localQuartX = Math.floorMod(quartX, 4);
        var localQuartZ = Math.floorMod(quartZ, 4);
        var vanillaBiome = chunk.biome(localQuartX, quartY, localQuartZ);

        System.out.println("quart=" + quartX + "," + quartY + "," + quartZ
                + " ours=" + ourBiome + " vanilla=" + vanillaBiome);
        System.exit(0);
    }
}
