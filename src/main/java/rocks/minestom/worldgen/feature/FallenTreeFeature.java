package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.FallenTreeConfiguration;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Port of vanilla's {@code FallenTreeFeature}: a one-block stump plus a
 * horizontal log a couple blocks away, both run through their decorators.
 */
public final class FallenTreeFeature implements Feature<FallenTreeConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<FallenTreeConfiguration, T> context) {
        this.placeFallenTree(context.config(), context.origin(), context.accessor(), context.random());
        return true;
    }

    private <T extends Block.Getter & Block.Setter> void placeFallenTree(
            FallenTreeConfiguration config,
            BlockVec origin,
            T level,
            RandomSource random
    ) {
        this.placeStump(config, level, random, origin);

        var direction = Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
        var logLength = config.logLength().sample(random) - 2;
        var logStartPos = relative(origin, direction, 2 + random.nextInt(2));
        logStartPos = this.setGroundHeightForFallenLogStartPos(level, logStartPos);

        if (this.canPlaceEntireFallenLog(level, logLength, logStartPos, direction)) {
            this.placeFallenLog(config, level, random, logLength, logStartPos, direction);
        }
    }

    private BlockVec setGroundHeightForFallenLogStartPos(Block.Getter level, BlockVec logStartPos) {
        var cursor = logStartPos.add(0, 1, 0);
        for (var step = 0; step < 6; step++) {
            if (this.mayPlaceOn(level, cursor)) {
                return cursor;
            }
            cursor = cursor.add(0, -1, 0);
        }
        return cursor;
    }

    private <T extends Block.Getter & Block.Setter> void placeStump(
            FallenTreeConfiguration config,
            T level,
            RandomSource random,
            BlockVec stumpPos
    ) {
        this.placeLogBlock(config, level, random, stumpPos, null);
        this.decorateLogs(level, random, Set.of(VanillaPos.of(stumpPos)), config.stumpDecorators());
    }

    private boolean canPlaceEntireFallenLog(Block.Getter level, int logLength, BlockVec logStartPos, Direction direction) {
        var gapInGround = 0;
        var cursor = logStartPos;

        for (var step = 0; step < logLength; step++) {
            if (!Feature.isValidTreePosition(level, cursor)) {
                return false;
            }

            if (!this.isOverSolidGround(level, cursor)) {
                if (++gapInGround > 2) {
                    return false;
                }
            } else {
                gapInGround = 0;
            }

            cursor = direction.relative(cursor);
        }

        return true;
    }

    private <T extends Block.Getter & Block.Setter> void placeFallenLog(
            FallenTreeConfiguration config,
            T level,
            RandomSource random,
            int logLength,
            BlockVec logStartPos,
            Direction direction
    ) {
        var fallenLog = new HashSet<VanillaPos>();
        var cursor = logStartPos;

        for (var step = 0; step < logLength; step++) {
            this.placeLogBlock(config, level, random, cursor, direction);
            fallenLog.add(VanillaPos.of(cursor));
            cursor = direction.relative(cursor);
        }

        this.decorateLogs(level, random, fallenLog, config.logDecorators());
    }

    private boolean mayPlaceOn(Block.Getter level, BlockVec position) {
        return Feature.isValidTreePosition(level, position) && this.isOverSolidGround(level, position);
    }

    private boolean isOverSolidGround(Block.Getter level, BlockVec position) {
        // Vanilla checks isFaceSturdy(UP); leaves have no sturdy faces
        var below = level.getBlock(position.add(0, -1, 0));
        return below.solid() && !below.name().endsWith("_leaves");
    }

    private <T extends Block.Getter & Block.Setter> void placeLogBlock(
            FallenTreeConfiguration config,
            T level,
            RandomSource random,
            BlockVec position,
            Direction sideways
    ) {
        var state = config.trunkProvider().getState(level, random, position);
        if (sideways != null) {
            var axis = sideways.stepX() != 0 ? "x" : "z";
            state = state.withProperty("axis", axis);
        }
        level.setBlock(position, state);
    }

    private <T extends Block.Getter & Block.Setter> void decorateLogs(
            T level,
            RandomSource random,
            Set<VanillaPos> logs,
            List<TreeDecorator> decorators
    ) {
        if (decorators.isEmpty()) {
            return;
        }

        var logList = logs.stream().map(VanillaPos::toBlockVec).toList();
        var decoratorContext = new TreeDecorator.Context(
                level,
                level::setBlock,
                random,
                logList,
                List.of(),
                List.of());
        for (var decorator : decorators) {
            decorator.place(decoratorContext);
        }
    }

    private static BlockVec relative(BlockVec position, Direction direction, int steps) {
        return position.add(direction.stepX() * steps, direction.stepY() * steps, direction.stepZ() * steps);
    }
}
