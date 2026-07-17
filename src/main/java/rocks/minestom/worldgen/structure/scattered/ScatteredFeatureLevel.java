package rocks.minestom.worldgen.structure.scattered;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.structure.StructureWrites;

/**
 * Chunk-local world view for scattered feature (desert pyramid, jungle
 * temple, swamp hut, buried treasure) piece placement, mirroring the reads
 * vanilla pieces perform against the proto-chunk: real terrain, the
 * structure's own writes so far, and a live motion-blocking-no-leaves /
 * ocean-floor heightmap. All piece reads and writes are clamped to the
 * generating chunk, so a single chunk column buffer is enough.
 */
final class ScatteredFeatureLevel {
    private final GenerationUnitAdapter adapter;
    private final Block[] blocks;
    private final int startX;
    private final int startZ;
    private final int sizeX;
    private final int sizeZ;
    private final int minY;
    private final int maxY;
    private final int height;
    private final int[] chunkHandle;

    ScatteredFeatureLevel(GenerationUnitAdapter adapter, Block[] blocks, int startX, int startZ,
            int minY, int maxY, int[] chunkHandle) {
        this(adapter, blocks, startX, startZ, 16, 16, minY, maxY, chunkHandle);
    }

    /**
     * Widened-footprint constructor used by tests to observe a piece's full
     * block output in one pass instead of the 16x16 per-chunk slices
     * production placement processes it in.
     */
    ScatteredFeatureLevel(GenerationUnitAdapter adapter, Block[] blocks, int startX, int startZ,
            int sizeX, int sizeZ, int minY, int maxY, int[] chunkHandle) {
        this.adapter = adapter;
        this.blocks = blocks;
        this.startX = startX;
        this.startZ = startZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.minY = minY;
        this.maxY = maxY;
        this.height = maxY - minY + 1;
        this.chunkHandle = chunkHandle;
    }

    Block getBlock(int x, int y, int z) {
        var index = this.index(x, y, z);
        if (index < 0) {
            return Block.AIR;
        }
        var block = this.blocks[index];
        return block != null ? block : Block.AIR;
    }

    void setBlock(int x, int y, int z, Block block) {
        var index = this.index(x, y, z);
        if (index < 0) {
            return;
        }
        this.blocks[index] = block;
        this.adapter.setBlock(x, y, z, block);
        StructureWrites.record(this.chunkHandle, x, y, z, block);
    }

    /**
     * Vanilla {@code MOTION_BLOCKING_NO_LEAVES} heightmap: one above the
     * highest motion-blocking, non-leaf block, live over the structure's
     * writes so far.
     */
    int motionBlockingNoLeavesHeight(int x, int z) {
        var localX = x - this.startX;
        var localZ = z - this.startZ;
        if (localX < 0 || localX >= this.sizeX || localZ < 0 || localZ >= this.sizeZ) {
            return this.minY;
        }
        var base = (localX * this.sizeZ + localZ) * this.height;
        for (var yIndex = this.height - 1; yIndex >= 0; yIndex--) {
            var block = this.blocks[base + yIndex];
            if (block != null && block.isSolid() && !isLeaves(block)) {
                return this.minY + yIndex + 1;
            }
        }
        return this.minY;
    }

    /**
     * Vanilla {@code OCEAN_FLOOR_WG} heightmap: one above the highest solid,
     * non-liquid block.
     */
    int oceanFloorHeight(int x, int z) {
        var localX = x - this.startX;
        var localZ = z - this.startZ;
        if (localX < 0 || localX >= this.sizeX || localZ < 0 || localZ >= this.sizeZ) {
            return this.minY;
        }
        var base = (localX * this.sizeZ + localZ) * this.height;
        for (var yIndex = this.height - 1; yIndex >= 0; yIndex--) {
            var block = this.blocks[base + yIndex];
            if (block != null && block.isSolid()) {
                return this.minY + yIndex + 1;
            }
        }
        return this.minY;
    }

    int minY() {
        return this.minY;
    }

    int maxY() {
        return this.maxY;
    }

    private static boolean isLeaves(Block block) {
        return block.key().value().endsWith("_leaves");
    }

    private int index(int x, int y, int z) {
        var localX = x - this.startX;
        var localZ = z - this.startZ;
        var yIndex = y - this.minY;
        if (localX < 0 || localX >= this.sizeX || localZ < 0 || localZ >= this.sizeZ || yIndex < 0 || yIndex >= this.height) {
            return -1;
        }
        return (localX * this.sizeZ + localZ) * this.height + yIndex;
    }
}
