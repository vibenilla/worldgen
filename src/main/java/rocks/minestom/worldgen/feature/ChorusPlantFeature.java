package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public final class ChorusPlantFeature implements Feature<NoneFeatureConfiguration> {
    private static final int MAX_RADIUS = 8;
    private static final Block CHORUS_PLANT = Block.CHORUS_PLANT;
    private static final Block CHORUS_FLOWER_DEAD = Block.CHORUS_FLOWER.withProperty("age", "5");

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var random = context.random();
        var origin = context.origin();

        if (!isEmpty(level, origin)) {
            return false;
        }

        var below = origin.add(0, -1, 0);
        var belowBlock = level.getBlock(below);
        if (!belowBlock.compare(Block.END_STONE) && !belowBlock.isAir()) {
            return false;
        }

        setPlantBlock(level, origin);
        growTreeRecursive(level, random, origin, origin, MAX_RADIUS, 0);
        return true;
    }

    private static <T extends Block.Getter & Block.Setter> void growTreeRecursive(
            T level,
            RandomSource random,
            BlockVec current,
            BlockVec origin,
            int maxRadius,
            int depth
    ) {
        var height = random.nextInt(4) + 1;
        if (depth == 0) {
            height++;
        }

        for (var heightIndex = 0; heightIndex < height; heightIndex++) {
            var upPos = current.add(0, heightIndex + 1, 0);
            if (!allNeighborsEmpty(level, upPos, null)) {
                return;
            }

            setPlantBlock(level, upPos);
            setPlantBlock(level, upPos.add(0, -1, 0));
        }

        var branched = false;
        if (depth < 4) {
            var branchCount = random.nextInt(4);
            if (depth == 0) {
                branchCount++;
            }

            for (var branchIndex = 0; branchIndex < branchCount; branchIndex++) {
                var direction = HorizontalDirection.random(random);
                var branchPos = current.add(0, height, 0).add(direction.stepX, 0, direction.stepZ);
                if (Math.abs(branchPos.blockX() - origin.blockX()) >= maxRadius
                        || Math.abs(branchPos.blockZ() - origin.blockZ()) >= maxRadius) {
                    continue;
                }

                if (!isEmpty(level, branchPos) || !isEmpty(level, branchPos.add(0, -1, 0))) {
                    continue;
                }

                if (!allNeighborsEmpty(level, branchPos, direction.opposite())) {
                    continue;
                }

                branched = true;
                setPlantBlock(level, branchPos);
                var oppositePos = branchPos.add(direction.opposite().stepX, 0, direction.opposite().stepZ);
                setPlantBlock(level, oppositePos);
                growTreeRecursive(level, random, branchPos, origin, maxRadius, depth + 1);
            }
        }

        if (!branched) {
            var flowerPos = current.add(0, height, 0);
            level.setBlock(flowerPos, CHORUS_FLOWER_DEAD);
            setPlantBlock(level, flowerPos.add(0, -1, 0));
        }
    }

    private static boolean isEmpty(Block.Getter getter, BlockVec position) {
        return getter.getBlock(position).isAir();
    }

    private static boolean allNeighborsEmpty(Block.Getter getter, BlockVec position, HorizontalDirection skipDirection) {
        for (var direction : HorizontalDirection.VALUES) {
            if (skipDirection != null && direction == skipDirection) {
                continue;
            }

            var neighbor = position.add(direction.stepX, 0, direction.stepZ);
            if (!getter.getBlock(neighbor).isAir()) {
                return false;
            }
        }

        return true;
    }

    private static <T extends Block.Getter & Block.Setter> void setPlantBlock(T level, BlockVec position) {
        var state = getPlantStateWithConnections(level, position);
        level.setBlock(position, state);
    }

    private static Block getPlantStateWithConnections(Block.Getter getter, BlockVec position) {
        var below = getter.getBlock(position.add(0, -1, 0));
        var above = getter.getBlock(position.add(0, 1, 0));
        var north = getter.getBlock(position.add(0, 0, -1));
        var east = getter.getBlock(position.add(1, 0, 0));
        var south = getter.getBlock(position.add(0, 0, 1));
        var west = getter.getBlock(position.add(-1, 0, 0));

        var downConnected = isChorusPlantOrFlower(below) || below.compare(Block.END_STONE) || below.isAir();
        var upConnected = isChorusPlantOrFlower(above);
        var northConnected = isChorusPlantOrFlower(north);
        var eastConnected = isChorusPlantOrFlower(east);
        var southConnected = isChorusPlantOrFlower(south);
        var westConnected = isChorusPlantOrFlower(west);

        return CHORUS_PLANT
                .withProperty("down", Boolean.toString(downConnected))
                .withProperty("up", Boolean.toString(upConnected))
                .withProperty("north", Boolean.toString(northConnected))
                .withProperty("east", Boolean.toString(eastConnected))
                .withProperty("south", Boolean.toString(southConnected))
                .withProperty("west", Boolean.toString(westConnected));
    }

    private static boolean isChorusPlantOrFlower(Block block) {
        return block.compare(Block.CHORUS_PLANT) || block.compare(Block.CHORUS_FLOWER);
    }

    private record HorizontalDirection(int stepX, int stepZ) {
        private static final HorizontalDirection EAST = new HorizontalDirection(1, 0);
        private static final HorizontalDirection WEST = new HorizontalDirection(-1, 0);
        private static final HorizontalDirection SOUTH = new HorizontalDirection(0, 1);
        private static final HorizontalDirection NORTH = new HorizontalDirection(0, -1);
        private static final HorizontalDirection[] VALUES = new HorizontalDirection[]{
                EAST,
                WEST,
                SOUTH,
                NORTH
        };

        private static HorizontalDirection random(RandomSource random) {
            return VALUES[random.nextInt(VALUES.length)];
        }

        private HorizontalDirection opposite() {
            if (this == EAST) {
                return WEST;
            }
            if (this == WEST) {
                return EAST;
            }
            if (this == SOUTH) {
                return NORTH;
            }
            return SOUTH;
        }
    }
}
