package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Single-round emulation of vanilla water scheduled ticks after chunk
 * generation: every feature-placed water block gets one {@code
 * FlowingFluid.tick} (spread down or to slope-weighted sides, converting
 * spread targets with two adjacent sources into new sources), and the blocks
 * that spread creates never tick themselves. This mirrors the pregen ladder,
 * where a chunk block-ticks only for the short dwell of its own forceload:
 * the initially scheduled fluid ticks run once and the follow-up ticks they
 * schedule are dropped when the chunk stops ticking.
 */
public final class WaterSpread {
    private static final int SLOPE_FIND_DISTANCE = 4;
    private static final int DROP_OFF = 1;

    private WaterSpread() {
    }

    /** Source water (amount 8), flowing water (amount 1-7), or falling water. */
    private record Fluid(int amount, boolean falling, boolean source) {
        static final Fluid EMPTY = new Fluid(0, false, false);

        boolean isEmpty() {
            return this.amount <= 0 && !this.source;
        }

        Block encode() {
            if (this.source) {
                return Block.WATER;
            }
            var legacyLevel = 8 - Math.min(this.amount, 8) + (this.falling ? 8 : 0);
            return Block.WATER.withProperty("level", Integer.toString(legacyLevel));
        }
    }

    /**
     * Vanilla {@code LevelChunk.postProcessGeneration} for a marked position
     * above a placed magma_block / soul_sand: the water there gets its fluid
     * tick, then {@code LiquidBlock.tick} runs
     * {@code BubbleColumnBlock.updateColumn}, converting the whole source
     * water column above the column-enabling block in one synchronous pass.
     */
    public static void postProcessMarked(GenerationUnitAdapter level, BlockVec position) {
        var state = blockAt(level, position);
        if (!isPlainWaterSource(state)) {
            return;
        }

        tick(level, position);

        var below = blockAt(level, position.add(0, -1, 0));
        boolean dragDown;
        if (below.compare(Block.MAGMA_BLOCK)) {
            dragDown = true;
        } else if (below.compare(Block.SOUL_SAND)) {
            dragDown = false;
        } else {
            return;
        }

        var columnState = Block.BUBBLE_COLUMN.withProperty("drag", Boolean.toString(dragDown));
        var current = position;
        while (canOccupyColumn(blockAt(level, current))) {
            level.setBlock(current, columnState);
            current = current.add(0, 1, 0);
        }
    }

    private static boolean isPlainWaterSource(Block state) {
        return state.key().value().equals("water") && fluidOf(state).source();
    }

    private static boolean canOccupyColumn(Block state) {
        return state.compare(Block.BUBBLE_COLUMN) || isPlainWaterSource(state);
    }

    public static void tick(GenerationUnitAdapter level, BlockVec position) {
        var state = level.getBlock(position.blockX(), position.blockY(), position.blockZ());
        var fluid = fluidOf(state);
        if (System.getProperty("worldgen.waterDebug") != null) {
            System.out.println("WTICK " + position.blockX() + "," + position.blockY() + "," + position.blockZ()
                    + " state=" + state.name() + " fluid=" + fluid);
        }
        if (fluid.isEmpty()) {
            return;
        }

        if (!fluid.source()) {
            var newFluid = getNewLiquid(level, position, state);
            if (newFluid.isEmpty()) {
                state = Block.AIR;
                level.setBlock(position, state);
                fluid = newFluid;
            } else if (!newFluid.equals(fluid)) {
                state = newFluid.encode();
                level.setBlock(position, state);
                fluid = newFluid;
            }
        }

        spread(level, position, state, fluid);
    }

    private static void spread(GenerationUnitAdapter level, BlockVec position, Block state, Fluid fluid) {
        if (fluid.isEmpty()) {
            return;
        }

        var belowPos = position.add(0, -1, 0);
        var belowState = blockAt(level, belowPos);
        var belowFluid = fluidOf(belowState);
        if (canMaybePassThrough(level, position, state, Direction.DOWN, belowPos, belowState, belowFluid)) {
            var newBelowFluid = getNewLiquid(level, belowPos, belowState);
            if (canReplaceWith(belowFluid, newBelowFluid) && canHoldSpecificFluid(belowState)) {
                spreadTo(level, belowPos, belowState, newBelowFluid);
                if (sourceNeighborCount(level, position) >= 3) {
                    spreadToSides(level, position, fluid, state);
                }
                return;
            }
        }

        if (fluid.source() || !isWaterHole(level, position, state, belowPos, belowState)) {
            spreadToSides(level, position, fluid, state);
        }
    }

