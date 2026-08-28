package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Port of vanilla's {@code net.minecraft.world.level.block.SculkSpreader}
 * (world generation configuration only, i.e. {@code createWorldGenSpreader}),
 * together with the world-generation-relevant parts of {@code SculkBehaviour},
 * {@code SculkBlock}, {@code SculkVeinBlock} and {@code MultifaceSpreader}
 * that its charge cursors depend on. Non-worldgen behaviour (tick scheduling,
 * particle events, saving/loading) is omitted since it has no effect on the
 * blocks a sculk patch feature writes.
 */
public final class SculkSpreader {
    private static final int MAX_CURSORS = 32;
    private static final int MAX_CHARGE = 1000;
    private static final int MAX_CURSOR_DISTANCE = 1024;
    private static final int GROWTH_SPAWN_COST = 50;
    private static final int NO_GROWTH_RADIUS = 1;
    private static final int CHARGE_DECAY_RATE = 5;
    private static final int ADDITIONAL_DECAY_RATE = 10;

    /**
     * Vanilla's {@code SculkVeinBlock.NON_CORNER_NEIGHBOURS}: every offset in
     * [-1,1]^3 with at least one zero coordinate, excluding the origin, in the
     * exact order {@code BlockPos.betweenClosedStream} produces them (x
     * fastest, then y, then z).
     */
    private static final int[][] NON_CORNER_NEIGHBOURS = {
            {0, -1, -1}, {-1, 0, -1}, {0, 0, -1}, {1, 0, -1}, {0, 1, -1},
            {-1, -1, 0}, {0, -1, 0}, {1, -1, 0}, {-1, 0, 0}, {1, 0, 0},
            {-1, 1, 0}, {0, 1, 0}, {1, 1, 0},
            {0, -1, 1}, {-1, 0, 1}, {0, 0, 1}, {1, 0, 1}, {0, 1, 1}
    };

    /** Vanilla's {@code #minecraft:sculk_replaceable} block tag (26.2 contents). */
    private static final Set<String> SCULK_REPLACEABLE = Set.of(
            "minecraft:stone", "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
            "minecraft:tuff", "minecraft:deepslate",
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots",
            "minecraft:moss_block", "minecraft:pale_moss_block",
            "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
            "minecraft:terracotta", "minecraft:white_terracotta", "minecraft:orange_terracotta",
            "minecraft:magenta_terracotta", "minecraft:light_blue_terracotta", "minecraft:yellow_terracotta",
            "minecraft:lime_terracotta", "minecraft:pink_terracotta", "minecraft:gray_terracotta",
            "minecraft:light_gray_terracotta", "minecraft:cyan_terracotta", "minecraft:purple_terracotta",
            "minecraft:blue_terracotta", "minecraft:brown_terracotta", "minecraft:green_terracotta",
            "minecraft:red_terracotta", "minecraft:black_terracotta",
            "minecraft:crimson_nylium", "minecraft:warped_nylium",
            "minecraft:netherrack", "minecraft:basalt", "minecraft:blackstone",
            "minecraft:sand", "minecraft:red_sand", "minecraft:gravel", "minecraft:soul_sand",
            "minecraft:soul_soil", "minecraft:calcite", "minecraft:smooth_basalt", "minecraft:clay",
            "minecraft:dripstone_block", "minecraft:end_stone", "minecraft:red_sandstone",
            "minecraft:sandstone", "minecraft:sulfur", "minecraft:cinnabar");

    /**
     * Vanilla's {@code #minecraft:sculk_replaceable_world_gen} block tag
     * (26.2 contents): {@link #SCULK_REPLACEABLE} plus deepslate building blocks.
     */
    private static final Set<String> SCULK_REPLACEABLE_WORLD_GEN;

