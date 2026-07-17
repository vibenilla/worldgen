package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.terrain.TerrainData;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GenerationUnitAdapter implements Block.Getter, Block.Setter {
    private final GenerationUnit unit;
    private final int startX;
    private final int startY;
    private final int startZ;
    private final int terrainStartX;
    private final int terrainStartZ;
    private final int terrainSizeX;
    private final int terrainSizeZ;
    private final int terrainMinY;
    private final Block[] terrainBlocks;
    private final int terrainHeight;
    private final TerrainLookup terrainLookup;
    private final Map<BlockVec, Block> blockCache;

    /**
     * Supplies the memoized terrain of any chunk so features can read real
     * neighbor blocks and heights, like vanilla's WorldGenLevel region.
     */
    public interface TerrainLookup {
        TerrainData terrain(int chunkX, int chunkZ);

        /**
         * Queues a spill-over write for a chunk that has not been blitted yet,
         * preserving vanilla's chronological write order (the target chunk's
         * own decoration must land on top). Returns false when the chunk is
         * already generated and the write must go through the fork instead.
         */
        default boolean writePending(int chunkX, int chunkZ, int bufferIndex, Block block) {
            return false;
        }
    }

    /**
     * The memoized terrain supplier backing this adapter, or null for bare
     * adapters. Exposed so structure placement can read the same live chunk
     * buffers that decoration reads.
     */
    public TerrainLookup terrainLookup() {
        return this.terrainLookup;
    }

    public GenerationUnitAdapter(GenerationUnit unit) {
        this(unit, 0, 0, 0, 0, 0, null, 0, null);
    }

    public GenerationUnitAdapter(
            GenerationUnit unit,
            int terrainStartX,
            int terrainStartZ,
            int terrainSizeX,
            int terrainSizeZ,
            int terrainMinY,
            Block[] terrainBlocks,
            int terrainHeight,
            TerrainLookup terrainLookup
    ) {
        this.unit = unit;
        this.startX = unit.absoluteStart().blockX();
        this.startY = unit.absoluteStart().blockY();
        this.startZ = unit.absoluteStart().blockZ();
        this.terrainStartX = terrainStartX;
        this.terrainStartZ = terrainStartZ;
        this.terrainSizeX = terrainSizeX;
        this.terrainSizeZ = terrainSizeZ;
        this.terrainMinY = terrainMinY;
        this.terrainBlocks = terrainBlocks;
        this.terrainHeight = terrainHeight;
        this.terrainLookup = terrainLookup;
        this.blockCache = new ConcurrentHashMap<>();
    }

    @Override
    public Block getBlock(int x, int y, int z) {
        return this.getBlock(new BlockVec(x, y, z));
    }

    @Override
    public Block getBlock(int x, int y, int z, Block.Getter.Condition condition) {
        return this.getBlock(x, y, z);
    }

    public Block getBlock(BlockVec position) {
        // Real generated blocks for the center chunk: terrain + surface + earlier features
        var bufferIndex = this.bufferIndex(position);
        if (bufferIndex >= 0) {
            var block = this.terrainBlocks[bufferIndex];
            return block != null ? block : Block.AIR;
        }

        var cached = this.blockCache.get(position);
        if (cached != null) {
            return cached;
        }

        // Neighbor chunks: read their memoized terrain like vanilla reads the
        // neighboring proto-chunks
        var yIndex = position.blockY() - this.terrainMinY;
        if (this.terrainLookup != null && yIndex >= 0 && yIndex < this.terrainHeight) {
            var neighbor = this.terrainLookup.terrain(position.blockX() >> 4, position.blockZ() >> 4);
            var block = neighbor.blocks()[((position.blockX() & 15) * 16 + (position.blockZ() & 15)) * this.terrainHeight + yIndex];
            return block != null ? block : Block.AIR;
        }

        return Block.AIR;
    }

    @Override
    public void setBlock(int x, int y, int z, Block block) {
        this.setBlock(new BlockVec(x, y, z), block);
    }

    public void setBlock(BlockVec position, Block block) {
        if (!this.isInBounds(position)) {
            return;
        }

        var bufferIndex = this.bufferIndex(position);
        if (bufferIndex >= 0) {
            this.terrainBlocks[bufferIndex] = block;
        } else {
            this.blockCache.put(position, block);
            // Mirror into the neighbor's cached terrain so its later decoration
            // reads our writes, like vanilla proto-chunks do
            var yIndex = position.blockY() - this.terrainMinY;
            if (this.terrainLookup != null && yIndex >= 0 && yIndex < this.terrainHeight) {
                var neighborChunkX = position.blockX() >> 4;
                var neighborChunkZ = position.blockZ() >> 4;
                var neighborIndex = ((position.blockX() & 15) * 16 + (position.blockZ() & 15)) * this.terrainHeight + yIndex;
                var neighbor = this.terrainLookup.terrain(neighborChunkX, neighborChunkZ);
                neighbor.blocks()[neighborIndex] = block;
                if (this.terrainLookup.writePending(neighborChunkX, neighborChunkZ, neighborIndex, block)) {
                    // Not blitted yet: the write arrives via the neighbor's own
                    // blit so its later decoration stays on top (vanilla order)
                    return;
                }
            }
        }

        var localX = position.blockX() - this.startX;
        var localY = position.blockY() - this.startY;
        var localZ = position.blockZ() - this.startZ;
        this.unit.modifier().setRelative(localX, localY, localZ, block);
    }

    /**
     * Equivalent of vanilla's {@code OCEAN_FLOOR_WG} heightmap lookup: the Y
     * coordinate one above the highest solid block, live over generated blocks.
     */
    public int getHeight(int x, int z) {
        return this.heightmap(HeightmapType.OCEAN_FLOOR, x, z);
    }

    /**
     * Live heightmap over the generated blocks (terrain + surface + features
     * placed so far), mirroring vanilla's heightmaps that update as decoration
     * places blocks. Neighbor chunks resolve against their pre-feature terrain.
     */
    public int heightmap(HeightmapType type, int x, int z) {
        Block[] column;
        int columnBase;
        var localX = x - this.terrainStartX;
        var localZ = z - this.terrainStartZ;
        if (this.terrainBlocks != null && localX >= 0 && localX < this.terrainSizeX && localZ >= 0 && localZ < this.terrainSizeZ) {
            column = this.terrainBlocks;
            columnBase = (localX * this.terrainSizeZ + localZ) * this.terrainHeight;
        } else if (this.terrainLookup != null) {
            column = this.terrainLookup.terrain(x >> 4, z >> 4).blocks();
            columnBase = ((x & 15) * 16 + (z & 15)) * this.terrainHeight;
        } else {
            return Integer.MAX_VALUE;
        }

        for (var yIndex = this.terrainHeight - 1; yIndex >= 0; yIndex--) {
            var block = column[columnBase + yIndex];
            if (block != null && type.isOpaque(block)) {
                return this.terrainMinY + yIndex + 1;
            }
        }
        return this.terrainMinY;
    }

    public enum HeightmapType {
        WORLD_SURFACE {
            @Override
            boolean isOpaque(Block block) {
                return !block.isAir();
            }
        },
        OCEAN_FLOOR {
            @Override
            boolean isOpaque(Block block) {
                return block.isSolid();
            }
        },
        MOTION_BLOCKING {
            @Override
            boolean isOpaque(Block block) {
                return block.isSolid() || isFluid(block);
            }
        },
        MOTION_BLOCKING_NO_LEAVES {
            @Override
            boolean isOpaque(Block block) {
                return (block.isSolid() && !block.key().value().endsWith("leaves")) || isFluid(block);
            }
        };

        abstract boolean isOpaque(Block block);

        private static boolean isFluid(Block block) {
            return block.compare(Block.WATER) || block.compare(Block.LAVA)
                    || "true".equals(block.getProperty("waterlogged"));
        }
    }

    /**
     * Index into the center chunk's block buffer, or -1 when the position is
     * outside the buffered chunk column.
     */
    private int bufferIndex(BlockVec position) {
        if (this.terrainBlocks == null) {
            return -1;
        }

        var localX = position.blockX() - this.terrainStartX;
        var localZ = position.blockZ() - this.terrainStartZ;
        var yIndex = position.blockY() - this.terrainMinY;
        if (localX < 0 || localX >= this.terrainSizeX || localZ < 0 || localZ >= this.terrainSizeZ
                || yIndex < 0 || yIndex >= this.terrainHeight) {
            return -1;
        }
        return (localX * this.terrainSizeZ + localZ) * this.terrainHeight + yIndex;
    }

    private boolean isInBounds(BlockVec position) {
        var localX = position.blockX() - this.startX;
        var localY = position.blockY() - this.startY;
        var localZ = position.blockZ() - this.startZ;

        return localX >= 0 && localX < this.unit.size().blockX()
                && localY >= 0 && localY < this.unit.size().blockY()
                && localZ >= 0 && localZ < this.unit.size().blockZ();
    }
}
