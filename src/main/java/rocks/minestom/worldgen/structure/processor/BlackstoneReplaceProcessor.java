package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;

import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla {@code BlackstoneReplaceProcessor}: a fixed cobblestone/stone
 * substitution map turning a ruined portal's masonry into its blackstone
 * equivalent (nether variant). Stair facing/half and slab type are carried
 * over; there is no randomness.
 */
public final class BlackstoneReplaceProcessor implements StructureProcessor {
    public static final BlackstoneReplaceProcessor INSTANCE = new BlackstoneReplaceProcessor();

    private final Map<Key, Block> replacements;

    private BlackstoneReplaceProcessor() {
        var map = new HashMap<Key, Block>();
        put(map, Block.COBBLESTONE, Block.BLACKSTONE);
        put(map, Block.MOSSY_COBBLESTONE, Block.BLACKSTONE);
        put(map, Block.STONE, Block.POLISHED_BLACKSTONE);
        put(map, Block.STONE_BRICKS, Block.POLISHED_BLACKSTONE_BRICKS);
        put(map, Block.MOSSY_STONE_BRICKS, Block.POLISHED_BLACKSTONE_BRICKS);
        put(map, Block.COBBLESTONE_STAIRS, Block.BLACKSTONE_STAIRS);
        put(map, Block.MOSSY_COBBLESTONE_STAIRS, Block.BLACKSTONE_STAIRS);
        put(map, Block.STONE_STAIRS, Block.POLISHED_BLACKSTONE_STAIRS);
        put(map, Block.STONE_BRICK_STAIRS, Block.POLISHED_BLACKSTONE_BRICK_STAIRS);
        put(map, Block.MOSSY_STONE_BRICK_STAIRS, Block.POLISHED_BLACKSTONE_BRICK_STAIRS);
        put(map, Block.COBBLESTONE_SLAB, Block.BLACKSTONE_SLAB);
        put(map, Block.MOSSY_COBBLESTONE_SLAB, Block.BLACKSTONE_SLAB);
        put(map, Block.SMOOTH_STONE_SLAB, Block.POLISHED_BLACKSTONE_SLAB);
        put(map, Block.STONE_SLAB, Block.POLISHED_BLACKSTONE_SLAB);
        put(map, Block.STONE_BRICK_SLAB, Block.POLISHED_BLACKSTONE_BRICK_SLAB);
        put(map, Block.MOSSY_STONE_BRICK_SLAB, Block.POLISHED_BLACKSTONE_BRICK_SLAB);
        put(map, Block.STONE_BRICK_WALL, Block.POLISHED_BLACKSTONE_BRICK_WALL);
        put(map, Block.MOSSY_STONE_BRICK_WALL, Block.POLISHED_BLACKSTONE_BRICK_WALL);
        put(map, Block.COBBLESTONE_WALL, Block.BLACKSTONE_WALL);
        put(map, Block.MOSSY_COBBLESTONE_WALL, Block.BLACKSTONE_WALL);
        put(map, Block.CHISELED_STONE_BRICKS, Block.CHISELED_POLISHED_BLACKSTONE);
        put(map, Block.CRACKED_STONE_BRICKS, Block.CRACKED_POLISHED_BLACKSTONE_BRICKS);
        put(map, Block.IRON_BARS, Block.IRON_CHAIN);
        this.replacements = Map.copyOf(map);
    }

    private static void put(Map<Key, Block> map, Block source, Block target) {
        map.put(source.key(), target);
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        var oldState = processedBlockInfo.state();
        var newBlock = this.replacements.get(oldState.key());
        if (newBlock == null) {
            return processedBlockInfo;
        }

        var newState = newBlock;
        var facing = oldState.getProperty("facing");
        if (facing != null && newState.getProperty("facing") != null) {
            newState = newState.withProperty("facing", facing);
        }
        var half = oldState.getProperty("half");
        if (half != null && newState.getProperty("half") != null) {
            newState = newState.withProperty("half", half);
        }
        var type = oldState.getProperty("type");
        if (type != null && newState.getProperty("type") != null) {
            newState = newState.withProperty("type", type);
        }

        return new StructureBlockInfo(processedBlockInfo.pos(), newState, processedBlockInfo.nbt());
    }
}
