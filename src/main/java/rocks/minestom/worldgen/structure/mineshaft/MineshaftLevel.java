package rocks.minestom.worldgen.structure.mineshaft;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.structure.StructureWrites;

import java.util.List;
import java.util.Set;

/**
 * Chunk-local world view for mineshaft piece placement, mirroring the reads
 * vanilla pieces perform against the proto-chunk: real terrain (noise +
 * carvers), the structure's own writes so far, and a live ocean-floor
 * heightmap. All piece reads and writes are clamped to the generating chunk,
 * so a single chunk column buffer is enough.
 */
final class MineshaftLevel {
    private final GenerationUnitAdapter adapter;
    private final Block[] blocks;
    private final int startX;
    private final int startZ;
    private final int minY;
    private final int maxY;
    private final int height;
    private final BiomeZoomer biomeZoomer;
    private final Set<Key> blockingBiomes;
    private final int[] chunkHandle;
    private final List<BlockVec> shapeUpdatePositions;

    MineshaftLevel(GenerationUnitAdapter adapter, Block[] blocks, int startX, int startZ,
            int minY, int maxY, BiomeZoomer biomeZoomer, Set<Key> blockingBiomes, int[] chunkHandle,
            List<BlockVec> shapeUpdatePositions) {
        this.adapter = adapter;
        this.blocks = blocks;
        this.startX = startX;
        this.startZ = startZ;
        this.minY = minY;
        this.maxY = maxY;
        this.height = maxY - minY + 1;
        this.biomeZoomer = biomeZoomer;
        this.blockingBiomes = blockingBiomes;
        this.chunkHandle = chunkHandle;
        this.shapeUpdatePositions = shapeUpdatePositions;
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
        if (isShapeCheckBlock(block)) {
            this.shapeUpdatePositions.add(new BlockVec(x, y, z));
        }
    }

    /**
     * Vanilla {@code StructurePiece.SHAPE_CHECK_BLOCKS}, restricted to the
     * families mineshaft pieces actually place (fences): a position marked
     * here has its connection shape recomputed against its final neighbors
     * once every piece in the chunk has been placed.
     */
    private static boolean isShapeCheckBlock(Block block) {
        var key = block.key().value();
        return key.endsWith("_fence") && !key.endsWith("_fence_gate");
    }

    /**
     * Vanilla {@code OCEAN_FLOOR_WG} heightmap: frozen post-carver terrain.
     * The WG heightmaps never see structure writes (heightmapsAfter switches
     * to the live set at the carvers status), so a corridor digging cave_air
     * through a column must NOT lower this value - a live scan skipped
     * spider-corridor cobwebs whose isInterior test vanilla passed.
     */
    int oceanFloorHeight(int x, int z) {
        var frozen = this.adapter.frozenOceanFloor(x, z);
        if (frozen != Integer.MAX_VALUE) {
            return frozen;
        }
        var localX = x - this.startX;
        var localZ = z - this.startZ;
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
            return this.minY;
        }
        var base = (localX * 16 + localZ) * this.height;
        for (var yIndex = this.height - 1; yIndex >= 0; yIndex--) {
            var block = this.blocks[base + yIndex];
            if (block != null && block.isSolid()) {
                return this.minY + yIndex + 1;
            }
        }
        return this.minY;
    }

    boolean isMineshaftBlockingBiome(int x, int y, int z) {
        return this.blockingBiomes.contains(this.biomeZoomer.biome(x, y, z));
    }

    int minY() {
        return this.minY;
    }

    int maxY() {
        return this.maxY;
    }

    private int index(int x, int y, int z) {
        var localX = x - this.startX;
        var localZ = z - this.startZ;
        var yIndex = y - this.minY;
        if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16 || yIndex < 0 || yIndex >= this.height) {
            return -1;
        }
        return (localX * 16 + localZ) * this.height + yIndex;
    }
}
