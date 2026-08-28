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
         * {@code previousBlock} is whatever the target position held before
         * this write, so callers can tell a replace-style write (ore blobs
         * overwriting stone with a different rock type) from a placement-style
         * write (foliage appearing over air).
         */
        default boolean writePending(int chunkX, int chunkZ, int bufferIndex, Block block, Block previousBlock) {
            return false;
        }

        /**
         * Whether the chunk's decoration has completed. Implementations that
         * cannot tell return null, and light emulation falls back to the
         * scanline-order approximation.
         */
        default Boolean decorated(int chunkX, int chunkZ) {
            return null;
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

    /**
     * Whether the position's chunk generates later in the scanline ladder
     * than the decorated chunk. Such a chunk has no light data yet, and
     * vanilla's sky light storage returns full brightness (15) everywhere in
     * it - the decorated chunk itself and already-decorated chunks read 0
     * during generation (verified with an instrumented MushroomBlock
     * canSurvive trace at r24).
     */
    public boolean fullBrightAtGeneration(int x, int z) {
        if (!this.hasSkylight()) {
            return false;
        }
        var chunkX = x >> 4;
        var chunkZ = z >> 4;
        var centerX = this.terrainStartX >> 4;
        var centerZ = this.terrainStartZ >> 4;
        // The vanilla light engine reads 0 for any chunk it knows about (the
        // chunk has noise sections: everything within ring 1 of a decorated
        // or currently-decorating chunk, since FEATURES requires CARVERS on
        // its ring) and walks up to full bright (15) only for columns with no
        // sections at all (instrumented SHROOM trace: an east ring-1 neighbor
        // reads 0 during decoration, not 15).
        if (Math.max(Math.abs(chunkX - centerX), Math.abs(chunkZ - centerZ)) <= 1) {
            return false;
        }
        if (this.terrainLookup != null) {
            var anyDecoratedNear = false;
            var trackingSupported = true;
            neighborhood:
            for (var offsetX = -1; offsetX <= 1; offsetX++) {
                for (var offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    var decorated = this.terrainLookup.decorated(chunkX + offsetX, chunkZ + offsetZ);
                    if (decorated == null) {
                        trackingSupported = false;
                        break neighborhood;
                    }
                    if (decorated) {
                        anyDecoratedNear = true;
                        break neighborhood;
                    }
                }
            }
            if (trackingSupported) {
                return !anyDecoratedNear;
            }
        }
        return chunkX > centerX || (chunkX == centerX && chunkZ > centerZ);
    }

    /**
     * Whether this dimension has sky light. Of the vanilla dimensions only
     * the overworld does (and only the overworld's build height starts at
     * -64), so the world floor stands in for the dimension type here.
     */
    public boolean hasSkylight() {
        return this.terrainMinY == -64;
    }

    /**
     * Vanilla {@code ChunkAccess.markPosForPostProcessing}: queues the
     * position for the FULL-promotion post-process pass (fluid tick plus
     * neighbour-shape update, see {@code WaterSpread.postProcessMarked}).
     */
    public void markPostProcess(BlockVec position) {
        rocks.minestom.worldgen.structure.StructureWrites.markPostProcess(this.terrainLookup, position);
    }

    public void setBlock(BlockVec position, Block block) {
        var writeTrace = System.getProperty("worldgen.writeTrace");
        if (writeTrace != null && writeTrace.equals(position.blockX() + "," + position.blockY() + "," + position.blockZ())) {
            System.out.println("WRITETRACE " + position + " " + block.name());
            Thread.dumpStack();
        }
        if (!this.isInBounds(position)) {
            return;
        }

        if (block.compare(Block.MAGMA_BLOCK) || block.compare(Block.SOUL_SAND)) {
            rocks.minestom.worldgen.structure.StructureWrites.markPostProcess(
                    this.terrainLookup, position.add(0, 1, 0));
        }
        // Vanilla registers hasPostProcess on the mushroom blocks themselves:
        // every placed mushroom is re-checked at FULL, where the real light
        // level breaks sky-exposed ones (canSurvive light < 13)
        if (block.compare(Block.RED_MUSHROOM) || block.compare(Block.BROWN_MUSHROOM)) {
            rocks.minestom.worldgen.structure.StructureWrites.markPostProcess(this.terrainLookup, position);
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
                var previousBlock = neighbor.blocks()[neighborIndex];
                neighbor.blocks()[neighborIndex] = block;
                if (this.terrainLookup.writePending(neighborChunkX, neighborChunkZ, neighborIndex, block, previousBlock)) {
                    // Not blitted yet: the write arrives via the neighbor's own
                    // blit so its later decoration stays on top (vanilla order)
                    return;
                }
            }
        }

        var localX = position.blockX() - this.startX;
        var localY = position.blockY() - this.startY;
        var localZ = position.blockZ() - this.startZ;
        var writeTraceProperty = System.getProperty("worldgen.writeTrace");
        if (writeTraceProperty != null && writeTraceProperty.equals(position.blockX() + "," + position.blockY() + "," + position.blockZ())) {
            System.out.println("WRITETRACE-FORK local=" + localX + "," + localY + "," + localZ
                    + " unitStart=" + this.unit.absoluteStart() + " unitSize=" + this.unit.size());
        }
        this.unit.modifier().setRelative(localX, localY, localZ, block);
    }

    /**
     * Equivalent of vanilla's {@code OCEAN_FLOOR} heightmap lookup: the Y
     * coordinate one above the highest solid block, live over generated blocks.
     */
    public int getHeight(int x, int z) {
        return this.heightmap(HeightmapType.OCEAN_FLOOR, x, z);
    }

    /**
     * Vanilla only persists the FINAL heightmaps for pre-FEATURES chunks, so
     * a chunk that unloads after receiving writes lazily RE-PRIMES its WG
     * heightmaps from live blocks at the first read after reload. The only
     * chunks that unload mid-pregen are the startup spawn area (decorated
     * before the empty-server pause; nothing holds them once spawn
     * preparation ends) and the chunks their decorations spilled into (the
     * 3x3 halo). Ladder chunks never unload (forceloads are never removed),
     * so the frozen post-carver model stays correct everywhere else.
     *
     * <p>The startup set comes from the compare harness (pre-PAUSE DECO
     * events, {@code worldgen.startupChunks}). During the startup chunks' OWN
     * decoration the maps are still fresh (the unload happens afterward), so
     * repriming only applies once a non-startup chunk is being decorated.
     */
    private static java.util.Set<Long> startupChunks;
    private static java.util.Set<Long> reprimedChunks;

    private static void loadStartupChunks() {
        if (startupChunks != null) {
            return;
        }
        var startup = new java.util.HashSet<Long>();
        var halo = new java.util.HashSet<Long>();
        var property = System.getProperty("worldgen.startupChunks", "");
        if (!property.isEmpty()) {
            for (var entry : property.split(";")) {
                var parts = entry.split(",");
                if (parts.length != 2) {
                    continue;
                }
                var chunkX = Integer.parseInt(parts[0].trim());
                var chunkZ = Integer.parseInt(parts[1].trim());
                startup.add((long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL));
                for (var offsetX = -1; offsetX <= 1; offsetX++) {
                    for (var offsetZ = -1; offsetZ <= 1; offsetZ++) {
                        halo.add((long) (chunkX + offsetX) << 32 | ((chunkZ + offsetZ) & 0xFFFFFFFFL));
                    }
                }
            }
        }
        reprimedChunks = halo;
        startupChunks = startup;
    }

    private boolean isReprimed(int chunkX, int chunkZ) {
        loadStartupChunks();
        if (reprimedChunks.isEmpty()
                || !reprimedChunks.contains((long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL))) {
            return false;
        }
        // Still inside the startup phase: the decorated chunk is itself a
        // startup chunk, whose WG maps are fresh and frozen.
        return !startupChunks.contains(
                (long) (this.terrainStartX >> 4) << 32 | ((this.terrainStartZ >> 4) & 0xFFFFFFFFL));
    }

    /**
     * Per-chunk re-primed WG heightmap snapshots: vanilla's lazy re-prime
     * happens ONCE (the first {@code getHeight} after reload primes all 256
     * columns from live blocks) and the map is frozen again from then on, so
     * later writes into the chunk must not show up in later reads.
     */
    private static final java.util.Map<TerrainLookup, java.util.Map<Long, int[]>> REPRIMED_WORLD_SURFACE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<TerrainLookup, java.util.Map<Long, int[]>> REPRIMED_OCEAN_FLOOR =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private int reprimedHeight(java.util.Map<TerrainLookup, java.util.Map<Long, int[]>> cache,
            HeightmapType type, int x, int z) {
        var chunkX = x >> 4;
        var chunkZ = z >> 4;
        var perChunk = cache.computeIfAbsent(this.terrainLookup,
                unused -> java.util.Collections.synchronizedMap(new java.util.HashMap<>()));
        var heights = perChunk.computeIfAbsent((long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL), unused -> {
            var blocks = this.terrainLookup.terrain(chunkX, chunkZ).blocks();
            var snapshot = new int[256];
            for (var index = 0; index < 256; index++) {
                var columnBase = index * this.terrainHeight;
                var height = this.terrainMinY;
                for (var yIndex = this.terrainHeight - 1; yIndex >= 0; yIndex--) {
                    var block = blocks[columnBase + yIndex];
                    if (block != null && type.isOpaque(block)) {
                        height = this.terrainMinY + yIndex + 1;
                        break;
                    }
                }
                snapshot[index] = height;
            }
            return snapshot;
        });
        return heights[(x & 15) * 16 + (z & 15)];
    }

    /**
     * Vanilla's {@code OCEAN_FLOOR_WG} heightmap: one above the highest solid
     * block of the post-carver terrain. Vanilla stops updating the WG
     * heightmaps once a chunk passes the carvers stage
     * ({@code ChunkStatus.CARVERS} switches {@code heightmapsAfter} to the
     * final set), so structure and feature writes never show up in them -
     * except for re-primed chunks (see {@link #isReprimed}), which read the
     * live blocks.
     */
    public int frozenOceanFloor(int x, int z) {
        if (this.terrainLookup == null) {
            return Integer.MAX_VALUE;
        }
        if (this.isReprimed(x >> 4, z >> 4)) {
            return this.reprimedHeight(REPRIMED_OCEAN_FLOOR, HeightmapType.OCEAN_FLOOR, x, z);
        }
        var terrain = this.terrainLookup.terrain(x >> 4, z >> 4);
        var solidTop = terrain.surfaceHeights()[(x & 15) * 16 + (z & 15)];
        return solidTop == Integer.MIN_VALUE ? this.terrainMinY : solidTop + 1;
    }

    /**
     * Vanilla's {@code WORLD_SURFACE_WG} heightmap: one above the highest
     * non-air (solid or fluid) block of the post-carver terrain, frozen for
     * the same reason as {@link #frozenOceanFloor(int, int)}.
     */
    public int frozenWorldSurface(int x, int z) {
        if (this.terrainLookup == null) {
            return Integer.MAX_VALUE;
        }
        if (this.isReprimed(x >> 4, z >> 4)) {
            return this.reprimedHeight(REPRIMED_WORLD_SURFACE, HeightmapType.WORLD_SURFACE, x, z);
        }
        var terrain = this.terrainLookup.terrain(x >> 4, z >> 4);
        var index = (x & 15) * 16 + (z & 15);
        var solidTop = terrain.surfaceHeights()[index];
        var fluidTop = terrain.waterHeights()[index];
        var surface = Math.max(solidTop == Integer.MIN_VALUE ? Integer.MIN_VALUE : solidTop + 1, fluidTop);
        return surface == Integer.MIN_VALUE ? this.terrainMinY : surface;
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
                return !block.air();
            }
        },
        OCEAN_FLOOR {
            @Override
            boolean isOpaque(Block block) {
                return block.solid();
            }
        },
        MOTION_BLOCKING {
            @Override
            boolean isOpaque(Block block) {
                return block.solid() || isFluid(block);
            }
        },
        MOTION_BLOCKING_NO_LEAVES {
            @Override
            boolean isOpaque(Block block) {
                return (block.solid() && !block.key().value().endsWith("leaves")) || isFluid(block);
            }
        };

        abstract boolean isOpaque(Block block);

        private static boolean isFluid(Block block) {
            return block.compare(Block.LAVA) || WaterStates.hasWaterFluid(block);
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