    static {
        var worldGen = new java.util.HashSet<>(SCULK_REPLACEABLE);
        worldGen.add("minecraft:deepslate_bricks");
        worldGen.add("minecraft:deepslate_tiles");
        worldGen.add("minecraft:cobbled_deepslate");
        worldGen.add("minecraft:cracked_deepslate_bricks");
        worldGen.add("minecraft:cracked_deepslate_tiles");
        worldGen.add("minecraft:polished_deepslate");
        SCULK_REPLACEABLE_WORLD_GEN = Set.copyOf(worldGen);
    }

    private enum SpreadType {
        SAME_POSITION, SAME_PLANE, WRAP_AROUND
    }

    private static final SpreadType[] SAME_POSITION_ONLY = {SpreadType.SAME_POSITION};
    private static final SpreadType[] DEFAULT_SPREAD_ORDER =
            {SpreadType.SAME_POSITION, SpreadType.SAME_PLANE, SpreadType.WRAP_AROUND};

    private List<ChargeCursor> cursors = new ArrayList<>();

    public void addCursors(BlockVec startPos, int charge) {
        while (charge > 0) {
            var currentCharge = Math.min(charge, MAX_CHARGE);
            this.addCursor(new ChargeCursor(startPos, currentCharge));
            charge -= currentCharge;
        }
    }

    public void clear() {
        this.cursors.clear();
    }

    public <T extends Block.Getter & Block.Setter> void updateCursors(
            T level, BlockVec originPos, RandomSource random, boolean spreadVeins) {
        if (this.cursors.isEmpty()) {
            return;
        }

        var survivors = new ArrayList<ChargeCursor>();
        for (var cursor : this.cursors) {
            if (!cursor.isPosUnreasonable(originPos)) {
                cursor.update(level, originPos, random, spreadVeins);
                if (cursor.charge > 0) {
                    survivors.add(cursor);
                }
            }
        }

        this.cursors = survivors;
    }

    private void addCursor(ChargeCursor cursor) {
        if (this.cursors.size() < MAX_CURSORS) {
            this.cursors.add(cursor);
        }
    }

    // --- static block/face helpers -----------------------------------------

    private static final BlockFace[] BLOCK_FACES = {
            BlockFace.BOTTOM, BlockFace.TOP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST
    };

    /**
     * Vanilla's {@code MultifaceBlock.canAttachTo} / {@code SupportType.FULL},
     * approximated with the block's collision shape rather than a coarse
     * solidity flag, since e.g. slabs and stairs are solid but not full on
     * every face.
     */
    private static boolean hasFullFace(Block block, Direction direction) {
        return block.collisionShape().isFaceFull(BLOCK_FACES[direction.ordinal()]);
    }

    private static boolean isReplaceable(Block block) {
        return block.replaceable();
    }

    private static boolean isFire(Block block) {
        return block.compare(Block.FIRE) || block.compare(Block.SOUL_FIRE);
    }

    private static boolean isWaterSource(Block block) {
        return block.compare(Block.WATER);
    }

    private static boolean hasNonWaterFluid(Block block) {
        return block.liquid() && !block.compare(Block.WATER);
    }

    private static boolean hasFace(Block state, Direction face) {
        return "true".equals(state.getProperty(face.serializedName()));
    }

    /**
     * Vanilla's {@code state.getFluidState().isEmpty()} negation: true for
     * actual liquid blocks and for waterlogged blocks alike. Unlike
     * {@code Block#isLiquid()}, which only reflects whether the block itself
     * is a fluid block (e.g. always false for a waterlogged sculk vein).
     */
    private static boolean hasFluid(Block block) {
        return block.liquid() || "true".equals(block.getProperty("waterlogged"));
    }

    private static Set<Direction> availableFaces(Block state) {
        if (!state.compare(Block.SCULK_VEIN)) {
            return Set.of();
        }

        var faces = EnumSet.noneOf(Direction.class);
        for (var direction : Direction.values()) {
            if (hasFace(state, direction)) {
                faces.add(direction);
            }
        }

        return faces;
    }

