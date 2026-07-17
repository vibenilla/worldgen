package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.MultifaceGrowthConfiguration;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla's {@code MultifaceGrowthFeature} (glow lichen): grows a
 * multiface block against a valid support face near the origin, optionally
 * spreading one extra face via the multiface spreader.
 */
public final class MultifaceGrowthFeature implements Feature<MultifaceGrowthConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<MultifaceGrowthConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var random = context.random();
        var config = context.config();

        var originState = level.getBlock(origin);
        if (!isAirOrWater(originState)) {
            return false;
        }

        var directions = config.shuffledDirections(random);
        if (placeGrowthIfPossible(level, origin, originState, config, random, directions)) {
            return true;
        }

        for (var searchDirection : directions) {
            var placementDirections = config.shuffledDirectionsExcept(random, searchDirection.opposite());

            for (var step = 0; step < config.searchRange(); step++) {
                // Vanilla quirk: the cursor is re-set from the origin every
                // iteration, so the search never advances past one block
                var position = searchDirection.relative(origin);
                var state = level.getBlock(position);
                if (!isAirOrWater(state) && !state.compare(config.placeBlock())) {
                    break;
                }

                if (placeGrowthIfPossible(level, position, state, config, random, placementDirections)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static <T extends Block.Getter & Block.Setter> boolean placeGrowthIfPossible(
            T level,
            BlockVec position,
            Block currentState,
            MultifaceGrowthConfiguration config,
            RandomSource random,
            List<Direction> placementDirections
    ) {
        for (var direction : placementDirections) {
            var support = level.getBlock(direction.relative(position));
            if (!config.canBePlacedOn().contains(support.name())) {
                continue;
            }

            var newState = getStateForPlacement(currentState, level, position, direction, config);
            if (newState == null) {
                return false;
            }

            level.setBlock(position, newState);
            if (random.nextFloat() < config.chanceOfSpreading()) {
                spreadFromFaceTowardRandomDirection(level, position, direction, random, config);
            }

            return true;
        }

        return false;
    }

    /**
     * Vanilla {@code MultifaceBlock.getStateForPlacement}: null when the face
     * is already grown or has no full support face behind it.
     */
    private static Block getStateForPlacement(
            Block currentState,
            Block.Getter level,
            BlockVec position,
            Direction face,
            MultifaceGrowthConfiguration config
    ) {
        Block base;
        if (currentState.compare(config.placeBlock())) {
            if (hasFace(currentState, face)) {
                return null;
            }
            base = currentState;
        } else if (currentState.compare(Block.WATER)) {
            base = waterlogged(config.placeBlock());
        } else {
            base = config.placeBlock();
        }

        if (!canAttachTo(level, face.relative(position))) {
            return null;
        }

        return base.withProperty(face.serializedName(), "true");
    }

    /** Vanilla spreads toward the first workable of all six shuffled directions. */
    private static <T extends Block.Getter & Block.Setter> void spreadFromFaceTowardRandomDirection(
            T level,
            BlockVec position,
            Direction face,
            RandomSource random,
            MultifaceGrowthConfiguration config
    ) {
        var directions = new ArrayList<>(List.of(Direction.values()));
        TreeDecorator.shuffle(directions, random);

        for (var direction : directions) {
            if (spreadFromFaceTowardDirection(level, position, face, direction, config)) {
                return;
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> boolean spreadFromFaceTowardDirection(
            T level,
            BlockVec position,
            Direction face,
            Direction direction,
            MultifaceGrowthConfiguration config
    ) {
        if (direction == face || direction == face.opposite()) {
            return false;
        }

        var state = level.getBlock(position);
        if (!hasFace(state, face) || hasFace(state, direction)) {
            return false;
        }

        // Vanilla spread order: same position, same plane, wrap around
        var candidates = new BlockVec[]{
                position,
                direction.relative(position),
                face.relative(direction.relative(position))
        };
        var faces = new Direction[]{direction, face, direction.opposite()};

        for (var index = 0; index < candidates.length; index++) {
            var spreadPos = candidates[index];
            var spreadFace = faces[index];
            if (canSpreadInto(level, spreadPos, spreadFace, config)) {
                var current = level.getBlock(spreadPos);
                var newState = getStateForPlacement(current, level, spreadPos, spreadFace, config);
                if (newState == null) {
                    return false;
                }
                level.setBlock(spreadPos, newState);
                return true;
            }
        }

        return false;
    }

    private static boolean canSpreadInto(Block.Getter level, BlockVec position, Direction face, MultifaceGrowthConfiguration config) {
        var state = level.getBlock(position);
        if (!state.isAir() && !state.compare(config.placeBlock()) && !state.compare(Block.WATER)) {
            return false;
        }

        // isValidStateForPlacement: face not yet grown, support face behind
        if (state.compare(config.placeBlock()) && hasFace(state, face)) {
            return false;
        }

        return canAttachTo(level, face.relative(position));
    }

    private static boolean hasFace(Block state, Direction face) {
        return "true".equals(state.getProperty(face.serializedName()));
    }

    /** Approximation of vanilla's full-face support check. */
    private static boolean canAttachTo(Block.Getter level, BlockVec position) {
        var block = level.getBlock(position);
        return block.registry().isSolid() && !block.name().endsWith("_leaves");
    }

    private static Block waterlogged(Block block) {
        return block.getProperty("waterlogged") != null ? block.withProperty("waterlogged", "true") : block;
    }

    private static boolean isAirOrWater(Block state) {
        return state.isAir() || state.compare(Block.WATER);
    }
}
