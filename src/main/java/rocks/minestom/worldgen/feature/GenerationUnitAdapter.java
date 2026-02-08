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
    private final Map<BlockVec, Block> blockCache;

    public GenerationUnitAdapter(GenerationUnit unit) {
        this.unit = unit;
        this.startX = unit.absoluteStart().blockX();
        this.startY = unit.absoluteStart().blockY();
        this.startZ = unit.absoluteStart().blockZ();
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
}
