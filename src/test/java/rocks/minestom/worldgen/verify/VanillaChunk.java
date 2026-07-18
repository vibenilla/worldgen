package rocks.minestom.worldgen.verify;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;

import java.util.HashMap;
import java.util.Map;

/**
 * A parsed vanilla chunk: canonical block state strings by position and biome keys by quart.
 */
public final class VanillaChunk {
    private final int minSectionY;
    private final int sectionCount;
    private final String[][] sectionBlocks;
    private final String[][] sectionBiomes;

    public static VanillaChunk parse(CompoundBinaryTag chunkTag) {
        var status = chunkTag.getString("Status");
        if (!status.endsWith("full")) {
            return null;
        }
        return new VanillaChunk(chunkTag);
    }

    /**
     * The dimension's vertical section range is read from the chunk data
     * itself (every section, even empty ones, has a {@code Y} entry) rather
     * than assumed, since the overworld's -64..319 range does not hold for
     * the nether (0..255) or the end (0..255).
     */
    private VanillaChunk(CompoundBinaryTag chunkTag) {
        var sections = chunkTag.getList("sections");
        var minY = Integer.MAX_VALUE;
        var maxY = Integer.MIN_VALUE;
        for (var sectionTag : sections) {
            var sectionY = ((CompoundBinaryTag) sectionTag).getByte("Y");
            minY = Math.min(minY, sectionY);
            maxY = Math.max(maxY, sectionY);
        }
        this.minSectionY = sections.size() == 0 ? 0 : minY;
        this.sectionCount = sections.size() == 0 ? 0 : maxY - minY + 1;
        this.sectionBlocks = new String[this.sectionCount][];
        this.sectionBiomes = new String[this.sectionCount][];

        for (var sectionTag : sections) {
            var section = (CompoundBinaryTag) sectionTag;
            var sectionY = section.getByte("Y");
            var sectionIndex = sectionY - this.minSectionY;
            if (sectionIndex < 0 || sectionIndex >= this.sectionCount) {
                continue;
            }

            var blockStates = section.getCompound("block_states");
            if (blockStates.size() > 0) {
                this.sectionBlocks[sectionIndex] = unpack(blockStates, 4096, 4, VanillaChunk::canonicalBlock);
            }

            var biomes = section.getCompound("biomes");
            if (biomes.size() > 0) {
                this.sectionBiomes[sectionIndex] = unpack(biomes, 64, 1, tag -> ((net.kyori.adventure.nbt.StringBinaryTag) tag).value());
            }
        }
    }

    /**
     * Returns the canonical block state string at local coordinates, or null if the section is absent.
     */
    public String block(int localX, int y, int localZ) {
        var sectionIndex = Math.floorDiv(y, 16) - this.minSectionY;
        if (sectionIndex < 0 || sectionIndex >= this.sectionCount || this.sectionBlocks[sectionIndex] == null) {
            return null;
        }
        var localY = Math.floorMod(y, 16);
        return this.sectionBlocks[sectionIndex][(localY << 8) | (localZ << 4) | localX];
    }

    /**
     * Returns the biome key at quart coordinates (localQuartX/Z in 0..3), or null if absent.
     */
    public String biome(int localQuartX, int quartY, int localQuartZ) {
        var sectionIndex = Math.floorDiv(quartY, 4) - this.minSectionY;
        if (sectionIndex < 0 || sectionIndex >= this.sectionCount || this.sectionBiomes[sectionIndex] == null) {
            return null;
        }
        var localY = Math.floorMod(quartY, 4);
        return this.sectionBiomes[sectionIndex][(localY << 4) | (localQuartZ << 2) | localQuartX];
    }

    private interface PaletteEntryParser {
        String parse(net.kyori.adventure.nbt.BinaryTag tag);
    }

    private static String[] unpack(CompoundBinaryTag container, int size, int minBits, PaletteEntryParser parser) {
        var paletteTag = container.getList("palette");
        var palette = new String[paletteTag.size()];
        for (var i = 0; i < palette.length; i++) {
            palette[i] = parser.parse(paletteTag.get(i));
        }

        var result = new String[size];
        if (palette.length == 1) {
            java.util.Arrays.fill(result, palette[0]);
            return result;
        }

        var bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        var data = container.getLongArray("data");
        var entriesPerLong = 64 / bits;
        var mask = (1L << bits) - 1;
        for (var i = 0; i < size; i++) {
            var packed = data[i / entriesPerLong];
            var value = (int) ((packed >>> (bits * (i % entriesPerLong))) & mask);
            result[i] = palette[value];
        }
        return result;
    }

    private static String canonicalBlock(net.kyori.adventure.nbt.BinaryTag tag) {
        var state = (CompoundBinaryTag) tag;
        var name = state.getString("Name");
        var propertiesTag = state.getCompound("Properties");
        if (propertiesTag.size() == 0) {
            return name;
        }

        Map<String, String> properties = new HashMap<>();
        for (var entry : propertiesTag) {
            properties.put(entry.getKey(), ((net.kyori.adventure.nbt.StringBinaryTag) entry.getValue()).value());
        }
        return canonical(name, properties);
    }

    /**
     * Canonical form shared with the Minestom side: name[key=value,...] with sorted keys.
     */
    public static String canonical(String name, Map<String, String> properties) {
        if (properties.isEmpty()) {
            return name;
        }
        var builder = new StringBuilder(name).append('[');
        properties.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> builder.append(entry.getKey()).append('=').append(entry.getValue()).append(','));
        builder.setLength(builder.length() - 1);
        return builder.append(']').toString();
    }
}
