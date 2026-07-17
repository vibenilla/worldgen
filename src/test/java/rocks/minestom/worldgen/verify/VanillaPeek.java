package rocks.minestom.worldgen.verify;

import java.nio.file.Path;

/** Prints vanilla blocks in a small box. Usage: worldDir minX minY minZ maxX maxY maxZ [filter] */
public final class VanillaPeek {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        int minX = Integer.parseInt(args[1]), minY = Integer.parseInt(args[2]), minZ = Integer.parseInt(args[3]);
        int maxX = Integer.parseInt(args[4]), maxY = Integer.parseInt(args[5]), maxZ = Integer.parseInt(args[6]);
        var filter = args.length > 7 ? args[7] : null;
        for (var x = minX; x <= maxX; x++) {
            for (var y = minY; y <= maxY; y++) {
                for (var z = minZ; z <= maxZ; z++) {
                    var region = new RegionFile(worldDir.resolve("region")
                            .resolve("r." + Math.floorDiv(x >> 4, 32) + "." + Math.floorDiv(z >> 4, 32) + ".mca"));
                    var chunk = VanillaChunk.parse(region.readChunk(x >> 4, z >> 4));
                    var block = chunk.block(x & 15, y, z & 15);
                    if (filter == null || (block != null && block.contains(filter))) {
                        System.out.println(x + "," + y + "," + z + " " + block);
                    }
                }
            }
        }
    }
}
