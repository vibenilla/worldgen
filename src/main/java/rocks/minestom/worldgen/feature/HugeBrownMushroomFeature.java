package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.HugeMushroomFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

/** Port of vanilla's {@code HugeBrownMushroomFeature}. */
public final class HugeBrownMushroomFeature extends AbstractHugeMushroomFeature {
    @Override
    protected <T extends Block.Getter & Block.Setter> void makeCap(
            T level,
            RandomSource random,
            BlockVec origin,
            int treeHeight,
            HugeMushroomFeatureConfiguration config
    ) {
        var radius = config.foliageRadius();

        for (var dx = -radius; dx <= radius; dx++) {
            for (var dz = -radius; dz <= radius; dz++) {
                var minX = dx == -radius;
                var maxX = dx == radius;
                var minZ = dz == -radius;
                var maxZ = dz == radius;
                var xEdge = minX || maxX;
                var zEdge = minZ || maxZ;
                if (xEdge && zEdge) {
                    continue;
                }

                var position = origin.add(dx, treeHeight, dz);
                var west = minX || zEdge && dx == 1 - radius;
                var east = maxX || zEdge && dx == radius - 1;
                var north = minZ || xEdge && dz == 1 - radius;
                var south = maxZ || xEdge && dz == radius - 1;

                var state = config.capProvider().getState(level, random, origin);
                if (state.getProperty("west") != null && state.getProperty("east") != null
                        && state.getProperty("north") != null && state.getProperty("south") != null) {
                    state = state.withProperty("west", Boolean.toString(west))
                            .withProperty("east", Boolean.toString(east))
                            .withProperty("north", Boolean.toString(north))
                            .withProperty("south", Boolean.toString(south));
                }

                this.placeMushroomBlock(level, position, state);
            }
        }
    }

    @Override
    protected int getTreeRadiusForHeight(int trunkHeight, int treeHeight, int leafRadius, int yo) {
        return yo <= 3 ? 0 : leafRadius;
    }
}
