package rocks.minestom.worldgen.verify;

import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Minimal Anvil region file reader for verification against vanilla worlds.
 */
public final class RegionFile {
    private final ByteBuffer buffer;

    public RegionFile(Path path) throws IOException {
        this.buffer = ByteBuffer.wrap(Files.readAllBytes(path));
    }

    /**
     * Reads the chunk NBT at the given absolute chunk coordinates, or null if absent.
     */
    public CompoundBinaryTag readChunk(int chunkX, int chunkZ) throws IOException {
        var index = (chunkX & 31) + (chunkZ & 31) * 32;
        var location = this.buffer.getInt(index * 4);
        var sectorOffset = location >>> 8;
        if (sectorOffset == 0) {
            return null;
        }

        var length = this.buffer.getInt(sectorOffset * 4096);
        var compression = this.buffer.get(sectorOffset * 4096 + 4);
        var data = new byte[length - 1];
        this.buffer.get(sectorOffset * 4096 + 5, data);

        var input = new ByteArrayInputStream(data);
        var stream = switch (compression) {
            case 1 -> new GZIPInputStream(input);
            case 2 -> new InflaterInputStream(input);
            case 3 -> input;
            default -> throw new IOException("Unknown compression: " + compression);
        };

        try (stream) {
            return BinaryTagIO.reader(64 * 1024 * 1024).read(stream);
        }
    }
}