    private static boolean sameAxis(Direction a, Direction b) {
        return (a.stepX() != 0 && b.stepX() != 0)
                || (a.stepY() != 0 && b.stepY() != 0)
                || (a.stepZ() != 0 && b.stepZ() != 0);
    }

    // --- multiface-style vein spreading --------------------------------------

    private record SpreadPos(BlockVec pos, Direction face) {
    }

    private static SpreadPos spreadPositionFor(SpreadType type, BlockVec pos, Direction spreadDirection, Direction fromFace) {
        return switch (type) {
            case SAME_POSITION -> new SpreadPos(pos, spreadDirection);
            case SAME_PLANE -> new SpreadPos(spreadDirection.relative(pos), fromFace);
            case WRAP_AROUND -> new SpreadPos(fromFace.relative(spreadDirection.relative(pos)), spreadDirection.opposite());
        };
    }

    private static <T extends Block.Getter & Block.Setter> boolean isValidStateForPlacement(
            T level, Block oldState, BlockVec placementPos, Direction placementDirection) {
        if (oldState.compare(Block.SCULK_VEIN) && hasFace(oldState, placementDirection)) {
            return false;
        }

        var neighbourPos = placementDirection.relative(placementPos);
        return hasFullFace(level.getBlock(neighbourPos), placementDirection.opposite());
    }

    private static <T extends Block.Getter & Block.Setter> boolean stateCanBeReplaced(
            T level, BlockVec sourcePos, BlockVec placementPos, Direction placementDirection, Block existingState) {
        var against = level.getBlock(placementDirection.relative(placementPos));
        if (against.compare(Block.SCULK) || against.compare(Block.SCULK_CATALYST) || against.compare(Block.MOVING_PISTON)) {
            return false;
        }

        if (manhattanDistance(sourcePos, placementPos) == 2) {
            var neighbourPos = placementDirection.opposite().relative(sourcePos);
            if (hasFullFace(level.getBlock(neighbourPos), placementDirection)) {
                return false;
            }
        }

        if (hasNonWaterFluid(existingState)) {
            return false;
        }

        if (isFire(existingState)) {
            return false;
        }

        return isReplaceable(existingState) || existingState.air() || existingState.compare(Block.SCULK_VEIN) || isWaterSource(existingState);
    }

    private static <T extends Block.Getter & Block.Setter> boolean canSpreadInto(T level, BlockVec sourcePos, SpreadPos spreadPos) {
        var existingState = level.getBlock(spreadPos.pos());
        return stateCanBeReplaced(level, sourcePos, spreadPos.pos(), spreadPos.face(), existingState)
                && isValidStateForPlacement(level, existingState, spreadPos.pos(), spreadPos.face());
    }

    private static Block stateForPlacement(Block oldState, Direction placementDirection) {
        Block newState;
        if (oldState.compare(Block.SCULK_VEIN)) {
            newState = oldState;
        } else if (isWaterSource(oldState)) {
            newState = Block.SCULK_VEIN.withProperty("waterlogged", "true");
        } else {
            newState = Block.SCULK_VEIN;
        }

        return newState.withProperty(placementDirection.serializedName(), "true");
    }

    private static <T extends Block.Getter & Block.Setter> boolean spreadToFace(T level, SpreadPos spreadPos) {
        var oldState = level.getBlock(spreadPos.pos());
        if (!isValidStateForPlacement(level, oldState, spreadPos.pos(), spreadPos.face())) {
            return false;
        }

        level.setBlock(spreadPos.pos().blockX(), spreadPos.pos().blockY(), spreadPos.pos().blockZ(),
                stateForPlacement(oldState, spreadPos.face()));
        return true;
    }

