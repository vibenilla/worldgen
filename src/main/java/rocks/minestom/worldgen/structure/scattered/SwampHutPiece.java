package rocks.minestom.worldgen.structure.scattered;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

/**
 * Port of vanilla {@code SwampHutPiece}. Witch and cat spawning are out of
 * scope (block parity only); the piece still tracks the same spawn gate
 * booleans so re-entrant calls behave like vanilla.
 */
final class SwampHutPiece extends ScatteredFeaturePiece {
    static final int WIDTH = 7;
    static final int DEPTH = 9;

    SwampHutPiece(RandomSource random, int west, int north) {
        super(west, 64, north, WIDTH, 7, DEPTH, getRandomHorizontalDirection(random));
    }

    void postProcess(ScatteredFeatureLevel level, BoundingBox chunkBB) {
        if (!this.updateAverageGroundHeight(level, chunkBB, 0)) {
            return;
        }

        this.generateBox(level, chunkBB, 1, 1, 1, 5, 1, 7, Block.SPRUCE_PLANKS, Block.SPRUCE_PLANKS, false);
        this.generateBox(level, chunkBB, 1, 4, 2, 5, 4, 7, Block.SPRUCE_PLANKS, Block.SPRUCE_PLANKS, false);
        this.generateBox(level, chunkBB, 2, 1, 0, 4, 1, 0, Block.SPRUCE_PLANKS, Block.SPRUCE_PLANKS, false);
        this.generateBox(level, chunkBB, 2, 2, 2, 3, 3, 2, Block.SPRUCE_PLANKS, Block.SPRUCE_PLANKS, false);
        this.generateBox(level, chunkBB, 1, 2, 3, 1, 3, 6, Block.SPRUCE_PLANKS, Block.SPRUCE_PLANKS, false);
        this.generateBox(level, chunkBB, 5, 2, 3, 5, 3, 6, Block.SPRUCE_PLANKS, Block.SPRUCE_PLANKS, false);
        this.generateBox(level, chunkBB, 2, 2, 7, 4, 3, 7, Block.SPRUCE_PLANKS, Block.SPRUCE_PLANKS, false);
        this.generateBox(level, chunkBB, 1, 0, 2, 1, 3, 2, Block.OAK_LOG, Block.OAK_LOG, false);
        this.generateBox(level, chunkBB, 5, 0, 2, 5, 3, 2, Block.OAK_LOG, Block.OAK_LOG, false);
        this.generateBox(level, chunkBB, 1, 0, 7, 1, 3, 7, Block.OAK_LOG, Block.OAK_LOG, false);
        this.generateBox(level, chunkBB, 5, 0, 7, 5, 3, 7, Block.OAK_LOG, Block.OAK_LOG, false);
        this.placeBlock(level, Block.OAK_FENCE, 2, 3, 2, chunkBB);
        this.placeBlock(level, Block.OAK_FENCE, 3, 3, 7, chunkBB);
        this.placeBlock(level, Block.AIR, 1, 3, 4, chunkBB);
        this.placeBlock(level, Block.AIR, 5, 3, 4, chunkBB);
        this.placeBlock(level, Block.AIR, 5, 3, 5, chunkBB);
        this.placeBlock(level, Block.POTTED_RED_MUSHROOM, 1, 3, 5, chunkBB);
        this.placeBlock(level, Block.CRAFTING_TABLE, 3, 2, 6, chunkBB);
        this.placeBlock(level, Block.CAULDRON, 4, 2, 6, chunkBB);
        this.placeBlock(level, Block.OAK_FENCE, 1, 2, 1, chunkBB);
        this.placeBlock(level, Block.OAK_FENCE, 5, 2, 1, chunkBB);

        var northStairs = Block.SPRUCE_STAIRS.withProperty("facing", "north");
        var eastStairs = Block.SPRUCE_STAIRS.withProperty("facing", "east");
        var westStairs = Block.SPRUCE_STAIRS.withProperty("facing", "west");
        var southStairs = Block.SPRUCE_STAIRS.withProperty("facing", "south");
        this.generateBox(level, chunkBB, 0, 4, 1, 6, 4, 1, northStairs, northStairs, false);
        this.generateBox(level, chunkBB, 0, 4, 2, 0, 4, 7, eastStairs, eastStairs, false);
        this.generateBox(level, chunkBB, 6, 4, 2, 6, 4, 7, westStairs, westStairs, false);
        this.generateBox(level, chunkBB, 0, 4, 8, 6, 4, 8, southStairs, southStairs, false);
        this.placeBlock(level, northStairs.withProperty("shape", "outer_right"), 0, 4, 1, chunkBB);
        this.placeBlock(level, northStairs.withProperty("shape", "outer_left"), 6, 4, 1, chunkBB);
        this.placeBlock(level, southStairs.withProperty("shape", "outer_left"), 0, 4, 8, chunkBB);
        this.placeBlock(level, southStairs.withProperty("shape", "outer_right"), 6, 4, 8, chunkBB);

        for (var z = 2; z <= 7; z += 5) {
            for (var x = 1; x <= 5; x += 4) {
                this.fillColumnDown(level, Block.OAK_LOG, x, -1, z, chunkBB);
            }
        }
    }
}
