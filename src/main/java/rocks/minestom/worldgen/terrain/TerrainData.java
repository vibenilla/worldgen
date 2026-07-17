package rocks.minestom.worldgen.terrain;

import java.util.Arrays;

/**
 * Holds the raw data arrays produced by the base terrain generation.
 * These are passed to the Surface System to decide where to place grass, sand,
 * etc.
 *
 * @param surfaceHeights Map of (x, z) to the Y coordinate of the highest solid
 *                       block ({@link #SOLID} or {@link #SOLID_OTHER}), or
 *                       {@link Integer#MIN_VALUE} if none.
 * @param waterHeights   Map of (x, z) to Y+1 of the first fluid block from the
 *                       top, or {@link Integer#MIN_VALUE} if none.
 * @param stoneMask      A flattened 3D per-block state mask using the
 *                       {@link #AIR}, {@link #SOLID}, {@link #FLUID} and
 *                       {@link #SOLID_OTHER} constants.
 */
public record TerrainData(
        int[] surfaceHeights,
        int[] waterHeights,
        byte[] stoneMask,
        net.minestom.server.instance.block.Block[] blocks) {
    /**
     * Nothing was placed at this position.
     */
    public static final byte AIR = 0;
    /**
     * The default block was placed (including aquifer barrier stone).
     */
    public static final byte SOLID = 1;
    /**
     * A fluid was placed (water or lava, whether from sea level or an aquifer).
     */
    public static final byte FLUID = 2;
    /**
     * A solid block that is not the default block was placed (e.g. ore-vein blocks).
     */
    public static final byte SOLID_OTHER = 3;

    public static TerrainData create(int sizeX, int sizeZ, int height) {
        var surfaceHeights = new int[sizeX * sizeZ];
        var waterHeights = new int[sizeX * sizeZ];
        var stoneMask = new byte[sizeX * sizeZ * height];
        var blocks = new net.minestom.server.instance.block.Block[sizeX * sizeZ * height];
        Arrays.fill(surfaceHeights, Integer.MIN_VALUE);
        Arrays.fill(waterHeights, Integer.MIN_VALUE);
        return new TerrainData(surfaceHeights, waterHeights, stoneMask, blocks);
    }
}
