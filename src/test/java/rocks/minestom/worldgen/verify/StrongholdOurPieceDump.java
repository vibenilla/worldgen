package rocks.minestom.worldgen.verify;

import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.structure.stronghold.StrongholdPieces;

import java.nio.file.Path;

/** Dumps our engine's stronghold piece layout (class name + BB) for the given start chunk. */
public final class StrongholdOurPieceDump {
    public static void main(String[] args) throws Exception {
        var datapackDir = Path.of(args[0]);
        var seed = Long.parseLong(args[1]);
        var chunkX = Integer.parseInt(args[2]);
        var chunkZ = Integer.parseInt(args[3]);

        var generators = new WorldGenerators(datapackDir, seed);
        var settings = generators.overworldSettings();
        var pieces = StrongholdPieces.generatePieces(seed, chunkX, chunkZ, settings.seaLevel(), settings.minY());
        System.out.println("count=" + pieces.size());
        var index = 0;
        for (var piece : pieces) {
            System.out.println(index + " " + piece.getClass().getSimpleName()
                    + " BB=" + piece.boundingBox() + " GD=" + piece.genDepth());
            index++;
        }
        System.exit(0);
    }
}
