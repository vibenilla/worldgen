package rocks.minestom.worldgen.carver;

import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.terrain.Aquifer;
import rocks.minestom.worldgen.terrain.TerrainData;

import java.util.BitSet;

/**
 * Per-target-chunk carving state: the terrain buffer being carved, the carving
 * aquifer (the same instance the noise fill used, mirroring vanilla's shared
 * {@code NoiseChunk} aquifer), the carving mask that prevents double-carving,
 * and the surface-rule hook used to restore grass/mycelium on exposed floors.
 */
public final class CarvingContext {
    private final TerrainData data;
    private final int chunkX;
    private final int chunkZ;
    private final int minGenY;
    private final int genDepth;
    private final Aquifer aquifer;
    private final BitSet mask;
    private final TopMaterial topMaterial;

    public CarvingContext(TerrainData data, int chunkX, int chunkZ, int minGenY, int genDepth,
            Aquifer aquifer, TopMaterial topMaterial) {
        this.data = data;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minGenY = minGenY;
        this.genDepth = genDepth;
        this.aquifer = aquifer;
        this.mask = new BitSet(16 * 16 * genDepth);
        this.topMaterial = topMaterial;
    }

    public int minGenY() {
        return this.minGenY;
    }

    public int genDepth() {
        return this.genDepth;
    }

    public int maxGenYInclusive() {
        return this.minGenY + this.genDepth - 1;
    }

    public int minBlockX() {
        return this.chunkX * 16;
    }

    public int minBlockZ() {
        return this.chunkZ * 16;
    }

    public int middleBlockX() {
        return this.chunkX * 16 + 8;
    }

    public int middleBlockZ() {
        return this.chunkZ * 16 + 8;
    }

    public int chunkX() {
        return this.chunkX;
    }

    public int chunkZ() {
        return this.chunkZ;
    }

    public Aquifer aquifer() {
        return this.aquifer;
    }

    public boolean maskGet(int localX, int blockY, int localZ) {
        return this.mask.get(this.maskIndex(localX, blockY, localZ));
    }

    public void maskSet(int localX, int blockY, int localZ) {
        this.mask.set(this.maskIndex(localX, blockY, localZ));
    }

    private int maskIndex(int localX, int blockY, int localZ) {
        return (localX * 16 + localZ) * this.genDepth + (blockY - this.minGenY);
    }

    public Block getBlock(int blockX, int blockY, int blockZ) {
        var block = this.data.blocks()[this.blockIndex(blockX, blockY, blockZ)];
        return block != null ? block : Block.AIR;
    }

    /**
     * Writes a carved state (air, water or lava) into the buffer and keeps the
     * stone mask in sync.
     */
    public void setCarved(int blockX, int blockY, int blockZ, Block state) {
        var index = this.blockIndex(blockX, blockY, blockZ);
        if (state.isAir()) {
            this.data.blocks()[index] = null;
            this.data.stoneMask()[index] = TerrainData.AIR;
        } else {
            this.data.blocks()[index] = state;
            this.data.stoneMask()[index] = TerrainData.FLUID;
        }
    }

    /**
     * Replaces a solid block (the dirt under a carved-away grass/mycelium block)
     * without changing the stone mask.
     */
    public void setSolid(int blockX, int blockY, int blockZ, Block block) {
        this.data.blocks()[this.blockIndex(blockX, blockY, blockZ)] = block;
    }

    @Nullable
    public Block topMaterial(int blockX, int blockY, int blockZ, boolean underFluid) {
        return this.topMaterial != null ? this.topMaterial.apply(blockX, blockY, blockZ, underFluid) : null;
    }

    private int blockIndex(int blockX, int blockY, int blockZ) {
        return ((blockX & 15) * 16 + (blockZ & 15)) * this.genDepth + (blockY - this.minGenY);
    }

    /**
     * Vanilla {@code CarvingContext.topMaterial}: evaluates the dimension's
     * surface rule at a single exposed-floor position.
     */
    public interface TopMaterial {
        @Nullable
        Block apply(int blockX, int blockY, int blockZ, boolean underFluid);
    }
}
