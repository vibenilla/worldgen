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

    /** Source fluid (amount 8), flowing (amount 1-7), or falling. */
    private record Fluid(int amount, boolean falling, boolean source, boolean lava) {
        static final Fluid EMPTY = new Fluid(0, false, false, false);

        boolean isEmpty() {
            return this.amount <= 0 && !this.source;
        }

        /** Vanilla overworld lava: dropOff 2 and slope find distance 2 (water: 1 and 4). */
        int dropOff() {
            return this.lava ? 2 : DROP_OFF;
        }

        int slopeFindDistance() {
            return this.lava ? 2 : SLOPE_FIND_DISTANCE;
        }

        Block encode() {
            var base = this.lava ? Block.LAVA : Block.WATER;
            if (this.source) {
                return base;
            }
            var legacyLevel = 8 - Math.min(this.amount, 8) + (this.falling ? 8 : 0);
            return base.withProperty("level", Integer.toString(legacyLevel));
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
            // Vanilla postProcessGeneration for a non-liquid marked block:
            // its fluid (waterlogged / inherent) ticks, then the block gets
            // Block.updateFromNeighbourShapes - for a multiface growth that
            // strips every face whose support was removed by decoration that
            // ran after the placing feature, air once no face remains; for a
            // floor-dependent water plant over later-placed magma the fold
            // returns plain AIR (setBlock with UPDATE_KNOWN_SHAPE, so a tall
            // upper half above stays orphaned and no column can form).
            if (!fluidOf(state).isEmpty()) {
                tick(level, position);
            }
            var updated = blockAt(level, position);
            if (isFloorPlant(updated) && !plantSurvives(level, position, updated)) {
                level.setBlock(position, Block.AIR);
                return;
            }
            var updatedKey = updated.key().value();
            if ((updatedKey.endsWith("_fence") || updatedKey.equals("iron_bars"))
                    && BlockSupports.manager() != null) {
                // A fence or iron-bars block marked by StructurePiece.placeBlock
                // recomputes its connections against the finished neighborhood
                rocks.minestom.worldgen.structure.template.StructureShapeUpdater.update(
                        level, BlockSupports.manager(), java.util.List.of(position));
                return;
            }
            stripUnsupportedFaces(level, position, updated);
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
            // Vanilla BubbleColumnBlock.updateColumn writes without
            // UPDATE_KNOWN_SHAPE, so each converted cell shape-updates its
            // neighbours: an unsupported water plant next to the column is
            // destroyed (updateOrDestroy -> destroyBlock), leaving its
            // inherent WATER source - which lets a later marked position
            // above the plant's own magma reform a second column through it.
            destroyUnsupportedSeagrassNeighbors(level, current);
            current = current.add(0, 1, 0);
        }
    }

    private static boolean isSeagrass(Block state) {
        var key = state.key().value();
        return key.equals("seagrass") || key.equals("tall_seagrass");
    }

    private static boolean isFloorPlant(Block state) {
        var key = state.key().value();
        return switch (key) {
            case "seagrass", "tall_seagrass", "short_grass", "fern", "tall_grass", "large_fern",
                    "firefly_bush", "bush", "dead_bush", "short_dry_grass", "tall_dry_grass",
                    "red_mushroom", "brown_mushroom",
                    // Vanilla BigDripleafBlock.updateShape folds an unsupported
                    // LEAF to air; the STEM only schedules a tick (which never
                    // fires while paused), so stems survive the fold
                    "big_dripleaf", "small_dripleaf" -> true;
            default -> false;
        };
    }

    /**
     * Vanilla canSurvive folded by {@code Block.updateFromNeighbourShapes} at
     * a marked position: an upper double-plant half needs its lower half
     * below; seagrass needs a sturdy top face outside cannot_support_seagrass
     * (magma); vegetation needs a supports_vegetation floor and dry
     * vegetation a supports_dry_vegetation one; a mushroom needs an
     * overriding floor or raw brightness below 13, which at the lit
     * post-process stage means it cannot be the column's sky-exposed top.
     */
    private static boolean plantSurvives(GenerationUnitAdapter level, BlockVec position, Block state) {
        var key = state.key().value();
        var below = blockAt(level, position.add(0, -1, 0));
        if ("upper".equals(state.getProperty("half"))) {
            return below.key().equals(state.key()) && "lower".equals(below.getProperty("half"));
        }
        // A double-plant lower half also folds the UP direction: with no
        // matching upper half above (a tree canopy overwrote it), vanilla
        // DoublePlantBlock.updateShape returns air
        if ("lower".equals(state.getProperty("half"))) {
            var above = blockAt(level, position.add(0, 1, 0));
            if (!above.key().equals(state.key()) || !"upper".equals(above.getProperty("half"))) {
                return false;
            }
        }
        return switch (key) {
            case "seagrass", "tall_seagrass" ->
                    SturdyFaces.isFaceSturdy(below, BlockFace.TOP) && !below.compare(Block.MAGMA_BLOCK);
            case "big_dripleaf" ->
                    below.key().value().equals("big_dripleaf_stem")
                            || below.key().value().equals("big_dripleaf")
                            || BlockSupports.isInTag("minecraft:supports_big_dripleaf", below);
            case "small_dripleaf" ->
                    BlockSupports.isInTag("minecraft:supports_small_dripleaf", below);
            case "dead_bush", "short_dry_grass", "tall_dry_grass" ->
                    BlockSupports.isInTag("minecraft:supports_dry_vegetation", below);
            case "red_mushroom", "brown_mushroom" ->
                    !level.hasSkylight()
                            || BlockSupports.isInTag("minecraft:overrides_mushroom_light_requirement", below)
                            || level.heightmap(GenerationUnitAdapter.HeightmapType.WORLD_SURFACE,
                                    position.blockX(), position.blockZ()) > position.blockY() + 1;
            default -> BlockSupports.isInTag("minecraft:supports_vegetation", below);
        };
    }

    private static void destroyUnsupportedSeagrassNeighbors(GenerationUnitAdapter level, BlockVec position) {
        for (var direction : Direction.HORIZONTAL) {
            var neighborPos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            var neighbor = blockAt(level, neighborPos);
            if (!isSeagrass(neighbor) || plantSurvives(level, neighborPos, neighbor)) {
                continue;
            }
            level.setBlock(neighborPos, Block.WATER);
            // destroyBlock's own setBlock cascades: the other half of a tall
            // plant fails canSurvive next and is destroyed to water too
            var abovePos = neighborPos.add(0, 1, 0);
            var above = blockAt(level, abovePos);
            if (above.key().value().equals("tall_seagrass") && "upper".equals(above.getProperty("half"))) {
                level.setBlock(abovePos, Block.WATER);
            }
            var belowPos = neighborPos.add(0, -1, 0);
            var belowState = blockAt(level, belowPos);
            if (belowState.key().value().equals("tall_seagrass") && "lower".equals(belowState.getProperty("half"))) {
                level.setBlock(belowPos, Block.WATER);
            }
        }
    }

    private static boolean isPlainWaterSource(Block state) {
        return state.key().value().equals("water") && fluidOf(state).source();
    }

    /**
     * Vanilla {@code MultifaceBlock.updateShape} folded over all six
     * neighbours ({@code Block.updateFromNeighbourShapes}): every grown face
     * without an attachable support behind it is removed, and a growth left
     * with no face at all becomes plain air (even when waterlogged).
     */
    private static void stripUnsupportedFaces(GenerationUnitAdapter level, BlockVec position, Block state) {
        var key = state.key().value();
        if (!key.equals("glow_lichen") && !key.equals("sculk_vein") && !key.equals("resin_clump")) {
            return;
        }

        var updated = state;
        var hasFace = false;
        for (var direction : Direction.values()) {
            var property = direction.serializedName();
            if (!"true".equals(updated.getProperty(property))) {
                continue;
            }
            var support = blockAt(level, direction.relative(position));
            if (SturdyFaces.canAttachTo(support, direction.opposite().blockFace())) {
                hasFace = true;
            } else {
                updated = updated.withProperty(property, "false");
            }
        }

        if (!hasFace) {
            updated = Block.AIR;
        }
        if (updated != state) {
            level.setBlock(position, updated);
        }
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
            var newFluid = getNewLiquid(level, position, state, fluid);
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
            var newBelowFluid = getNewLiquid(level, belowPos, belowState, fluid);
            if (canReplaceWith(belowFluid, newBelowFluid, Direction.DOWN) && canHoldSpecificFluid(belowState)) {
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
        var neighborAmount = fluid.amount() - fluid.dropOff();
        if (fluid.falling()) {
            neighborAmount = 7;
        }
        if (neighborAmount <= 0) {
            return;
        }

        var spreads = getSpread(level, position, state, fluid);
        for (var entry : spreads.entrySet()) {
            var direction = entry.getKey();
            var neighborPos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            spreadTo(level, neighborPos, blockAt(level, neighborPos), entry.getValue());
        }
    }

    private static Map<Direction, Fluid> getSpread(GenerationUnitAdapter level, BlockVec position, Block state,
            Fluid spreading) {
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

            var newFluid = getNewLiquid(level, testPos, testState, spreading);
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
                distance = getSlopeDistance(level, testPos, 1, direction.opposite(), testState, holeCache, spreading);
            }

            if (distance < lowest) {
                result.clear();
            }
            if (distance <= lowest) {
                if (canReplaceWith(testFluid, newFluid, direction)) {
                    result.put(direction, newFluid);
                }
                lowest = distance;
            }
        }

        return result;
    }

    private static int getSlopeDistance(GenerationUnitAdapter level, BlockVec position, int pass,
            Direction from, Block state, Map<BlockVec, Boolean> holeCache, Fluid spreading) {
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
            if (pass < spreading.slopeFindDistance()) {
                var value = getSlopeDistance(level, testPos, pass + 1, direction.opposite(), testState, holeCache, spreading);
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
        // Vanilla isSourceBlockOfThisType: only a SAME-type source stops the
        // spread scan - a lava source in a water spread's path is still a
        // candidate (LavaFluid.canBeReplacedWith accepts water)
        return !(testFluid.source() && testFluid.lava() == fluidOf(sourceState).lava())
                && canHoldAnyFluid(testState)
                && canPassThroughWall(direction, sourceState, testState);
    }

    private static boolean canReplaceWith(Fluid existing, Fluid replacement, Direction direction) {
        // Vanilla FluidState.canBeReplacedWith per type: an empty cell accepts
        // anything; lava of height >= 4/9 (amount >= 4) yields to incoming
        // water from any direction; water yields only to a non-water fluid
        // arriving from above; same-type never replaces.
        if (existing.isEmpty()) {
            return true;
        }
        if (existing.lava() == replacement.lava()) {
            return false;
        }
        if (existing.lava()) {
            return existing.amount() >= 4 && !replacement.lava();
        }
        return direction == Direction.DOWN;
    }

    private static void spreadTo(GenerationUnitAdapter level, BlockVec position, Block state, Fluid fluid) {
        if (System.getProperty("worldgen.waterDebug") != null) {
            System.out.println("WSPREAD " + position.blockX() + "," + position.blockY() + "," + position.blockZ()
                    + " " + fluid + " over " + state.name());
        }
        if (state.getProperty("waterlogged") != null) {
            // Vanilla SimpleWaterloggedBlock.placeLiquid only accepts
            // Fluids.WATER - the source type. A flowing spread into a
            // waterloggable block is chosen as a spread target but the
            // placement is a silent no-op; only a source (via the two-source
            // conversion) actually waterlogs.
            if (fluid.source() && !fluid.lava()
                    && "false".equals(state.getProperty("waterlogged")) && WaterStates.canBeWaterlogged(state)) {
                level.setBlock(position, state.withProperty("waterlogged", "true"));
            }
            return;
        }
        level.setBlock(position, fluid.encode());
        // Vanilla spreadTo writes with neighbor shape updates: destroying a
        // double-plant half takes the partner half with it (updateOrDestroy
        // -> destroyBlock leaves the partner's inherent fluid, air for a land
        // plant), instead of leaving an orphan
        var half = state.getProperty("half");
        if (half != null) {
            var partnerPos = "lower".equals(half) ? position.add(0, 1, 0) : position.add(0, -1, 0);
            var partner = blockAt(level, partnerPos);
            if (partner.key().equals(state.key()) && !half.equals(partner.getProperty("half"))) {
                level.setBlock(partnerPos, fluidOf(partner).isEmpty() ? Block.AIR : Block.WATER);
            }
        }
        if (!fluid.lava()) {
            solidifyAdjacentLava(level, position);
        }
    }

    /**
     * Vanilla {@code LiquidBlock.shouldSpreadLiquid}, triggered by the
     * neighbor updates of a block change (water placement or a conversion):
     * an updated lava block scans its OWN up and horizontal neighbors for
     * water - so lava sitting under a long-present water source converts as
     * soon as ANY adjacent change pokes it - and solidifies to obsidian for
     * a source, cobblestone for flowing. The conversion is a
     * {@code setBlockAndUpdate}, so it propagates updates to ITS neighbors
     * in turn (chained conversions).
     */
    private static void solidifyAdjacentLava(GenerationUnitAdapter level, BlockVec changedPosition) {
        var neighbors = new BlockVec[] {
                changedPosition.add(0, -1, 0), changedPosition.add(0, 1, 0),
                changedPosition.add(1, 0, 0), changedPosition.add(-1, 0, 0),
                changedPosition.add(0, 0, 1), changedPosition.add(0, 0, -1)
        };
        for (var lavaPosition : neighbors) {
            var state = blockAt(level, lavaPosition);
            if (!state.key().value().equals("lava")) {
                continue;
            }
            if (!hasWaterAboveOrBeside(level, lavaPosition)) {
                continue;
            }
            level.setBlock(lavaPosition, fluidOf(state).source() ? Block.OBSIDIAN : Block.COBBLESTONE);
            solidifyAdjacentLava(level, lavaPosition);
        }
    }

    /** The water scan of {@code shouldSpreadLiquid}: up and the four horizontals, never down. */
    private static boolean hasWaterAboveOrBeside(GenerationUnitAdapter level, BlockVec lavaPosition) {
        var checks = new BlockVec[] {
                lavaPosition.add(0, 1, 0),
                lavaPosition.add(1, 0, 0), lavaPosition.add(-1, 0, 0),
                lavaPosition.add(0, 0, 1), lavaPosition.add(0, 0, -1)
        };
        for (var check : checks) {
            var fluid = fluidOf(blockAt(level, check));
            if (!fluid.isEmpty() && !fluid.lava()) {
                return true;
            }
        }
        return false;
    }

    private static Fluid getNewLiquid(GenerationUnitAdapter level, BlockVec position, Block state, Fluid forFluid) {
        var highestNeighbor = 0;
        var neighborSources = 0;

        for (var direction : Direction.HORIZONTAL) {
            var relativePos = position.add(direction.stepX(), direction.stepY(), direction.stepZ());
            var neighborState = blockAt(level, relativePos);
            var neighborFluid = fluidOf(neighborState);
            if (neighborFluid.isEmpty() || neighborFluid.lava() != forFluid.lava()
                    || !canPassThroughWall(direction, state, neighborState)) {
                continue;
            }
            if (neighborFluid.source()) {
                neighborSources++;
            }
            highestNeighbor = Math.max(highestNeighbor, neighborFluid.amount());
        }

        // Vanilla WaterFluid.canConvertToSource is true, LavaFluid's is false
        // (the lava-source-conversion gamerule is off by default)
        if (!forFluid.lava() && neighborSources >= 2) {
            var belowState = blockAt(level, position.add(0, -1, 0));
            if (blocksMotion(belowState) || fluidOf(belowState).source()) {
                return new Fluid(8, false, true, false);
            }
        }

        var aboveState = blockAt(level, position.add(0, 1, 0));
        var aboveFluid = fluidOf(aboveState);
        if (!aboveFluid.isEmpty() && aboveFluid.lava() == forFluid.lava()
                && canPassThroughWall(Direction.UP, state, aboveState)) {
            return new Fluid(8, true, false, forFluid.lava());
        }

        var amount = highestNeighbor - forFluid.dropOff();
        return amount <= 0 ? Fluid.EMPTY : new Fluid(amount, false, false, forFluid.lava());
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

    /**
     * Vanilla {@code BlockStateBase.blocksMotion}: the legacy-solid flag
     * minus cobweb and bamboo sapling. Collision-shape emptiness is the
     * wrong test - a moss carpet has a collision box but water still flows
     * into (and destroys) it.
     */
    private static boolean blocksMotion(Block state) {
        return state.registry().isSolid() && !state.compare(Block.COBWEB) && !state.compare(Block.BAMBOO_SAPLING);
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
        var key = state.key().value();
        if (key.equals("water") || key.equals("lava")) {
            var lava = key.equals("lava");
            var levelProperty = state.getProperty("level");
            var legacyLevel = levelProperty == null ? 0 : Integer.parseInt(levelProperty);
            if (legacyLevel == 0) {
                return new Fluid(8, false, true, lava);
            }
            var falling = legacyLevel >= 8;
            var amount = falling ? 8 : 8 - legacyLevel;
            return new Fluid(amount, falling, false, lava);
        }
        if (WaterStates.hasWaterFluid(state)) {
            return new Fluid(8, false, true, false);
        }
        return Fluid.EMPTY;
    }
}
