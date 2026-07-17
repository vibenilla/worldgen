package rocks.minestom.worldgen.verify;

import net.minestom.server.MinecraftServer;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads a few chunks in each dimension to catch loader/generation crashes.
 */
final class DimensionSmokeTest {

    @Test
    void allDimensionsGenerate() {
        MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), 123456789L);
        var manager = MinecraftServer.getInstanceManager();

        for (var entry : new Object[][]{
                {"overworld", generators.overworld()},
                {"nether", generators.nether()},
                {"end", generators.end()}}) {
            var instance = manager.createInstanceContainer();
            instance.setGenerator((net.minestom.server.instance.generator.Generator) entry[1]);
            var nonAir = 0;
            for (var chunkX = 0; chunkX < 2; chunkX++) {
                instance.loadChunk(chunkX, 0).join();
                for (var y = -60; y < 250; y += 3) {
                    for (var x = 0; x < 16; x += 4) {
                        if (!instance.getBlock(chunkX * 16 + x, y, 8).isAir()) {
                            nonAir++;
                        }
                    }
                }
            }
            assertTrue(nonAir > 0, entry[0] + " generated no blocks");
            System.out.println(entry[0] + " non-air samples: " + nonAir);
        }
    }
}
