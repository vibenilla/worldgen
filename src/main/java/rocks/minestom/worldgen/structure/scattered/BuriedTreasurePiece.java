package rocks.minestom.worldgen.structure.scattered;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

/**
 * Port of vanilla {@code BuriedTreasurePieces.BuriedTreasurePiece}: walks
 * down from the ocean-floor surface at the structure's anchor column until
 * it finds sandstone/stone/andesite/granite/diorite below, then carves a
 * one-block sand/soft pocket around that spot and drops a bare chest (loot
 * table NBT is out of scope, matching the rest of the port).
 */
final class BuriedTreasurePiece {
    private final int anchorX;
    private final int anchorZ;
    private BoundingBox boundingBox;

    BuriedTreasurePiece(int anchorX, int anchorZ) {
        this.anchorX = anchorX;
        this.anchorZ = anchorZ;
        this.boundingBox = new BoundingBox(anchorX, 90, anchorZ, anchorX, 90, anchorZ);
    }

    BoundingBox boundingBox() {
        return this.boundingBox;
    }

    void postProcess(ScatteredFeatureLevel level, RandomSource random) {
        var y = level.oceanFloorHeight(this.anchorX, this.anchorZ);
        var x = this.anchorX;
        var z = this.anchorZ;

        while (y > level.minY()) {
            var below = level.getBlock(x, y - 1, z);
            if (isSupportBlock(below)) {
                var current = level.getBlock(x, y, z);
                var softState = !current.air() && !isLiquid(current) ? current : Block.SAND;

                for (var direction : Direction.values()) {
                    var relativeX = x + direction.stepX();
                    var relativeY = y + direction.stepY();
                    var relativeZ = z + direction.stepZ();
                    var relative = level.getBlock(relativeX, relativeY, relativeZ);
                    if (relative.air() || isLiquid(relative)) {
                        var belowRelative = level.getBlock(relativeX, relativeY - 1, relativeZ);
                        if ((belowRelative.air() || isLiquid(belowRelative)) && direction != Direction.UP) {
                            level.setBlock(relativeX, relativeY, relativeZ, below);
                        } else {
                            level.setBlock(relativeX, relativeY, relativeZ, softState);
                        }
                    }
                }

                level.setBlock(x, y, z, ScatteredFeaturePiece.reorientChest(level, x, y, z));
                this.boundingBox = new BoundingBox(x, y, z, x, y, z);
                random.nextLong();
                return;
            }

            y--;
        }
    }

    private static boolean isSupportBlock(Block block) {
        return block.compare(Block.SANDSTONE) || block.compare(Block.STONE) || block.compare(Block.ANDESITE)
                || block.compare(Block.GRANITE) || block.compare(Block.DIORITE);
    }

    private static boolean isLiquid(Block block) {
        return block.compare(Block.WATER) || block.compare(Block.LAVA);
    }

    static BlockVec anchor(int chunkMinX, int chunkMinZ) {
        return new BlockVec(chunkMinX + 9, 90, chunkMinZ + 9);
    }
}
