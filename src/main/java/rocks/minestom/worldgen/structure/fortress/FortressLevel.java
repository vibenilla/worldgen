package rocks.minestom.worldgen.structure.fortress;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.structure.StructureWrites;

import java.util.List;

/**
 * Chunk-local world view for nether fortress piece placement, mirroring the
 * reads and writes vanilla pieces perform against the proto-chunk. All piece
 * reads and writes are clamped to the generating chunk, so a single chunk
 * column buffer is enough.
 *
 * <p>Writes go through the plain {@link Block.Setter} interface (rather than
 * a concrete {@code GenerationUnitAdapter}) so tests can drive placement
 * against a lightweight fake without a real generation unit.
 */
final class FortressLevel {
    private final Block.Setter adapter;
    private final Block[] blocks;
    private final int startX;
    private final int startZ;
    private final int minY;
    private final int maxY;
    private final int height;
    private final int[] chunkHandle;
    private final List<BlockVec> shapeUpdatePositions;

    FortressLevel(Block.Setter adapter, Block[] blocks, int startX, int startZ,
            int minY, int maxY, int[] chunkHandle, List<BlockVec> shapeUpdatePositions) {
        this.adapter = adapter;
        this.blocks = blocks;
        this.startX = startX;
        this.startZ = startZ;
        this.minY = minY;
        this.maxY = maxY;
        this.height = maxY - minY + 1;
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
        if (this.shapeUpdatePositions != null && isShapeCheckBlock(block)) {
            this.shapeUpdatePositions.add(new BlockVec(x, y, z));
        }
    }

    /**
     * Vanilla {@code StructurePiece.SHAPE_CHECK_BLOCKS}, restricted to the
     * families fortress pieces actually place (fences): a position marked
     * here has its connection shape recomputed against its final neighbors
     * once every piece in the chunk has been placed.
     */
    private static boolean isShapeCheckBlock(Block block) {
        var key = block.key().value();
        return key.endsWith("_fence") && !key.endsWith("_fence_gate");
    }

    int minY() {
        return this.minY;
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