    private static void spreadToSides(GenerationUnitAdapter level, BlockVec position, Fluid fluid, Block state) {
        var neighborAmount = fluid.amount() - DROP_OFF;
        if (fluid.falling()) {
            neighborAmount = 7;
        }
        if (neighborAmount <= 0) {
            return;
        }

        var spreads = getSpread(level, position, state);
        for (var entry : spreads.entrySet()) {
            var direction = entry.getKey();
            var neighborPos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            spreadTo(level, neighborPos, blockAt(level, neighborPos), entry.getValue());
        }
    }

    private static Map<Direction, Fluid> getSpread(GenerationUnitAdapter level, BlockVec position, Block state) {
        var lowest = 1000;
        var result = new EnumMap<Direction, Fluid>(Direction.class);
        Map<BlockVec, Boolean> holeCache = null;

        for (var direction : Direction.HORIZONTAL) {
            var testPos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            var testState = blockAt(level, testPos);
            var testFluid = fluidOf(testState);
            if (!canMaybePassThrough(level, position, state, direction, testPos, testState, testFluid)) {
                continue;
            }

            var newFluid = getNewLiquid(level, testPos, testState);
            if (!canHoldSpecificFluid(testState)) {
                continue;
            }

            if (holeCache == null) {
                holeCache = new HashMap<>();
            }

            int distance;
            if (isHole(level, testPos, holeCache)) {
                distance = 0;
            } else {
                distance = getSlopeDistance(level, testPos, 1, direction.opposite(), testState, holeCache);
            }

            if (distance < lowest) {
                result.clear();
            }
            if (distance <= lowest) {
                if (canReplaceWith(testFluid, newFluid)) {
                    result.put(direction, newFluid);
                }
                lowest = distance;
            }
        }

        return result;
    }

    private static int getSlopeDistance(GenerationUnitAdapter level, BlockVec position, int pass,
            Direction from, Block state, Map<BlockVec, Boolean> holeCache) {
        var lowest = 1000;
        for (var direction : Direction.HORIZONTAL) {
            if (direction == from) {
                continue;
            }
            var testPos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            var testState = blockAt(level, testPos);
            var testFluid = fluidOf(testState);
            if (!canPassThrough(level, position, state, direction, testPos, testState, testFluid)) {
                continue;
            }
            if (isHole(level, testPos, holeCache)) {
                return pass;
            }
            if (pass < SLOPE_FIND_DISTANCE) {
                var value = getSlopeDistance(level, testPos, pass + 1, direction.opposite(), testState, holeCache);
                if (value < lowest) {
                    lowest = value;
                }
            }
        }
        return lowest;
    }

    private static boolean isHole(GenerationUnitAdapter level, BlockVec position, Map<BlockVec, Boolean> holeCache) {
        return holeCache.computeIfAbsent(position, pos -> {
            var state = blockAt(level, pos);
            var belowPos = pos.add(0, -1, 0);
            var belowState = blockAt(level, belowPos);
            return isWaterHole(level, pos, state, belowPos, belowState);
        });
    }

    private static boolean isWaterHole(GenerationUnitAdapter level, BlockVec topPos, Block topState,
            BlockVec bottomPos, Block bottomState) {
        if (!canPassThroughWall(Direction.DOWN, topState, bottomState)) {
            return false;
        }
        return !fluidOf(bottomState).isEmpty() || canHoldFluid(bottomState);
    }

    private static boolean canPassThrough(GenerationUnitAdapter level, BlockVec sourcePos, Block sourceState,
            Direction direction, BlockVec testPos, Block testState, Fluid testFluid) {
        return canMaybePassThrough(level, sourcePos, sourceState, direction, testPos, testState, testFluid)
                && canHoldSpecificFluid(testState);
    }

    private static boolean canMaybePassThrough(GenerationUnitAdapter level, BlockVec sourcePos, Block sourceState,
            Direction direction, BlockVec testPos, Block testState, Fluid testFluid) {
        return !(testFluid.source())
                && canHoldAnyFluid(testState)
                && canPassThroughWall(direction, sourceState, testState);
    }

    private static boolean canReplaceWith(Fluid existing, Fluid replacement) {
        // Vanilla WaterFluid.canBeReplacedWith: only a falling non-same fluid
        // replaces water; between water states, an empty cell accepts anything
        // and existing water is never replaced by more water (setBlock still
        // happens for the same-type amount raise through spreadTo's caller
        // only when the target held no source).
        return existing.isEmpty();
    }

    private static void spreadTo(GenerationUnitAdapter level, BlockVec position, Block state, Fluid fluid) {
        if (System.getProperty("worldgen.waterDebug") != null) {
            System.out.println("WSPREAD " + position.blockX() + "," + position.blockY() + "," + position.blockZ()
                    + " " + fluid + " over " + state.name());
        }
        if (state.getProperty("waterlogged") != null) {
            if ("false".equals(state.getProperty("waterlogged"))) {
                level.setBlock(position, state.withProperty("waterlogged", "true"));
            }
            return;
        }
        level.setBlock(position, fluid.encode());
    }

