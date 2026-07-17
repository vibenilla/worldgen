package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.HugeMushroomFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

/** Port of vanilla's {@code HugeRedMushroomFeature}. */
public final class HugeRedMushroomFeature extends AbstractHugeMushroomFeature {
    @Override
    protected <T extends Block.Getter & Block.Setter> void makeCap(
            T level,
            RandomSource random,
            BlockVec origin,
            int treeHeight,
            HugeMushroomFeatureConfiguration config
    ) {
        for (var dy = treeHeight - 3; dy <= treeHeight; dy++) {
            var radius = dy < treeHeight ? config.foliageRadius() : config.foliageRadius() - 1;
            var center = config.foliageRadius() - 2;

            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    var minX = dx == -radius;
                    var maxX = dx == radius;
                    var minZ = dz == -radius;
                    var maxZ = dz == radius;
                    var xEdge = minX || maxX;
                    var zEdge = minZ || maxZ;
                    if (dy < treeHeight && xEdge == zEdge) {
                        continue;
                    }

                    var position = origin.add(dx, dy, dz);
                    var state = config.capProvider().getState(level, random, origin);
                    if (state.getProperty("west") != null && state.getProperty("east") != null
                            && state.getProperty("north") != null && state.getProperty("south") != null
                            && state.getProperty("up") != null) {
                        state = state.withProperty("up", Boolean.toString(dy >= treeHeight - 1))
                                .withProperty("west", Boolean.toString(dx < -center))
                                .withProperty("east", Boolean.toString(dx > center))
                                .withProperty("north", Boolean.toString(dz < -center))
                                .withProperty("south", Boolean.toString(dz > center));
                    }

                    this.placeMushroomBlock(level, position, state);
                }
            }
        }
    }

    @Override
    protected int getTreeRadiusForHeight(int trunkHeight, int treeHeight, int leafRadius, int yo) {
        var radius = 0;
        if (yo < treeHeight && yo >= treeHeight - 3) {
            radius = leafRadius;
        } else if (yo == treeHeight) {
            radius = leafRadius;
        }
        return radius;
    }
}
