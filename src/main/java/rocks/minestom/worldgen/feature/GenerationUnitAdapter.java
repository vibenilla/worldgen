package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;

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
    private final int[] terrainSurfaceHeights;
    private final int[] terrainWaterHeights;
    private final Block terrainDefaultBlock;
    private final Block terrainDefaultFluid;
    private final Map<BlockVec, Block> blockCache;

    public GenerationUnitAdapter(GenerationUnit unit) {
        this(unit, 0, 0, 0, 0, 0, null, null, null, null);
    }

    public GenerationUnitAdapter(
            GenerationUnit unit,
            int terrainStartX,
            int terrainStartZ,
            int terrainSizeX,
            int terrainSizeZ,
            int terrainMinY,
            int[] terrainSurfaceHeights,
            int[] terrainWaterHeights,
            Block terrainDefaultBlock,
            Block terrainDefaultFluid
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
        this.terrainSurfaceHeights = terrainSurfaceHeights;
        this.terrainWaterHeights = terrainWaterHeights;
        this.terrainDefaultBlock = terrainDefaultBlock;
        this.terrainDefaultFluid = terrainDefaultFluid;
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
        var cached = this.blockCache.get(position);
        if (cached != null) {
            return cached;
        }

        if (!this.isInBounds(position)) {
            return Block.AIR;
        }

        var terrainBlock = this.getTerrainBlock(position);
        if (terrainBlock != null) {
            return terrainBlock;
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

        this.blockCache.put(position, block);
        var localX = position.blockX() - this.startX;
        var localY = position.blockY() - this.startY;
        var localZ = position.blockZ() - this.startZ;
        this.unit.modifier().setRelative(localX, localY, localZ, block);
    }

    private boolean isInBounds(BlockVec position) {
        var localX = position.blockX() - this.startX;
        var localY = position.blockY() - this.startY;
        var localZ = position.blockZ() - this.startZ;

        return localX >= 0 && localX < this.unit.size().blockX()
                && localY >= 0 && localY < this.unit.size().blockY()
                && localZ >= 0 && localZ < this.unit.size().blockZ();
    }

    private Block getTerrainBlock(BlockVec position) {
        if (this.terrainSurfaceHeights == null || this.terrainWaterHeights == null
                || this.terrainDefaultBlock == null || this.terrainDefaultFluid == null) {
            return null;
        }

        var localX = position.blockX() - this.terrainStartX;
        var localZ = position.blockZ() - this.terrainStartZ;
        if (localX < 0 || localX >= this.terrainSizeX || localZ < 0 || localZ >= this.terrainSizeZ) {
            return null;
        }

        var surfaceIndex = localX * this.terrainSizeZ + localZ;
        var surfaceY = this.terrainSurfaceHeights[surfaceIndex];
        var waterTopY = this.terrainWaterHeights[surfaceIndex];
        var blockY = position.blockY();

        if (surfaceY != Integer.MIN_VALUE && blockY <= surfaceY && blockY >= this.terrainMinY) {
            return this.terrainDefaultBlock;
        }

        if (waterTopY != Integer.MIN_VALUE && blockY < waterTopY
                && (surfaceY == Integer.MIN_VALUE || blockY > surfaceY)) {
            return this.terrainDefaultFluid;
        }

        return Block.AIR;
    }
}