    private static Fluid getNewLiquid(GenerationUnitAdapter level, BlockVec position, Block state) {
        var highestNeighbor = 0;
        var neighborSources = 0;

        for (var direction : Direction.HORIZONTAL) {
            var relativePos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            var neighborState = blockAt(level, relativePos);
            var neighborFluid = fluidOf(neighborState);
            if (neighborFluid.isEmpty() || !canPassThroughWall(direction, state, neighborState)) {
                continue;
            }
            if (neighborFluid.source()) {
                neighborSources++;
            }
            highestNeighbor = Math.max(highestNeighbor, neighborFluid.amount());
        }

        if (neighborSources >= 2) {
            var belowState = blockAt(level, position.add(0, -1, 0));
            if (isFullCube(belowState) || fluidOf(belowState).source()) {
                return new Fluid(8, false, true);
            }
        }

        var aboveState = blockAt(level, position.add(0, 1, 0));
        var aboveFluid = fluidOf(aboveState);
        if (!aboveFluid.isEmpty() && canPassThroughWall(Direction.UP, state, aboveState)) {
            return new Fluid(8, true, false);
        }

        var amount = highestNeighbor - DROP_OFF;
        return amount <= 0 ? Fluid.EMPTY : new Fluid(amount, false, false);
    }

    private static int sourceNeighborCount(GenerationUnitAdapter level, BlockVec position) {
        var count = 0;
        for (var direction : Direction.HORIZONTAL) {
            var testPos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            if (fluidOf(blockAt(level, testPos)).source()) {
                count++;
            }
        }
        return count;
    }

    private static boolean canHoldFluid(Block state) {
        return canHoldAnyFluid(state) && canHoldSpecificFluid(state);
    }

    private static boolean canHoldAnyFluid(Block state) {
        if (state.getProperty("waterlogged") != null) {
            return true;
        }
        if (blocksMotion(state)) {
            return false;
        }
        var key = state.key().value();
        return !key.endsWith("_door") && !key.endsWith("_sign")
                && !key.equals("ladder") && !key.equals("sugar_cane")
                && !key.equals("bubble_column") && !key.equals("nether_portal")
                && !key.equals("end_portal") && !key.equals("end_gateway")
                && !key.equals("structure_void");
    }

    private static boolean canHoldSpecificFluid(Block state) {
        var waterlogged = state.getProperty("waterlogged");
        return waterlogged == null || waterlogged.equals("false");
    }

    private static boolean canPassThroughWall(Direction direction, Block sourceState, Block targetState) {
        if (isFullCube(targetState) || isFullCube(sourceState)) {
            return false;
        }
        return !(faceFull(sourceState, direction) || faceFull(targetState, direction.opposite()));
    }

    private static boolean blocksMotion(Block state) {
        var shape = state.registry().collisionShape();
        if (shape == null) {
            return false;
        }
        var end = shape.relativeEnd();
        var start = shape.relativeStart();
        return end.x() - start.x() > 0 || end.y() - start.y() > 0 || end.z() - start.z() > 0;
    }

    private static boolean isFullCube(Block state) {
        var shape = state.registry().collisionShape();
        if (shape == null) {
            return false;
        }
        for (var face : BlockFace.values()) {
            if (!shape.isFaceFull(face)) {
                return false;
            }
        }
        return true;
    }

    private static boolean faceFull(Block state, Direction direction) {
        var shape = state.registry().collisionShape();
        return shape != null && shape.isFaceFull(blockFaceOf(direction));
    }

    private static BlockFace blockFaceOf(Direction direction) {
        return switch (direction) {
            case DOWN -> BlockFace.BOTTOM;
            case UP -> BlockFace.TOP;
            case NORTH -> BlockFace.NORTH;
            case SOUTH -> BlockFace.SOUTH;
            case WEST -> BlockFace.WEST;
            case EAST -> BlockFace.EAST;
        };
    }

    private static Block blockAt(GenerationUnitAdapter level, BlockVec position) {
        return level.getBlock(position.blockX(), position.blockY(), position.blockZ());
    }

    private static Fluid fluidOf(Block state) {
        if (state.key().value().equals("water")) {
            var levelProperty = state.getProperty("level");
            var legacyLevel = levelProperty == null ? 0 : Integer.parseInt(levelProperty);
            if (legacyLevel == 0) {
                return new Fluid(8, false, true);
            }
            var falling = legacyLevel >= 8;
            var amount = falling ? 8 : 8 - legacyLevel;
            return new Fluid(amount, falling, false);
        }
        if (WaterStates.hasWaterFluid(state)) {
            return new Fluid(8, false, true);
        }
        return Fluid.EMPTY;
    }
}
