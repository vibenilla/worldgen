package rocks.minestom.worldgen.verify;

import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.nio.file.Path;
import java.util.HashMap;

/**
 * Scans a vanilla world save for end city structure starts within a chunk
 * range and prints their start chunk, BB and child piece list, without
 * running the (slow) block-level diff that StructureVerify performs.
 */
public final class EndCityScan {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var minChunkX = Integer.parseInt(args[1]);
        var minChunkZ = Integer.parseInt(args[2]);
        var maxChunkX = Integer.parseInt(args[3]);
        var maxChunkZ = Integer.parseInt(args[4]);
        var nameFilter = args.length > 5 ? args[5] : "end_city";
        var dumpChildrenFor = args.length > 6 ? args[6] : null;

        var regions = new HashMap<Long, RegionFile>();
        for (var chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (var chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                var regionKey = ((long) Math.floorDiv(chunkX, 32) << 32) | (Math.floorDiv(chunkZ, 32) & 0xffffffffL);
                var finalChunkX = chunkX;
                var finalChunkZ = chunkZ;
                var region = regions.computeIfAbsent(regionKey, unused -> {
                    try {
                        return new RegionFile(worldDir.resolve("region").resolve(
                                "r." + Math.floorDiv(finalChunkX, 32) + "." + Math.floorDiv(finalChunkZ, 32) + ".mca"));
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
                var starts = chunkTag.getCompound("structures").getCompound("starts");
                for (var entry : starts) {
                    if (!entry.getKey().contains(nameFilter)) {
                        continue;
                    }
                    var start = (CompoundBinaryTag) entry.getValue();
                    if ("INVALID".equals(start.getString("id", "INVALID"))) {
                        continue;
                    }
                    var bb = start.getIntArray("BB");
                    System.out.println(entry.getKey() + " start chunk=" + chunkX + "," + chunkZ
                            + " BB=" + java.util.Arrays.toString(bb)
                            + " children=" + start.getList("Children").size());

                    if (dumpChildrenFor != null
                            && dumpChildrenFor.equals(chunkX + "," + chunkZ)) {
                        var index = 0;
                        for (var childTag : start.getList("Children")) {
                            var child = (CompoundBinaryTag) childTag;
                            System.out.println("  [" + index + "] id=" + child.getString("id")
                                    + " O=" + child.getInt("O", -999)
                                    + " GD=" + child.getInt("GD", -999)
                                    + " BB=" + java.util.Arrays.toString(child.getIntArray("BB")));
                            index++;
                        }
                    }
                }
            }
        }
        System.out.println("done");
        System.exit(0);
    }
}
