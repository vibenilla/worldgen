package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;

public final class EndPodiumFeature {
    private static final int PODIUM_RADIUS = 4;
    private static final float INNER_RADIUS = 2.5F;
    private static final float OUTER_RADIUS = 3.5F;

    private EndPodiumFeature() {
    }

    public static <T extends Block.Getter & Block.Setter> void place(T level, BlockVec origin, boolean active) {
        var originX = origin.blockX();
        var originY = origin.blockY();
        var originZ = origin.blockZ();
        var innerRadiusSquared = INNER_RADIUS * INNER_RADIUS;
        var outerRadiusSquared = OUTER_RADIUS * OUTER_RADIUS;

        for (var x = originX - PODIUM_RADIUS; x <= originX + PODIUM_RADIUS; x++) {
            for (var z = originZ - PODIUM_RADIUS; z <= originZ + PODIUM_RADIUS; z++) {
                for (var y = originY - 1; y <= originY + 32; y++) {
                    var dx = (float) (x - originX);
                    var dy = (float) (y - originY);
                    var dz = (float) (z - originZ);
                    var distanceSquared = dx * dx + dy * dy + dz * dz;
                    var isInner = distanceSquared < innerRadiusSquared;
                    var isOuter = distanceSquared < outerRadiusSquared;
                    if (!isOuter) {
                        continue;
                    }

                    var pos = new BlockVec(x, y, z);
                    if (y < originY) {
                        level.setBlock(pos, isInner ? Block.BEDROCK : Block.END_STONE);
                    } else if (y > originY) {
                        level.setBlock(pos, Block.AIR);
                    } else if (!isInner) {
                        level.setBlock(pos, Block.BEDROCK);
                    } else if (active) {
                        level.setBlock(pos, Block.END_PORTAL);
                    } else {
                        level.setBlock(pos, Block.AIR);
                    }
                }
            }
        }

        for (var index = 0; index < 4; index++) {
            level.setBlock(origin.add(0, index, 0), Block.BEDROCK);
        }
    }
}