    /**
     * Vanilla {@code MultifaceSpreader.spreadAll}. {@code sourceState} is the
     * block the spread originates from (a fresh non-vein block, or the vein
     * itself when a charge cursor sitting on vein re-spreads); when it is not
     * itself vein, {@code isOtherBlockValidAsSource} is unconditionally true
     * and every (startingFace, spreadDirection) pair of differing axis is
     * attempted, otherwise only faces the vein already has feed further faces.
     */
    private static <T extends Block.Getter & Block.Setter> long spreadAll(
            T level, BlockVec pos, Block sourceState, SpreadType[] spreadTypes) {
        var isOtherBlockValidAsSource = !sourceState.compare(Block.SCULK_VEIN);
        var placedCount = 0L;
        for (var startingFace : Direction.values()) {
            if (!isOtherBlockValidAsSource && !hasFace(sourceState, startingFace)) {
                continue;
            }

            for (var spreadDirection : Direction.values()) {
                if (sameAxis(spreadDirection, startingFace)) {
                    continue;
                }

                if (!isOtherBlockValidAsSource && !(hasFace(sourceState, startingFace) && !hasFace(sourceState, spreadDirection))) {
                    continue;
                }

                for (var spreadType : spreadTypes) {
                    var spreadPos = spreadPositionFor(spreadType, pos, spreadDirection, startingFace);
                    if (canSpreadInto(level, pos, spreadPos)) {
                        if (spreadToFace(level, spreadPos)) {
                            placedCount++;
                        }
                        break;
                    }
                }
            }
        }

        return placedCount;
    }

