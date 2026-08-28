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
        if (debugGate(origin)) {
            System.out.println("MFG origin=" + origin.blockX() + "," + origin.blockY() + "," + origin.blockZ()
                    + " state=" + originState.name() + " dirs=" + directions);
        }
        if (placeGrowthIfPossible(level, origin, originState, config, random, directions)) {
            return true;
        }

        for (var searchDirection : directions) {
            var placementDirections = config.shuffledDirectionsExcept(random, searchDirection.opposite());
            if (debugGate(origin)) {
                System.out.println("MFG search=" + searchDirection + " placeDirs=" + placementDirections);
            }

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
                if (debugGate(position)) {
                    System.out.println("MFG skip pos=" + position.blockX() + "," + position.blockY() + ","
                            + position.blockZ() + " dir=" + direction + " support=" + support.name());
                }
                continue;
            }

            var newState = getStateForPlacement(currentState, level, position, direction, config);
            if (newState == null) {
                if (debugGate(position)) {
                    System.out.println("MFG null pos=" + position.blockX() + "," + position.blockY() + ","
                            + position.blockZ() + " dir=" + direction + " support=" + support.name());
                }
                return false;
            }

            level.setBlock(position, newState);
            markPostProcess(level, position);
            var roll = random.nextFloat();
            if (debugGate(position)) {
                System.out.println("MFG placed pos=" + position.blockX() + "," + position.blockY() + ","
                        + position.blockZ() + " dir=" + direction + " roll=" + roll);
            }
            if (roll < config.chanceOfSpreading()) {
                spreadFromFaceTowardRandomDirection(level, position, direction, random, config);
            }

            return true;
        }

        return false;
    }

    private static boolean debugGate(BlockVec position) {
        var box = System.getProperty("worldgen.mfgBox", "");
        if (box.isEmpty()) {
            return false;
        }
        var parts = box.split(",");
        return position.blockX() >= Integer.parseInt(parts[0]) && position.blockX() <= Integer.parseInt(parts[2])
                && position.blockZ() >= Integer.parseInt(parts[1]) && position.blockZ() <= Integer.parseInt(parts[3]);
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
        } else if (currentState.compare(Block.WATER) && "0".equals(currentState.getProperty("level"))) {
            // Vanilla MultifaceBlock.getStateForPlacement waterlogs only over
            // a SOURCE (isSourceOfType); a growth placed into flowing water
            // stays dry and replaces the flow.
            base = waterlogged(config.placeBlock());
        } else {
            base = config.placeBlock();
        }

        if (!canAttachTo(level, position, face)) {
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
                markPostProcess(level, spreadPos);
                return true;
            }
        }

        return false;
    }

    private static boolean canSpreadInto(Block.Getter level, BlockVec position, Direction face, MultifaceGrowthConfiguration config) {
        var state = level.getBlock(position);
        if (!state.air() && !state.compare(config.placeBlock()) && !state.compare(Block.WATER)) {
            return false;
        }

        // isValidStateForPlacement: face not yet grown, support face behind
        if (state.compare(config.placeBlock()) && hasFace(state, face)) {
            return false;
        }

        return canAttachTo(level, position, face);
    }

    private static boolean hasFace(Block state, Direction face) {
        return "true".equals(state.getProperty(face.serializedName()));
    }

    /** Vanilla {@code MultifaceBlock.canAttachTo} toward the support behind {@code face}. */
    private static boolean canAttachTo(Block.Getter level, BlockVec position, Direction face) {
        return SturdyFaces.canAttachTo(level.getBlock(face.relative(position)), face.opposite().blockFace());
    }

    /**
     * Vanilla {@code markPosForPostProcessing}: every placed multiface growth
     * is post-processed at FULL promotion, stripping faces whose support was
     * removed by later decoration (see {@code WaterSpread.postProcessMarked}).
     */
    private static void markPostProcess(Object level, BlockVec position) {
        if (level instanceof GenerationUnitAdapter adapter) {
            adapter.markPostProcess(position);
        }
    }

    private static Block waterlogged(Block block) {
        return block.getProperty("waterlogged") != null ? block.withProperty("waterlogged", "true") : block;
    }

    private static boolean isAirOrWater(Block state) {
        return state.air() || state.compare(Block.WATER);
    }
}
