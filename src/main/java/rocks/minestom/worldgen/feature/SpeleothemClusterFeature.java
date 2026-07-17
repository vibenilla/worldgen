package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SpeleothemClusterConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.ClampedNormalFloatProvider;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.OptionalInt;

/**
 * Port of vanilla's {@code SpeleothemClusterFeature} (the generic version of
 * the old dripstone cluster feature). Random call order matches vanilla
 * exactly.
 */
public final class SpeleothemClusterFeature implements Feature<SpeleothemClusterConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SpeleothemClusterConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var config = context.config();
        var random = context.random();
        if (!SpeleothemUtils.isEmptyOrWater(level, origin)) {
            return false;
        }

        var height = config.height().sample(random);
        var wetness = config.wetness().sample(random);
        var density = config.density().sample(random);
        var xRadius = config.radius().sample(random);
        var zRadius = config.radius().sample(random);

        for (var dx = -xRadius; dx <= xRadius; dx++) {
            for (var dz = -zRadius; dz <= zRadius; dz++) {
                var chanceOfStalagmiteOrStalactite = this.getChanceOfStalagmiteOrStalactite(xRadius, zRadius, dx, dz, config);
                var pos = origin.add(dx, 0, dz);
                this.placeColumn(level, random, pos, dx, dz, wetness, chanceOfStalagmiteOrStalactite, height, density, config);
            }
        }

        return true;
    }

    private <T extends Block.Getter & Block.Setter> void placeColumn(
            T level,
            RandomSource random,
            BlockVec pos,
            int dx,
            int dz,
            float chanceOfWater,
            double chanceOfStalagmiteOrStalactite,
            int clusterHeight,
            float density,
            SpeleothemClusterConfiguration config
    ) {
        var baseColumn = Column.scan(
                level, pos, config.floorToCeilingSearchRange(),
                SpeleothemUtils::isEmptyOrWater, SpeleothemUtils::isNeitherEmptyNorWater);
        if (baseColumn.isEmpty()) {
            return;
        }

        var ceiling = baseColumn.get().ceiling();
        var baseFloor = baseColumn.get().floor();
        if (ceiling.isEmpty() && baseFloor.isEmpty()) {
            return;
        }

        var wantPool = random.nextFloat() < chanceOfWater;
        Column column;
        if (wantPool && baseFloor.isPresent() && this.canPlacePool(level, atY(pos, baseFloor.getAsInt()), config)) {
            var baseFloorY = baseFloor.getAsInt();
            column = baseColumn.get().withFloor(OptionalInt.of(baseFloorY - 1));
            level.setBlock(atY(pos, baseFloorY), Block.WATER);
        } else {
            column = baseColumn.get();
        }

        var floor = column.floor();
        var wantStalactite = random.nextDouble() < chanceOfStalagmiteOrStalactite;
        int stalactiteHeight;
        if (ceiling.isPresent() && wantStalactite && !this.isLava(level, atY(pos, ceiling.getAsInt()))) {
            var ceilingThickness = config.speleothemBlockLayerThickness().sample(random);
            this.replaceBlocksWithBaseBlocks(level, atY(pos, ceiling.getAsInt()), ceilingThickness, Direction.UP, config);
            int maxHeightForThisColumn;
            if (floor.isPresent()) {
                maxHeightForThisColumn = Math.min(clusterHeight, ceiling.getAsInt() - floor.getAsInt());
            } else {
                maxHeightForThisColumn = clusterHeight;
            }

            stalactiteHeight = this.getSpeleothemHeight(random, dx, dz, density, maxHeightForThisColumn, config);
        } else {
            stalactiteHeight = 0;
        }

        var wantStalagmite = random.nextDouble() < chanceOfStalagmiteOrStalactite;
        int stalagmiteHeight;
        if (floor.isPresent() && wantStalagmite && !this.isLava(level, atY(pos, floor.getAsInt()))) {
            var floorThickness = config.speleothemBlockLayerThickness().sample(random);
            this.replaceBlocksWithBaseBlocks(level, atY(pos, floor.getAsInt()), floorThickness, Direction.DOWN, config);
            if (ceiling.isPresent()) {
                stalagmiteHeight = Math.max(
                        0,
                        stalactiteHeight + randomBetweenInclusive(random,
                                -config.maxStalagmiteStalactiteHeightDiff(), config.maxStalagmiteStalactiteHeightDiff()));
            } else {
                stalagmiteHeight = this.getSpeleothemHeight(random, dx, dz, density, clusterHeight, config);
            }
        } else {
            stalagmiteHeight = 0;
        }

        int actualStalagmiteHeight;
        int actualStalactiteHeight;
        if (ceiling.isPresent() && floor.isPresent() && ceiling.getAsInt() - stalactiteHeight <= floor.getAsInt() + stalagmiteHeight) {
            var floorY = floor.getAsInt();
            var ceilingY = ceiling.getAsInt();
            var lowestStalactiteBottom = Math.max(ceilingY - stalactiteHeight, floorY + 1);
            var highestStalagmiteTop = Math.min(floorY + stalagmiteHeight, ceilingY - 1);
            var actualStalactiteBottom = randomBetweenInclusive(random, lowestStalactiteBottom, highestStalagmiteTop + 1);
            var actualStalagmiteTop = actualStalactiteBottom - 1;
            actualStalactiteHeight = ceilingY - actualStalactiteBottom;
            actualStalagmiteHeight = actualStalagmiteTop - floorY;
        } else {
            actualStalactiteHeight = stalactiteHeight;
            actualStalagmiteHeight = stalagmiteHeight;
        }

        var mergeTips = random.nextBoolean()
                && actualStalactiteHeight > 0
                && actualStalagmiteHeight > 0
                && column.height().isPresent()
                && actualStalactiteHeight + actualStalagmiteHeight == column.height().getAsInt();
        if (ceiling.isPresent()) {
            SpeleothemUtils.growSpeleothem(
                    level,
                    atY(pos, ceiling.getAsInt() - 1),
                    Direction.DOWN,
                    actualStalactiteHeight,
                    mergeTips,
                    config.baseBlock(),
                    config.pointedBlock(),
                    config.replaceableBlocks());
        }

        if (floor.isPresent()) {
            SpeleothemUtils.growSpeleothem(
                    level,
                    atY(pos, floor.getAsInt() + 1),
                    Direction.UP,
                    actualStalagmiteHeight,
                    mergeTips,
                    config.baseBlock(),
                    config.pointedBlock(),
                    config.replaceableBlocks());
        }
    }

    private boolean isLava(Block.Getter level, BlockVec position) {
        return level.getBlock(position).compare(Block.LAVA);
    }

    private int getSpeleothemHeight(
            RandomSource random, int dx, int dz, float density, int maxHeight, SpeleothemClusterConfiguration config
    ) {
        if (random.nextFloat() > density) {
            return 0;
        }

        var distanceFromCenter = Math.abs(dx) + Math.abs(dz);
        var heightMean = (float) clampedMap(
                distanceFromCenter, 0.0D, config.maxDistanceFromCenterAffectingHeightBias(), maxHeight / 2.0D, 0.0D);
        return (int) randomBetweenBiased(random, 0.0F, maxHeight, heightMean, config.heightDeviation());
    }

    private <T extends Block.Getter & Block.Setter> boolean canPlacePool(T level, BlockVec pos, SpeleothemClusterConfiguration config) {
        var state = level.getBlock(pos);
        if (state.compare(Block.WATER) || state.compare(config.baseBlock()) || state.compare(config.pointedBlock())) {
            return false;
        }

        if (SpeleothemUtils.isWater(level.getBlock(pos.add(0, 1, 0)))) {
            return false;
        }

        for (var direction : Direction.HORIZONTAL) {
            if (!this.canBeAdjacentToWater(level, direction.relative(pos), config)) {
                return false;
            }
        }

        return this.canBeAdjacentToWater(level, pos.sub(0, 1, 0), config);
    }

    private boolean canBeAdjacentToWater(Block.Getter level, BlockVec pos, SpeleothemClusterConfiguration config) {
        var state = level.getBlock(pos);
        return config.baseStoneBlocks().contains(state.key()) || SpeleothemUtils.isWater(state);
    }

    private <T extends Block.Getter & Block.Setter> void replaceBlocksWithBaseBlocks(
            T level, BlockVec firstPos, int maxCount, Direction direction, SpeleothemClusterConfiguration config
    ) {
        var pos = firstPos;

        for (var index = 0; index < maxCount; index++) {
            if (!SpeleothemUtils.placeBaseBlockIfPossible(level, pos, config.baseBlock(), config.replaceableBlocks())) {
                return;
            }

            pos = direction.relative(pos);
        }
    }

    private double getChanceOfStalagmiteOrStalactite(
            int xRadius, int zRadius, int dx, int dz, SpeleothemClusterConfiguration config
    ) {
        var xDistanceFromEdge = xRadius - Math.abs(dx);
        var zDistanceFromEdge = zRadius - Math.abs(dz);
        var distanceFromEdge = Math.min(xDistanceFromEdge, zDistanceFromEdge);
        return clampedMap(
                (float) distanceFromEdge,
                0.0F,
                config.maxDistanceFromEdgeAffectingChanceOfSpeleothem(),
                config.chanceOfSpeleothemAtMaxDistanceFromCenter(),
                1.0F);
    }

    private static float randomBetweenBiased(RandomSource random, float min, float maxExclusive, float mean, float deviation) {
        return ClampedNormalFloatProvider.sample(random, mean, deviation, min, maxExclusive);
    }

    private static BlockVec atY(BlockVec pos, int y) {
        return new BlockVec(pos.blockX(), y, pos.blockZ());
    }

    private static int randomBetweenInclusive(RandomSource random, int min, int maxInclusive) {
        return random.nextInt(maxInclusive - min + 1) + min;
    }

    private static double clampedMap(double value, double inMin, double inMax, double outMin, double outMax) {
        var delta = (value - inMin) / (inMax - inMin);
        var clamped = Math.max(0.0D, Math.min(1.0D, delta));
        return outMin + clamped * (outMax - outMin);
    }

    private static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        var delta = (value - inMin) / (inMax - inMin);
        var clamped = Math.max(0.0F, Math.min(1.0F, delta));
        return outMin + clamped * (outMax - outMin);
    }
}
