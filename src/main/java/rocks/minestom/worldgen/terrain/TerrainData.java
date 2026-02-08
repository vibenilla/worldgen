package rocks.minestom.worldgen.terrain;

import java.util.Arrays;

/**
 * Holds the raw data arrays produced by the base terrain generation.
 * These are passed to the Surface System to decide where to place grass, sand,
 * etc.
 *
 * @param surfaceHeights Map of (x, z) to the Y coordinate of the highest solid
 *                       block.
 * @param waterHeights   Map of (x, z) to the Y coordinate of the highest water
 *                       block.
 * @param stoneMask      A flattened 3D bitmask array. 1 means "base
 *                       stone/ground", 0 means air/water.
 */
public record TerrainData(
        int[] surfaceHeights,
        int[] waterHeights,
        byte[] stoneMask) {
    public static TerrainData create(int sizeX, int sizeZ, int height) {
        var surfaceHeights = new int[sizeX * sizeZ];
        var waterHeights = new int[sizeX * sizeZ];
        var stoneMask = new byte[sizeX * sizeZ * height];
        Arrays.fill(surfaceHeights, Integer.MIN_VALUE);
        Arrays.fill(waterHeights, Integer.MIN_VALUE);
        return new TerrainData(surfaceHeights, waterHeights, stoneMask);
    }
}
