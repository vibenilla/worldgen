package rocks.minestom.worldgen.verify;

import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.nio.file.Path;

/** Lists structure starts stored in a vanilla world save within a chunk radius. */
public final class StructureStartScan {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var radius = Integer.parseInt(args[1]);
        for (var chunkX = -radius; chunkX < radius; chunkX++) {
            for (var chunkZ = -radius; chunkZ < radius; chunkZ++) {
                var regionPath = worldDir.resolve("region")
                        .resolve("r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca");
                RegionFile region;
                try {
                    region = new RegionFile(regionPath);
                } catch (Exception exception) {
                    continue;
                }
                var chunkTag = region.readChunk(chunkX, chunkZ);
                if (chunkTag == null) {
                    continue;
                }
                var starts = chunkTag.getCompound("structures").getCompound("starts");
                for (var entry : starts) {
                    var start = (CompoundBinaryTag) entry.getValue();
                    if (!"INVALID".equals(start.getString("id", "INVALID"))) {
                        System.out.println(entry.getKey() + " at chunk " + chunkX + "," + chunkZ
                                + " children=" + start.getList("Children").size());
                    }
                }
            }
        }
        System.exit(0);
    }
}
