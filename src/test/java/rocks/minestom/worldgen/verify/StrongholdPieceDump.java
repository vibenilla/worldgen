package rocks.minestom.worldgen.verify;

import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.nio.file.Path;
import java.util.Arrays;

/** Dumps the piece list (id + BB + genDepth) of a vanilla stronghold start. */
public final class StrongholdPieceDump {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var chunkX = Integer.parseInt(args[1]);
        var chunkZ = Integer.parseInt(args[2]);

        var region = new RegionFile(worldDir.resolve("region").resolve(
                "r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca"));
        var chunkTag = region.readChunk(chunkX, chunkZ);
        var starts = chunkTag.getCompound("structures").getCompound("starts");
        for (var entry : starts) {
            if (!entry.getKey().contains("stronghold")) {
                continue;
            }
            var start = (CompoundBinaryTag) entry.getValue();
            var children = start.getList("Children");
            System.out.println("children=" + children.size());
            var index = 0;
            for (var childTag : children) {
                var child = (CompoundBinaryTag) childTag;
                System.out.println(index + " id=" + child.getString("id")
                        + " BB=" + Arrays.toString(child.getIntArray("BB"))
                        + " GD=" + child.getInt("GD", -1)
                        + " O=" + child.getString("O", ""));
                index++;
            }
        }
        System.exit(0);
    }
}
