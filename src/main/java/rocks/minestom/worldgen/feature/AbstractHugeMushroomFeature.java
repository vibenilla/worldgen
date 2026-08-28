package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.HugeMushroomFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.Set;

/** Port of vanilla's {@code AbstractHugeMushroomFeature}. */
public abstract class AbstractHugeMushroomFeature implements Feature<HugeMushroomFeatureConfiguration> {

    /** Vanilla's 26.2 {@code #minecraft:replaceable_by_mushrooms} block tag. */
    private static final Set<String> REPLACEABLE_BY_MUSHROOMS;

    static {
        var replaceable = new java.util.HashSet<>(Feature.REPLACEABLE_BY_TREES);
        replaceable.add("minecraft:brown_mushroom");
        replaceable.add("minecraft:red_mushroom");
        replaceable.add("minecraft:brown_mushroom_block");
        replaceable.add("minecraft:red_mushroom_block");
        REPLACEABLE_BY_MUSHROOMS = Set.copyOf(replaceable);
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<HugeMushroomFeatureConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var random = context.random();
        var config = context.config();

        var treeHeight = this.getTreeHeight(random);
        if (!this.isValidPosition(level, origin, treeHeight, config, context.minY(), context.maxY())) {
            return false;
        }

        this.makeCap(level, random, origin, treeHeight, config);
        this.placeTrunk(level, random, origin, config, treeHeight);
        return true;
    }

    protected <T extends Block.Getter & Block.Setter> void placeTrunk(
            T level,
            RandomSource random,
            BlockVec origin,
            HugeMushroomFeatureConfiguration config,
            int treeHeight
    ) {
        for (var dy = 0; dy < treeHeight; dy++) {
            var position = origin.add(0, dy, 0);
            this.placeMushroomBlock(level, position, config.stemProvider().getState(level, random, origin));
        }
    }

    protected <T extends Block.Getter & Block.Setter> void placeMushroomBlock(T level, BlockVec position, Block newState) {
        var current = level.getBlock(position);
        if (current.air() || REPLACEABLE_BY_MUSHROOMS.contains(current.name())) {
            level.setBlock(position, newState);
        }
    }

    protected int getTreeHeight(RandomSource random) {
        var treeHeight = random.nextInt(3) + 4;
        if (random.nextInt(12) == 0) {
            treeHeight *= 2;
        }
        return treeHeight;
    }

    protected boolean isValidPosition(
            Block.Getter level,
            BlockVec origin,
            int treeHeight,
            HugeMushroomFeatureConfiguration config,
            int minY,
            int maxY
    ) {
        var y = origin.blockY();
        if (y < minY + 1 || y + treeHeight + 1 > maxY + 1) {
            return false;
        }

        var predicateContext = Feature.predicateContext(level, minY, maxY);
        if (!config.canPlaceOn().test(predicateContext, origin.add(0, -1, 0))) {
            return false;
        }

        for (var dy = 0; dy <= treeHeight; dy++) {
            var radius = this.getTreeRadiusForHeight(-1, -1, config.foliageRadius(), dy);

            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    var state = level.getBlock(origin.blockX() + dx, y + dy, origin.blockZ() + dz);
                    if (!state.air() && !Feature.LEAVES.contains(state.name())) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    protected abstract int getTreeRadiusForHeight(int trunkHeight, int treeHeight, int leafRadius, int yo);

    protected abstract <T extends Block.Getter & Block.Setter> void makeCap(
            T level,
            RandomSource random,
            BlockVec origin,
            int treeHeight,
            HugeMushroomFeatureConfiguration config
    );
}
