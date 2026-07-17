package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.WorldGenerator;
import rocks.minestom.worldgen.WorldGenerators;

import java.nio.file.Path;

/** Locates a structure near the origin using our generator, no vanilla comparison. */
public final class LocateOnly {
    public static void main(String[] args) throws Exception {
        var datapackDir = Path.of(args[0]);
        var seed = Long.parseLong(args[1]);
        var structureKey = Key.key(args[2]);
        var centerX = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        var centerZ = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        var radiusChunks = args.length > 5 ? Integer.parseInt(args[5]) : 100;

        var generators = new WorldGenerators(datapackDir, seed);
        var worldGenerator = (WorldGenerator) generators.overworld();
        var located = worldGenerator.locateStructure(structureKey, centerX, centerZ, radiusChunks);
        System.out.println("locateStructure(" + structureKey.asString() + ") around " + centerX + "," + centerZ
                + " radius=" + radiusChunks + " -> " + located);
        if (located != null) {
            System.out.println("chunk = " + (located.blockX() >> 4) + "," + (located.blockZ() >> 4));
        }
        System.exit(0);
    }
}
