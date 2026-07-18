package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.StructureRng;

import java.util.Locale;

/**
 * Vanilla {@code BlockAgeProcessor}: weathers stone bricks, stairs, slabs,
 * walls and obsidian into cracked, mossy and crying variants. A fresh legacy
 * random is seeded from the block's world position hash (vanilla's
 * {@code settings.getRandom(pos)}), and every draw is consumed in the same
 * order as vanilla so the shared per-position random stays in sync.
 */
public final class BlockAgeProcessor implements StructureProcessor {
    private static final Key STAIRS_TAG = Key.key("minecraft:stairs");
    private static final Key SLABS_TAG = Key.key("minecraft:slabs");
    private static final Key WALLS_TAG = Key.key("minecraft:walls");
    private static final Block[] NON_MOSSY_REPLACEMENTS = {Block.STONE_SLAB, Block.STONE_BRICK_SLAB};

    private final float mossiness;

    public BlockAgeProcessor(float mossiness) {
        this.mossiness = mossiness;
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        var position = processedBlockInfo.pos();
        var random = new LegacyRandomSource(StructureRng.getSeed(
                position.blockX(), position.blockY(), position.blockZ()));
        var state = processedBlockInfo.state();
        var blockTags = context.blockTags();

        Block newState = null;
        if (state.compare(Block.STONE_BRICKS) || state.compare(Block.STONE) || state.compare(Block.CHISELED_STONE_BRICKS)) {
            newState = this.maybeReplaceFullStoneBlock(random);
        } else if (blockTags.blocks(STAIRS_TAG).contains(state.key())) {
            newState = this.maybeReplaceStairs(state, random);
        } else if (blockTags.blocks(SLABS_TAG).contains(state.key())) {
            newState = this.maybeReplaceSlab(state, random);
        } else if (blockTags.blocks(WALLS_TAG).contains(state.key())) {
            newState = this.maybeReplaceWall(state, random);
        } else if (state.compare(Block.OBSIDIAN)) {
            newState = this.maybeReplaceObsidian(random);
        }

        return newState != null
                ? new StructureBlockInfo(position, newState, processedBlockInfo.nbt())
                : processedBlockInfo;
    }

    private Block maybeReplaceFullStoneBlock(RandomSource random) {
        if (random.nextFloat() >= 0.5F) {
            return null;
        }
        Block[] nonMossyReplacements = {
                Block.CRACKED_STONE_BRICKS, getRandomFacingStairs(random, Block.STONE_BRICK_STAIRS)};
        Block[] mossyReplacements = {
                Block.MOSSY_STONE_BRICKS, getRandomFacingStairs(random, Block.MOSSY_STONE_BRICK_STAIRS)};
        return this.getRandomBlock(random, nonMossyReplacements, mossyReplacements);
    }

    private Block maybeReplaceStairs(Block state, RandomSource random) {
        if (random.nextFloat() >= 0.5F) {
            return null;
        }
        Block[] mossyReplacements = {
                copyProperties(Block.MOSSY_STONE_BRICK_STAIRS, state), Block.MOSSY_STONE_BRICK_SLAB};
        return this.getRandomBlock(random, NON_MOSSY_REPLACEMENTS, mossyReplacements);
    }

    private Block maybeReplaceSlab(Block state, RandomSource random) {
        return random.nextFloat() < this.mossiness ? copyProperties(Block.MOSSY_STONE_BRICK_SLAB, state) : null;
    }

    private Block maybeReplaceWall(Block state, RandomSource random) {
        return random.nextFloat() < this.mossiness ? copyProperties(Block.MOSSY_STONE_BRICK_WALL, state) : null;
    }

    private Block maybeReplaceObsidian(RandomSource random) {
        return random.nextFloat() < 0.15F ? Block.CRYING_OBSIDIAN : null;
    }

    private Block getRandomBlock(RandomSource random, Block[] nonMossyBlocks, Block[] mossyBlocks) {
        return random.nextFloat() < this.mossiness ? getRandomBlock(random, mossyBlocks) : getRandomBlock(random, nonMossyBlocks);
    }

    private static Block getRandomBlock(RandomSource random, Block[] blocks) {
        return blocks[random.nextInt(blocks.length)];
    }

    private static Block getRandomFacingStairs(RandomSource random, Block stairBlock) {
        var facing = Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
        var half = random.nextInt(2) == 0 ? "top" : "bottom";
        return stairBlock
                .withProperty("facing", facing.name().toLowerCase(Locale.ROOT))
                .withProperty("half", half);
    }

    /** Vanilla {@code BlockState.withPropertiesOf}: carry over every property shared with the source state. */
    private static Block copyProperties(Block target, Block source) {
        var result = target;
        for (var entry : source.properties().entrySet()) {
            if (target.getProperty(entry.getKey()) != null) {
                result = result.withProperty(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
