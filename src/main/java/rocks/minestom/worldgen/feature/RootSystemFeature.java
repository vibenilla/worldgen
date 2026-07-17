package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import rocks.minestom.worldgen.feature.configurations.RootSystemConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla's {@code RootSystemFeature}: searches upward for a spot the
 * nested tree feature can occupy, places it together with a rooted-dirt
 * column beneath it, and finally sprinkles hanging roots around the origin.
 */
public final class RootSystemFeature implements Feature<RootSystemConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<RootSystemConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        if (!level.getBlock(origin).isAir()) {
            return false;
        }

        var random = context.random();
        var config = context.config();
        if (placeDirtAndTree(context, level, config, random, origin)) {
            placeRoots(level, config, random, origin);
        }

        return true;
    }

    private static <T extends Block.Getter & Block.Setter> boolean spaceForTree(T level, RootSystemConfiguration config, BlockVec pos) {
        for (var i = 1; i <= config.requiredVerticalSpaceForTree(); i++) {
            var columnPos = pos.add(0, i, 0);
            if (!isAllowedTreeSpace(level.getBlock(columnPos), i, config.allowedVerticalWaterForTree())) {
                return false;
            }
        }

        if (config.levelTestDistance() > 0) {
            for (var i = 0; i < 4; i++) {
                var direction = Direction.HORIZONTAL.get(i);
                var cornerPos = pos.add(
                        direction.stepX() * config.levelTestDistance(), 0, direction.stepZ() * config.levelTestDistance());
                var below = level.getBlock(cornerPos.add(0, -config.maxLevelDeviation(), 0));
                var above = level.getBlock(cornerPos.add(0, config.maxLevelDeviation(), 0));
                if (below.isAir() || !above.isAir()) {
                    return false;
                }
            }
        }

        return true;
    }

    private static boolean isAllowedTreeSpace(Block state, int blocksAboveOrigin, int allowedVerticalWaterHeight) {
        if (state.isAir()) {
            return true;
        }

        var blocksAboveGround = blocksAboveOrigin + 1;
        return blocksAboveGround <= allowedVerticalWaterHeight && state.compare(Block.WATER);
    }

    private static <T extends Block.Getter & Block.Setter> boolean placeDirtAndTree(
            FeaturePlaceContext<RootSystemConfiguration, T> context, T level, RootSystemConfiguration config,
            RandomSource random, BlockVec origin) {
        for (var y = 0; y < config.rootColumnMaxHeight(); y++) {
            var workingPos = origin.add(0, y + 1, 0);
            if (level instanceof LargeDripstoneFeature.WorldSurface surface
                    && surface.worldSurfaceHeight(workingPos.blockX(), workingPos.blockZ()) < workingPos.blockY()) {
                return false;
            }

            if (config.allowedTreePosition().test(level, workingPos) && spaceForTree(level, config, workingPos)) {
                var belowPos = workingPos.add(0, -1, 0);
                var belowBlock = level.getBlock(belowPos);
                if (belowBlock.compare(Block.LAVA) || !belowBlock.registry().isSolid()) {
                    return false;
                }

                FeaturePlaceContext<FeatureConfiguration, T> treeContext = new FeaturePlaceContext<>(
                        level, random, workingPos, null, context.worldSeed(), context.minY(), context.maxY(), context.seaLevel());
                if (RandomSelectorFeature.placePlacedFeature(treeContext, config.loader(), config.treeFeature())) {
                    placeDirt(origin, origin.blockY() + y, level, config, random);
                    return true;
                }
            }
        }

        return false;
    }

    private static <T extends Block.Getter & Block.Setter> void placeDirt(
            BlockVec origin, int targetHeight, T level, RootSystemConfiguration config, RandomSource random) {
        for (var y = origin.blockY(); y < targetHeight; y++) {
            placeRootedDirt(level, config, random, origin.blockX(), origin.blockZ(), y);
        }
    }

    private static <T extends Block.Getter & Block.Setter> void placeRootedDirt(
            T level, RootSystemConfiguration config, RandomSource random, int originX, int originZ, int y) {
        var rootRadius = config.rootRadius();
        for (var i = 0; i < config.rootPlacementAttempts(); i++) {
            var x = originX + random.nextInt(rootRadius) - random.nextInt(rootRadius);
            var z = originZ + random.nextInt(rootRadius) - random.nextInt(rootRadius);
            var candidate = new BlockVec(x, y, z);
            if (config.rootReplaceable().contains(level.getBlock(candidate).key())) {
                level.setBlock(x, y, z, config.rootStateProvider().getState(level, random, candidate));
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> void placeRoots(
            T level, RootSystemConfiguration config, RandomSource random, BlockVec origin) {
        var rootRadius = config.hangingRootRadius();
        var verticalSpan = config.hangingRootsVerticalSpan();

        for (var i = 0; i < config.hangingRootPlacementAttempts(); i++) {
            var candidate = origin.add(
                    random.nextInt(rootRadius) - random.nextInt(rootRadius),
                    random.nextInt(verticalSpan) - random.nextInt(verticalSpan),
                    random.nextInt(rootRadius) - random.nextInt(rootRadius));
            if (level.getBlock(candidate).isAir()) {
                var targetState = config.hangingRootStateProvider().getState(level, random, candidate);
                var abovePos = candidate.add(0, 1, 0);
                if (hasFullFace(level.getBlock(abovePos), BlockFace.BOTTOM)) {
                    level.setBlock(candidate.blockX(), candidate.blockY(), candidate.blockZ(), targetState);
                }
            }
        }
    }

    /** Vanilla's {@code BlockState.isFaceSturdy} (default {@code SupportType.FULL}), approximated with the collision shape. */
    private static boolean hasFullFace(Block block, BlockFace face) {
        return block.registry().collisionShape().isFaceFull(face);
    }
}