    private static <T extends Block.Getter & Block.Setter> boolean regrow(T level, BlockVec pos, Block existing, Set<Direction> faces) {
        var hasAtLeastOneFace = false;
        var newState = Block.SCULK_VEIN;

        for (var face : faces) {
            var neighbourPos = face.relative(pos);
            if (hasFullFace(level.getBlock(neighbourPos), face.opposite())) {
                newState = newState.withProperty(face.serializedName(), "true");
                hasAtLeastOneFace = true;
            }
        }

        if (!hasAtLeastOneFace) {
            return false;
        }

        if (hasFluid(existing)) {
            newState = newState.withProperty("waterlogged", "true");
        }

        level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), newState);
        return true;
    }

    /**
     * Vanilla {@code SculkBehaviour.DEFAULT.attemptSpreadVein}, used only when
     * the cursor sits on a block that is neither sculk nor sculk vein.
     */
    private static <T extends Block.Getter & Block.Setter> boolean defaultAttemptSpreadVein(
            T level, BlockVec pos, Block currentState, Set<Direction> facings) {
        if (facings == null) {
            return spreadAll(level, pos, currentState, SAME_POSITION_ONLY) > 0;
        } else if (!facings.isEmpty()) {
            if (!currentState.air() && !isWaterSource(currentState)) {
                return false;
            }
            return regrow(level, pos, currentState, facings);
        } else {
            return spreadAll(level, pos, currentState, DEFAULT_SPREAD_ORDER) > 0;
        }
    }

    /**
     * Vanilla {@code SculkVeinBlock.attemptPlaceSculk}: pushes a charge into a
     * replaceable neighbour, turning it into sculk and spreading vein around it.
     */
    private static <T extends Block.Getter & Block.Setter> boolean attemptPlaceSculk(T level, BlockVec pos, RandomSource random) {
        var state = level.getBlock(pos);
        var supports = new ArrayList<>(List.of(Direction.values()));
        TreeDecorator.shuffle(supports, random);

        for (var support : supports) {
            if (!hasFace(state, support)) {
                continue;
            }

            var supportPos = support.relative(pos);
            var supportState = level.getBlock(supportPos);
            if (!SCULK_REPLACEABLE_WORLD_GEN.contains(supportState.name())) {
                continue;
            }

            level.setBlock(supportPos.blockX(), supportPos.blockY(), supportPos.blockZ(), Block.SCULK);
            spreadAll(level, supportPos, Block.SCULK, DEFAULT_SPREAD_ORDER);

            var skip = support.opposite();
            for (var veinDirection : Direction.values()) {
                if (veinDirection == skip) {
                    continue;
                }

                var veinPos = veinDirection.relative(supportPos);
                var possibleVein = level.getBlock(veinPos);
                if (possibleVein.compare(Block.SCULK_VEIN)) {
                    dischargeVein(level, possibleVein, veinPos);
                }
            }

            return true;
        }

        return false;
    }

    /** Vanilla {@code SculkVeinBlock.hasSubstrateAccess}, always against the plain (non-worldgen) replaceable tag. */
    private static <T extends Block.Getter & Block.Setter> boolean hasSubstrateAccess(T level, Block state, BlockVec pos) {
        if (!state.compare(Block.SCULK_VEIN)) {
            return false;
        }

        for (var direction : Direction.values()) {
            if (hasFace(state, direction)) {
                var neighbour = level.getBlock(direction.relative(pos));
                if (SCULK_REPLACEABLE.contains(neighbour.name())) {
                    return true;
                }
            }
        }

        return false;
    }

    /** Vanilla {@code SculkVeinBlock.onDischarged}: prunes faces that face already-sculk neighbours. */
    private static <T extends Block.Getter & Block.Setter> void dischargeVein(T level, Block state, BlockVec pos) {
        if (!state.compare(Block.SCULK_VEIN)) {
            return;
        }

        var newState = state;
        for (var direction : Direction.values()) {
            if (hasFace(newState, direction) && level.getBlock(direction.relative(pos)).compare(Block.SCULK)) {
                newState = newState.withProperty(direction.serializedName(), "false");
            }
        }

        if (!hasAnyFace(newState)) {
            newState = hasFluid(state) ? Block.WATER : Block.AIR;
        }

        level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), newState);
    }

    private static boolean hasAnyFace(Block state) {
        for (var direction : Direction.values()) {
            if (hasFace(state, direction)) {
                return true;
            }
        }

        return false;
    }

    private static int manhattanDistance(BlockVec a, BlockVec b) {
        return Math.abs(a.blockX() - b.blockX()) + Math.abs(a.blockY() - b.blockY()) + Math.abs(a.blockZ() - b.blockZ());
    }

    private static boolean closerThanHorizontally(BlockVec origin, BlockVec pos, double distance) {
        var dx = pos.blockX() - origin.blockX();
        var dz = pos.blockZ() - origin.blockZ();
        return (double) (dx * dx + dz * dz) < distance * distance;
    }

    private static boolean closerThan(BlockVec a, BlockVec b, double distance) {
        var dx = a.blockX() - b.blockX();
        var dy = a.blockY() - b.blockY();
        var dz = a.blockZ() - b.blockZ();
        return (double) (dx * dx + dy * dy + dz * dz) < distance * distance;
    }

    // --- charge cursor -------------------------------------------------------

    private static final class ChargeCursor {
        private BlockVec pos;
        private int charge;
        private int decayDelay = 1;
        private int updateDelay;
        private Set<Direction> facings;

        ChargeCursor(BlockVec pos, int charge) {
            this.pos = pos;
            this.charge = charge;
        }

        private boolean isPosUnreasonable(BlockVec originPos) {
            var dx = this.pos.blockX() - originPos.blockX();
            var dy = this.pos.blockY() - originPos.blockY();
            var dz = this.pos.blockZ() - originPos.blockZ();
            return Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) > MAX_CURSOR_DISTANCE;
        }

        <T extends Block.Getter & Block.Setter> void update(T level, BlockVec originPos, RandomSource random, boolean spreadVeins) {
            if (this.charge <= 0) {
                return;
            }

            if (this.updateDelay > 0) {
                this.updateDelay--;
                return;
            }

            var currentState = level.getBlock(this.pos);
            if (spreadVeins && this.attemptSpreadVein(level, currentState)) {
                if (!currentState.compare(Block.SCULK)) {
                    currentState = level.getBlock(this.pos);
                }
            }

            this.charge = this.attemptUseCharge(level, currentState, originPos, random, spreadVeins);
            if (this.charge <= 0) {
                this.onDischarged(level, currentState);
                return;
            }

            var transferPos = getValidMovementPos(level, this.pos, random);
            if (transferPos != null) {
                this.onDischarged(level, currentState);
                this.pos = transferPos;
                if (!closerThanHorizontally(originPos, this.pos, 15.0)) {
                    this.charge = 0;
                    return;
                }

                currentState = level.getBlock(transferPos);
            }

            if (currentState.compare(Block.SCULK) || currentState.compare(Block.SCULK_VEIN)) {
                this.facings = availableFaces(currentState);
            }

            this.decayDelay = currentState.compare(Block.SCULK) || currentState.compare(Block.SCULK_VEIN)
                    ? 1
                    : Math.max(this.decayDelay - 1, 0);
            this.updateDelay = 1;
        }

        private <T extends Block.Getter & Block.Setter> boolean attemptSpreadVein(T level, Block currentState) {
            if (currentState.compare(Block.SCULK) || currentState.compare(Block.SCULK_VEIN)) {
                return spreadAll(level, this.pos, currentState, DEFAULT_SPREAD_ORDER) > 0;
            }

            return defaultAttemptSpreadVein(level, this.pos, currentState, this.facings);
        }

        private <T extends Block.Getter & Block.Setter> int attemptUseCharge(
                T level, Block currentState, BlockVec originPos, RandomSource random, boolean spreadVeins) {
            if (currentState.compare(Block.SCULK)) {
                return this.sculkAttemptUseCharge(level, originPos, random);
            } else if (currentState.compare(Block.SCULK_VEIN)) {
                return this.veinAttemptUseCharge(level, random, spreadVeins);
            } else {
                return this.decayDelay > 0 ? this.charge : 0;
            }
        }

        private int sculkAttemptUseCharge(Block.Getter level, BlockVec originPos, RandomSource random) {
            if (this.charge == 0 || random.nextInt(CHARGE_DECAY_RATE) != 0) {
                return this.charge;
            }

            var isCloseToCatalyst = closerThan(this.pos, originPos, NO_GROWTH_RADIUS);
            if (!isCloseToCatalyst && canPlaceGrowth(level, this.pos)) {
                if (random.nextInt(GROWTH_SPAWN_COST) < this.charge) {
                    var growthPos = this.pos.add(0, 1, 0);
                    var growthState = randomGrowthState(level, growthPos, random);
                    ((Block.Setter) level).setBlock(growthPos.blockX(), growthPos.blockY(), growthPos.blockZ(), growthState);
                }

                return Math.max(0, this.charge - GROWTH_SPAWN_COST);
            } else {
                if (random.nextInt(ADDITIONAL_DECAY_RATE) != 0) {
                    return this.charge;
                }

                return this.charge - (isCloseToCatalyst ? 1 : decayPenalty(this.pos, originPos, this.charge));
            }
        }

        private <T extends Block.Getter & Block.Setter> int veinAttemptUseCharge(T level, RandomSource random, boolean spreadVeins) {
            if (spreadVeins && attemptPlaceSculk(level, this.pos, random)) {
                return this.charge - 1;
            }

            return random.nextInt(CHARGE_DECAY_RATE) == 0 ? (int) Math.floor(this.charge * 0.5F) : this.charge;
        }

        private <T extends Block.Getter & Block.Setter> void onDischarged(T level, Block currentState) {
            if (currentState.compare(Block.SCULK_VEIN)) {
                dischargeVein(level, currentState, this.pos);
            }
        }
    }

    private static boolean canPlaceGrowth(Block.Getter level, BlockVec pos) {
        var above = pos.add(0, 1, 0);
        var aboveState = level.getBlock(above);
        if (!(aboveState.air() || isWaterSource(aboveState))) {
            return false;
        }

        var growthCount = 0;
        for (var dx = -4; dx <= 4; dx++) {
            for (var dy = 0; dy <= 2; dy++) {
                for (var dz = -4; dz <= 4; dz++) {
                    var candidate = level.getBlock(pos.blockX() + dx, pos.blockY() + dy, pos.blockZ() + dz);
                    if (candidate.compare(Block.SCULK_SENSOR) || candidate.compare(Block.SCULK_SHRIEKER)) {
                        growthCount++;
                        if (growthCount > 2) {
                            return false;
                        }
                    }
                }
            }
        }

        return true;
    }

    private static Block randomGrowthState(Block.Getter level, BlockVec pos, RandomSource random) {
        Block state;
        if (random.nextInt(11) == 0) {
            state = Block.SCULK_SHRIEKER.withProperty("can_summon", "true");
        } else {
            state = Block.SCULK_SENSOR;
        }

        if (state.getProperty("waterlogged") != null && isWaterSource(level.getBlock(pos))) {
            return state.withProperty("waterlogged", "true");
        }

        return state;
    }

    private static int decayPenalty(BlockVec pos, BlockVec originPos, int charge) {
        var dx = pos.blockX() - originPos.blockX();
        var dy = pos.blockY() - originPos.blockY();
        var dz = pos.blockZ() - originPos.blockZ();
        var distance = (float) Math.sqrt((double) (dx * dx + dy * dy + dz * dz));
        var outerDistance = distance - NO_GROWTH_RADIUS;
        var outerDistanceSquared = outerDistance * outerDistance;
        var maxReach = 24 - NO_GROWTH_RADIUS;
        var maxReachSquared = (float) (maxReach * maxReach);
        var distanceFactor = Math.min(1.0F, outerDistanceSquared / maxReachSquared);
        return Math.max(1, (int) (charge * distanceFactor * 0.5F));
    }

    private static <T extends Block.Getter & Block.Setter> BlockVec getValidMovementPos(T level, BlockVec pos, RandomSource random) {
        var offsets = new ArrayList<int[]>(List.of(NON_CORNER_NEIGHBOURS));
        TreeDecorator.shuffle(offsets, random);

        var result = pos;
        for (var offset : offsets) {
            var neighbour = pos.add(offset[0], offset[1], offset[2]);
            var transferee = level.getBlock(neighbour);
            if ((transferee.compare(Block.SCULK) || transferee.compare(Block.SCULK_VEIN))
                    && isMovementUnobstructed(level, pos, neighbour)) {
                result = neighbour;
                if (hasSubstrateAccess(level, transferee, neighbour)) {
                    break;
                }
            }
        }

        return result.equals(pos) ? null : result;
    }

    private static <T extends Block.Getter & Block.Setter> boolean isMovementUnobstructed(T level, BlockVec from, BlockVec to) {
        if (manhattanDistance(from, to) == 1) {
            return true;
        }

        var dx = to.blockX() - from.blockX();
        var dy = to.blockY() - from.blockY();
        var dz = to.blockZ() - from.blockZ();
        var directionX = dx < 0 ? Direction.WEST : Direction.EAST;
        var directionY = dy < 0 ? Direction.DOWN : Direction.UP;
        var directionZ = dz < 0 ? Direction.NORTH : Direction.SOUTH;

        if (dx == 0) {
            return isUnobstructed(level, from, directionY) || isUnobstructed(level, from, directionZ);
        } else if (dy == 0) {
            return isUnobstructed(level, from, directionX) || isUnobstructed(level, from, directionZ);
        } else {
            return isUnobstructed(level, from, directionX) || isUnobstructed(level, from, directionY);
        }
    }

    private static boolean isUnobstructed(Block.Getter level, BlockVec from, Direction direction) {
        var testPos = direction.relative(from);
        return !hasFullFace(level.getBlock(testPos), direction.opposite());
    }
}
