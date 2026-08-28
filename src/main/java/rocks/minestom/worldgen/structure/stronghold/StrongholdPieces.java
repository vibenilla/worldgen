package rocks.minestom.worldgen.structure.stronghold;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code StrongholdPieces}: the procedural corridor, stairs,
 * crossing, library and portal room pieces. Random call order matches
 * vanilla exactly so a seeded {@code WorldgenRandom} reproduces vanilla
 * layouts and decoration block-for-block.
 *
 * <p>Vanilla keeps the piece-weight selection state ({@code currentPieces},
 * {@code imposedPiece}, {@code totalWeight}) in static fields reset via
 * {@code resetPieces()} at the start of every generation attempt. This port
 * instead holds that state on {@link Generator}, one instance per attempt,
 * with identical semantics.
 */
public final class StrongholdPieces {
    static final Block CAVE_AIR = Block.CAVE_AIR;
    private static final int LOWEST_Y_POSITION = 10;

    private StrongholdPieces() {
    }

    /**
     * Vanilla {@code StrongholdStructure.generatePieces}: retries the whole
     * layout, reseeding the piece random with {@code seed + attempt} on
     * every retry, until it both places at least one piece (always true,
     * since the start piece itself is added first) and contains a portal
     * room, then offsets the layout below sea level.
     */
    public static List<StrongholdPiece> generatePieces(long seed, int chunkX, int chunkZ, int seaLevel, int minY) {
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        List<StrongholdPiece> pieces;
        StartPiece startPiece;
        var tries = 0;
        do {
            random.setLargeFeatureSeed(seed + tries, chunkX, chunkZ);
            tries++;

            var generator = new Generator();
            startPiece = new StartPiece(random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
            generator.pieces.add(startPiece);
            startPiece.addChildren(startPiece, generator, random);

            while (!startPiece.pendingChildren.isEmpty()) {
                var pos = random.nextInt(startPiece.pendingChildren.size());
                var piece = startPiece.pendingChildren.remove(pos);
                piece.addChildren(startPiece, generator, random);
            }

            moveBelowSeaLevel(generator.pieces, seaLevel, minY, random);
            pieces = generator.pieces;
        } while (startPiece.portalRoomPiece == null);

        return pieces;
    }

    /**
     * Vanilla {@code StructurePiecesBuilder.moveBelowSeaLevel(seaLevel,
     * minY, random, 10)}: offsets every piece so the layout's top sits
     * randomly below sea level.
     */
    private static void moveBelowSeaLevel(List<StrongholdPiece> pieces, int seaLevel, int minY, RandomSource random) {
        var bounds = boundsOf(pieces);
        var maxTargetY = seaLevel - 10;
        var targetTop = bounds.getYSpan() + minY + 1;
        if (targetTop < maxTargetY) {
            targetTop += random.nextInt(maxTargetY - targetTop);
        }
        var dy = targetTop - bounds.maxY();
        for (var piece : pieces) {
            piece.move(0, dy, 0);
        }
    }

    static BoundingBox boundsOf(List<StrongholdPiece> pieces) {
        var first = pieces.getFirst().boundingBox();
        var bounds = new BoundingBox(first.minX(), first.minY(), first.minZ(), first.maxX(), first.maxY(), first.maxZ());
        for (var index = 1; index < pieces.size(); index++) {
            bounds.encapsulate(pieces.get(index).boundingBox());
        }
        return bounds;
    }

    private enum PieceKind {
        STRAIGHT, PRISON_HALL, LEFT_TURN, RIGHT_TURN, ROOM_CROSSING, STRAIGHT_STAIRS_DOWN,
        STAIRS_DOWN, FIVE_CROSSING, CHEST_CORRIDOR, LIBRARY, PORTAL_ROOM
    }

    private static final class PieceWeight {
        final PieceKind kind;
        final int weight;
        final int maxPlaceCount;
        final int minDepthExclusive;
        int placeCount;

        PieceWeight(PieceKind kind, int weight, int maxPlaceCount) {
            this(kind, weight, maxPlaceCount, 0);
        }

        PieceWeight(PieceKind kind, int weight, int maxPlaceCount, int minDepthExclusive) {
            this.kind = kind;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
            this.minDepthExclusive = minDepthExclusive;
        }

        boolean doPlace(int depth) {
            if (this.maxPlaceCount != 0 && this.placeCount >= this.maxPlaceCount) {
                return false;
            }
            return this.minDepthExclusive == 0 || depth > this.minDepthExclusive;
        }

        boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }

    private static List<PieceWeight> freshWeights() {
        var weights = new ArrayList<PieceWeight>();
        weights.add(new PieceWeight(PieceKind.STRAIGHT, 40, 0));
        weights.add(new PieceWeight(PieceKind.PRISON_HALL, 5, 5));
        weights.add(new PieceWeight(PieceKind.LEFT_TURN, 20, 0));
        weights.add(new PieceWeight(PieceKind.RIGHT_TURN, 20, 0));
        weights.add(new PieceWeight(PieceKind.ROOM_CROSSING, 10, 6));
        weights.add(new PieceWeight(PieceKind.STRAIGHT_STAIRS_DOWN, 5, 5));
        weights.add(new PieceWeight(PieceKind.STAIRS_DOWN, 5, 5));
        weights.add(new PieceWeight(PieceKind.FIVE_CROSSING, 5, 4));
        weights.add(new PieceWeight(PieceKind.CHEST_CORRIDOR, 5, 4));
        weights.add(new PieceWeight(PieceKind.LIBRARY, 10, 2, 4));
        weights.add(new PieceWeight(PieceKind.PORTAL_ROOM, 20, 1, 5));
        return weights;
    }

    /**
     * Per-attempt piece-weight selection state and the piece list itself
     * (vanilla's {@code StructurePieceAccessor}), mirroring the semantics of
     * vanilla's static {@code currentPieces}/{@code imposedPiece}/
     * {@code totalWeight} without sharing state across attempts or threads.
     */
    static final class Generator {
        private static final int MAX_DEPTH = 50;

        final List<StrongholdPiece> pieces = new ArrayList<>();
        private List<PieceWeight> currentPieces = freshWeights();
        private PieceKind imposedPiece;
        private int totalWeight;

        StrongholdPiece findCollisionPiece(BoundingBox box) {
            for (var piece : this.pieces) {
                if (piece.boundingBox.intersects(box)) {
                    return piece;
                }
            }
            return null;
        }

        StrongholdPiece generateAndAddPiece(StartPiece startPiece, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int depth) {
            if (depth > MAX_DEPTH) {
                return null;
            }
            if (Math.abs(footX - startPiece.boundingBox.minX()) > 112
                    || Math.abs(footZ - startPiece.boundingBox.minZ()) > 112) {
                return null;
            }

            var newPiece = this.generatePieceFromSmallDoor(startPiece, random, footX, footY, footZ, direction, depth + 1);
            if (newPiece != null) {
                this.pieces.add(newPiece);
                startPiece.pendingChildren.add(newPiece);
            }
            return newPiece;
        }

        private boolean updatePieceWeight() {
            var hasAnyPieces = false;
            this.totalWeight = 0;
            for (var weight : this.currentPieces) {
                if (weight.maxPlaceCount > 0 && weight.placeCount < weight.maxPlaceCount) {
                    hasAnyPieces = true;
                }
                this.totalWeight += weight.weight;
            }
            return hasAnyPieces;
        }

        private StrongholdPiece generatePieceFromSmallDoor(StartPiece startPiece, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int depth) {
            if (!this.updatePieceWeight()) {
                return null;
            }

            if (this.imposedPiece != null) {
                var piece = createPiece(this.imposedPiece, this, random, footX, footY, footZ, direction, depth);
                this.imposedPiece = null;
                if (piece != null) {
                    return piece;
                }
            }

            for (var attempt = 0; attempt < 5; attempt++) {
                var weightSelection = random.nextInt(this.totalWeight);
                for (var weight : this.currentPieces) {
                    weightSelection -= weight.weight;
                    if (weightSelection < 0) {
                        if (!weight.doPlace(depth) || weight == startPiece.previousPieceWeight) {
                            break;
                        }

                        var piece = createPiece(weight.kind, this, random, footX, footY, footZ, direction, depth);
                        if (piece != null) {
                            weight.placeCount++;
                            startPiece.previousPieceWeight = weight;
                            if (!weight.isValid()) {
                                this.currentPieces.remove(weight);
                            }
                            return piece;
                        }
                    }
                }
            }

            var box = FillerCorridor.findPieceBox(this, random, footX, footY, footZ, direction);
            return box != null && box.minY() > 1 ? new FillerCorridor(depth, box, direction) : null;
        }

        void imposeFiveCrossing() {
            this.imposedPiece = PieceKind.FIVE_CROSSING;
        }
    }

    private static StrongholdPiece createPiece(PieceKind kind, Generator generator, RandomSource random,
            int footX, int footY, int footZ, Direction direction, int depth) {
        return switch (kind) {
            case STRAIGHT -> Straight.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case PRISON_HALL -> PrisonHall.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case LEFT_TURN -> LeftTurn.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case RIGHT_TURN -> RightTurn.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case ROOM_CROSSING -> RoomCrossing.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case STRAIGHT_STAIRS_DOWN ->
                    StraightStairsDown.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case STAIRS_DOWN -> StairsDown.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case FIVE_CROSSING -> FiveCrossing.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case CHEST_CORRIDOR -> ChestCorridor.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case LIBRARY -> Library.createPiece(generator, random, footX, footY, footZ, direction, depth);
            case PORTAL_ROOM -> PortalRoom.createPiece(generator, footX, footY, footZ, direction, depth);
        };
    }

    static boolean isInside(BoundingBox box, int x, int y, int z) {
        return x >= box.minX() && x <= box.maxX()
                && y >= box.minY() && y <= box.maxY()
                && z >= box.minZ() && z <= box.maxZ();
    }

    static BoundingBox orientBox(int footX, int footY, int footZ, int offX, int offY, int offZ,
            int width, int height, int depth, Direction direction) {
        return switch (direction) {
            case NORTH -> new BoundingBox(footX + offX, footY + offY, footZ - depth + 1 + offZ,
                    footX + width - 1 + offX, footY + height - 1 + offY, footZ + offZ);
            case WEST -> new BoundingBox(footX - depth + 1 + offZ, footY + offY, footZ + offX,
                    footX + offZ, footY + height - 1 + offY, footZ + width - 1 + offX);
            case EAST -> new BoundingBox(footX + offZ, footY + offY, footZ + offX,
                    footX + depth - 1 + offZ, footY + height - 1 + offY, footZ + width - 1 + offX);
            default -> new BoundingBox(footX + offX, footY + offY, footZ + offZ,
                    footX + width - 1 + offX, footY + height - 1 + offY, footZ + depth - 1 + offZ);
        };
    }

    static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int width, int height, int depth) {
        return direction == Direction.NORTH || direction == Direction.SOUTH
                ? new BoundingBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1)
                : new BoundingBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
    }

    static Direction randomHorizontalDirection(RandomSource random) {
        return Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
    }

    enum SmallDoorType {
        OPENING, WOOD_DOOR, GRATES, IRON_DOOR
    }

    abstract static class BlockSelector {
        Block next = Block.AIR;

        abstract void next(RandomSource random, int worldX, int worldY, int worldZ, boolean isEdge);
    }

    private static final class SmoothStoneSelector extends BlockSelector {
        @Override
        void next(RandomSource random, int worldX, int worldY, int worldZ, boolean isEdge) {
            if (isEdge) {
                var selection = random.nextFloat();
                if (selection < 0.2F) {
                    this.next = Block.CRACKED_STONE_BRICKS;
                } else if (selection < 0.5F) {
                    this.next = Block.MOSSY_STONE_BRICKS;
                } else if (selection < 0.55F) {
                    this.next = Block.INFESTED_STONE_BRICKS;
                } else {
                    this.next = Block.STONE_BRICKS;
                }
            } else {
                this.next = Block.CAVE_AIR;
            }
        }
    }

    private static final BlockSelector SMOOTH_STONE_SELECTOR = new SmoothStoneSelector();

    public abstract static class StrongholdPiece {
        final int genDepth;
        BoundingBox boundingBox;
        private Direction orientation;
        private boolean mirrorActive;
        private boolean rotateActive;
        SmallDoorType entryDoor = SmallDoorType.OPENING;

        StrongholdPiece(int genDepth, BoundingBox boundingBox) {
            this.genDepth = genDepth;
            this.boundingBox = boundingBox;
        }

        public BoundingBox boundingBox() {
            return this.boundingBox;
        }

        public int genDepth() {
            return this.genDepth;
        }

        public Direction orientation() {
            return this.orientation;
        }

        void move(int dx, int dy, int dz) {
            this.boundingBox = this.boundingBox.moved(dx, dy, dz);
        }

        void setOrientation(Direction orientation) {
            this.orientation = orientation;
            if (orientation == null) {
                this.mirrorActive = false;
                this.rotateActive = false;
                return;
            }
            switch (orientation) {
                case SOUTH -> {
                    this.mirrorActive = true;
                    this.rotateActive = false;
                }
                case WEST -> {
                    this.mirrorActive = true;
                    this.rotateActive = true;
                }
                case EAST -> {
                    this.mirrorActive = false;
                    this.rotateActive = true;
                }
                default -> {
                    this.mirrorActive = false;
                    this.rotateActive = false;
                }
            }
        }

        abstract void addChildren(StartPiece startPiece, Generator generator, RandomSource random);

        abstract void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB);

        int worldX(int x, int z) {
            var orientation = this.orientation;
            if (orientation == null) {
                return x;
            }
            return switch (orientation) {
                case NORTH, SOUTH -> this.boundingBox.minX() + x;
                case WEST -> this.boundingBox.maxX() - z;
                case EAST -> this.boundingBox.minX() + z;
                default -> x;
            };
        }

        int worldY(int y) {
            return this.orientation == null ? y : y + this.boundingBox.minY();
        }

        int worldZ(int x, int z) {
            var orientation = this.orientation;
            if (orientation == null) {
                return z;
            }
            return switch (orientation) {
                case NORTH -> this.boundingBox.maxZ() - z;
                case SOUTH -> this.boundingBox.minZ() + z;
                case WEST, EAST -> this.boundingBox.minZ() + x;
                default -> z;
            };
        }

        void placeBlock(StrongholdLevel level, Block block, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return;
            }
            level.setBlock(wx, wy, wz, this.transform(block));
        }

        Block getBlock(StrongholdLevel level, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return Block.AIR;
            }
            return level.getBlock(wx, wy, wz);
        }

        boolean isInterior(StrongholdLevel level, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y + 1);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return false;
            }
            return wy < level.oceanFloorHeight(wx, wz);
        }

        void generateBox(StrongholdLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
                int x1, int y1, int z1, Block edgeBlock, Block fillBlock, boolean skipAir) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        if (skipAir && this.getBlock(level, x, y, z, chunkBB).air()) {
                            continue;
                        }
                        if (y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1) {
                            this.placeBlock(level, fillBlock, x, y, z, chunkBB);
                        } else {
                            this.placeBlock(level, edgeBlock, x, y, z, chunkBB);
                        }
                    }
                }
            }
        }

        void generateBox(StrongholdLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
                int x1, int y1, int z1, boolean skipAir, RandomSource random, BlockSelector selector) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        if (skipAir && this.getBlock(level, x, y, z, chunkBB).air()) {
                            continue;
                        }
                        selector.next(random, x, y, z, y == y0 || y == y1 || x == x0 || x == x1 || z == z0 || z == z1);
                        this.placeBlock(level, selector.next, x, y, z, chunkBB);
                    }
                }
            }
        }

        void generateMaybeBox(StrongholdLevel level, BoundingBox chunkBB, RandomSource random, float probability,
                int x0, int y0, int z0, int x1, int y1, int z1,
                Block edgeBlock, Block fillBlock, boolean skipAir, boolean hasToBeInside) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        if (random.nextFloat() > probability) {
                            continue;
                        }
                        if (skipAir && this.getBlock(level, x, y, z, chunkBB).air()) {
                            continue;
                        }
                        if (hasToBeInside && !this.isInterior(level, x, y, z, chunkBB)) {
                            continue;
                        }
                        if (y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1) {
                            this.placeBlock(level, fillBlock, x, y, z, chunkBB);
                        } else {
                            this.placeBlock(level, edgeBlock, x, y, z, chunkBB);
                        }
                    }
                }
            }
        }

        void maybeGenerateBlock(StrongholdLevel level, BoundingBox chunkBB, RandomSource random,
                float probability, int x, int y, int z, Block block) {
            if (random.nextFloat() < probability) {
                this.placeBlock(level, block, x, y, z, chunkBB);
            }
        }

        SmallDoorType randomSmallDoor(RandomSource random) {
            return switch (random.nextInt(5)) {
                case 2 -> SmallDoorType.WOOD_DOOR;
                case 3 -> SmallDoorType.GRATES;
                case 4 -> SmallDoorType.IRON_DOOR;
                default -> SmallDoorType.OPENING;
            };
        }

        void generateSmallDoor(StrongholdLevel level, RandomSource random, BoundingBox chunkBB,
                SmallDoorType doorType, int footX, int footY, int footZ) {
            switch (doorType) {
                case OPENING -> this.generateBox(level, chunkBB, footX, footY, footZ,
                        footX + 3 - 1, footY + 3 - 1, footZ, CAVE_AIR, CAVE_AIR, false);
                case WOOD_DOOR -> {
                    this.placeBlock(level, Block.STONE_BRICKS, footX, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 1, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 2, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 2, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 2, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.OAK_DOOR, footX + 1, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.OAK_DOOR.withProperty("half", "upper"), footX + 1, footY + 1, footZ, chunkBB);
                }
                case GRATES -> {
                    this.placeBlock(level, Block.CAVE_AIR, footX + 1, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.CAVE_AIR, footX + 1, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_BARS.withProperty("west", "true"), footX, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_BARS.withProperty("west", "true"), footX, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_BARS.withProperty("east", "true").withProperty("west", "true"),
                            footX, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_BARS.withProperty("east", "true").withProperty("west", "true"),
                            footX + 1, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_BARS.withProperty("east", "true").withProperty("west", "true"),
                            footX + 2, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_BARS.withProperty("east", "true"), footX + 2, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_BARS.withProperty("east", "true"), footX + 2, footY, footZ, chunkBB);
                }
                case IRON_DOOR -> {
                    this.placeBlock(level, Block.STONE_BRICKS, footX, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 1, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 2, footY + 2, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 2, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, footX + 2, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_DOOR, footX + 1, footY, footZ, chunkBB);
                    this.placeBlock(level, Block.IRON_DOOR.withProperty("half", "upper"), footX + 1, footY + 1, footZ, chunkBB);
                    this.placeBlock(level, Block.STONE_BUTTON.withProperty("facing", "north"),
                            footX + 2, footY + 1, footZ + 1, chunkBB);
                    this.placeBlock(level, Block.STONE_BUTTON.withProperty("facing", "south"),
                            footX + 2, footY + 1, footZ - 1, chunkBB);
                }
            }
        }

        StrongholdPiece generateSmallDoorChildForward(StartPiece startPiece, Generator generator, RandomSource random,
                int xOff, int yOff) {
            var orientation = this.orientation();
            if (orientation == null) {
                return null;
            }
            return switch (orientation) {
                case NORTH -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1,
                        orientation, this.genDepth);
                case SOUTH -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1,
                        orientation, this.genDepth);
                case WEST -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff,
                        orientation, this.genDepth);
                case EAST -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff,
                        orientation, this.genDepth);
                default -> null;
            };
        }

        StrongholdPiece generateSmallDoorChildLeft(StartPiece startPiece, Generator generator, RandomSource random,
                int yOff, int zOff) {
            var orientation = this.orientation();
            if (orientation == null) {
                return null;
            }
            return switch (orientation) {
                case NORTH, SOUTH -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff,
                        Direction.WEST, this.genDepth);
                case WEST, EAST -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1,
                        Direction.NORTH, this.genDepth);
                default -> null;
            };
        }

        StrongholdPiece generateSmallDoorChildRight(StartPiece startPiece, Generator generator, RandomSource random,
                int yOff, int zOff) {
            var orientation = this.orientation();
            if (orientation == null) {
                return null;
            }
            return switch (orientation) {
                case NORTH, SOUTH -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff,
                        Direction.EAST, this.genDepth);
                case WEST, EAST -> generator.generateAndAddPiece(startPiece, random,
                        this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1,
                        Direction.SOUTH, this.genDepth);
                default -> null;
            };
        }

        boolean createChest(StrongholdLevel level, BoundingBox chunkBB, RandomSource random, int x, int y, int z) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz) || level.getBlock(wx, wy, wz).compare(Block.CHEST)) {
                return false;
            }
            level.setBlock(wx, wy, wz, reorientChest(level, wx, wy, wz));
            random.nextLong();
            return true;
        }

        private Block transform(Block block) {
            var result = block;
            if (this.mirrorActive) {
                result = mirrorLeftRight(result);
            }
            if (this.rotateActive) {
                result = rotateClockwise(result);
            }
            return result;
        }

        private static Block mirrorLeftRight(Block block) {
            var facing = block.getProperty("facing");
            if (facing != null) {
                var mirrored = switch (facing) {
                    case "north" -> block.withProperty("facing", "south");
                    case "south" -> block.withProperty("facing", "north");
                    default -> block;
                };
                var hinge = mirrored.getProperty("hinge");
                if (hinge != null) {
                    mirrored = mirrored.withProperty("hinge", hinge.equals("left") ? "right" : "left");
                }
                return mirrored;
            }

            var north = block.getProperty("north");
            var south = block.getProperty("south");
            if (north != null && south != null) {
                return block.withProperty("north", south).withProperty("south", north);
            }

            return block;
        }

        private static Block rotateClockwise(Block block) {
            var facing = block.getProperty("facing");
            if (facing != null) {
                return switch (facing) {
                    case "north" -> block.withProperty("facing", "east");
                    case "east" -> block.withProperty("facing", "south");
                    case "south" -> block.withProperty("facing", "west");
                    case "west" -> block.withProperty("facing", "north");
                    default -> block;
                };
            }

            var north = block.getProperty("north");
            if (north != null && block.getProperty("east") != null) {
                var east = block.getProperty("east");
                var south = block.getProperty("south");
                var west = block.getProperty("west");
                return block.withProperty("east", north)
                        .withProperty("south", east)
                        .withProperty("west", south)
                        .withProperty("north", west);
            }

            return block;
        }
    }

    /**
     * Vanilla {@code StructurePiece.reorient}: faces the chest away from its
     * one solid horizontal neighbor, or - with zero or several solid
     * neighbors - locks onto the first of north, south, east, west (starting
     * from the chest's default facing) whose own neighbor is not solid.
     */
    private static Block reorientChest(StrongholdLevel level, int x, int y, int z) {
        Direction solidNeighbor = null;
        for (var direction : Direction.HORIZONTAL) {
            var state = level.getBlock(x + direction.stepX(), y + direction.stepY(), z + direction.stepZ());
            if (state.compare(Block.CHEST)) {
                return Block.CHEST;
            }
            if (state.solid()) {
                if (solidNeighbor != null) {
                    solidNeighbor = null;
                    break;
                }
                solidNeighbor = direction;
            }
        }

        if (solidNeighbor != null) {
            return Block.CHEST.withProperty("facing", solidNeighbor.opposite().serializedName());
        }

        var lockDirection = Direction.NORTH;
        if (level.getBlock(x + lockDirection.stepX(), y, z + lockDirection.stepZ()).solid()) {
            lockDirection = lockDirection.opposite();
        }
        if (level.getBlock(x + lockDirection.stepX(), y, z + lockDirection.stepZ()).solid()) {
            lockDirection = clockwise(lockDirection);
        }
        if (level.getBlock(x + lockDirection.stepX(), y, z + lockDirection.stepZ()).solid()) {
            lockDirection = lockDirection.opposite();
        }
        return Block.CHEST.withProperty("facing", lockDirection.serializedName());
    }

    private static Direction clockwise(Direction direction) {
        var index = Direction.HORIZONTAL.indexOf(direction);
        return Direction.HORIZONTAL.get((index + 1) % Direction.HORIZONTAL.size());
    }

    private static boolean isOkBox(BoundingBox box) {
        return box.minY() > LOWEST_Y_POSITION;
    }

    abstract static class Turn extends StrongholdPiece {
        Turn(int genDepth, BoundingBox boundingBox) {
            super(genDepth, boundingBox);
        }
    }

    static final class ChestCorridor extends StrongholdPiece {
        private boolean hasPlacedChest;

        ChestCorridor(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        static ChestCorridor createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 7, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new ChestCorridor(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            this.generateSmallDoorChildForward(startPiece, generator, random, 1, 1);
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 6, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            this.generateSmallDoor(level, random, chunkBB, SmallDoorType.OPENING, 1, 1, 6);
            this.generateBox(level, chunkBB, 3, 1, 2, 3, 1, 4, Block.STONE_BRICKS, Block.STONE_BRICKS, false);
            this.placeBlock(level, Block.STONE_BRICK_SLAB, 3, 1, 1, chunkBB);
            this.placeBlock(level, Block.STONE_BRICK_SLAB, 3, 1, 5, chunkBB);
            this.placeBlock(level, Block.STONE_BRICK_SLAB, 3, 2, 2, chunkBB);
            this.placeBlock(level, Block.STONE_BRICK_SLAB, 3, 2, 4, chunkBB);

            for (var z = 2; z <= 4; z++) {
                this.placeBlock(level, Block.STONE_BRICK_SLAB, 2, 1, z, chunkBB);
            }

            if (!this.hasPlacedChest && isInside(chunkBB, this.worldX(3, 3), this.worldY(2), this.worldZ(3, 3))) {
                this.hasPlacedChest = true;
                this.createChest(level, chunkBB, random, 3, 2, 3);
            }
        }
    }

    static final class FillerCorridor extends StrongholdPiece {
        private final int steps;

        FillerCorridor(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.steps = direction != Direction.NORTH && direction != Direction.SOUTH
                    ? boundingBox.getXSpan() : boundingBox.getZSpan();
        }

        static BoundingBox findPieceBox(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction) {
            var box = orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 4, direction);
            var collisionPiece = generator.findCollisionPiece(box);
            if (collisionPiece == null) {
                return null;
            }

            if (collisionPiece.boundingBox().minY() == box.minY()) {
                for (var depth = 2; depth >= 1; depth--) {
                    box = orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, depth, direction);
                    if (!collisionPiece.boundingBox().intersects(box)) {
                        return orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, depth + 1, direction);
                    }
                }
            }

            return null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            for (var i = 0; i < this.steps; i++) {
                this.placeBlock(level, Block.STONE_BRICKS, 0, 0, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 1, 0, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 2, 0, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 3, 0, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 4, 0, i, chunkBB);

                for (var y = 1; y <= 3; y++) {
                    this.placeBlock(level, Block.STONE_BRICKS, 0, y, i, chunkBB);
                    this.placeBlock(level, Block.CAVE_AIR, 1, y, i, chunkBB);
                    this.placeBlock(level, Block.CAVE_AIR, 2, y, i, chunkBB);
                    this.placeBlock(level, Block.CAVE_AIR, 3, y, i, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, 4, y, i, chunkBB);
                }

                this.placeBlock(level, Block.STONE_BRICKS, 0, 4, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 1, 4, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 2, 4, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 3, 4, i, chunkBB);
                this.placeBlock(level, Block.STONE_BRICKS, 4, 4, i, chunkBB);
            }
        }
    }

    static final class FiveCrossing extends StrongholdPiece {
        private final boolean leftLow;
        private final boolean leftHigh;
        private final boolean rightLow;
        private final boolean rightHigh;

        FiveCrossing(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.leftLow = random.nextBoolean();
            this.leftHigh = random.nextBoolean();
            this.rightLow = random.nextBoolean();
            this.rightHigh = random.nextInt(3) > 0;
        }

        static FiveCrossing createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -4, -3, 0, 10, 9, 11, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new FiveCrossing(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            var zOffA = 3;
            var zOffB = 5;
            var orientation = this.orientation();
            if (orientation == Direction.WEST || orientation == Direction.NORTH) {
                zOffA = 8 - zOffA;
                zOffB = 8 - zOffB;
            }

            this.generateSmallDoorChildForward(startPiece, generator, random, 5, 1);
            if (this.leftLow) {
                this.generateSmallDoorChildLeft(startPiece, generator, random, zOffA, 1);
            }
            if (this.leftHigh) {
                this.generateSmallDoorChildLeft(startPiece, generator, random, zOffB, 7);
            }
            if (this.rightLow) {
                this.generateSmallDoorChildRight(startPiece, generator, random, zOffA, 1);
            }
            if (this.rightHigh) {
                this.generateSmallDoorChildRight(startPiece, generator, random, zOffB, 7);
            }
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 9, 8, 10, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 4, 3, 0);
            if (this.leftLow) {
                this.generateBox(level, chunkBB, 0, 3, 1, 0, 5, 3, CAVE_AIR, CAVE_AIR, false);
            }
            if (this.rightLow) {
                this.generateBox(level, chunkBB, 9, 3, 1, 9, 5, 3, CAVE_AIR, CAVE_AIR, false);
            }
            if (this.leftHigh) {
                this.generateBox(level, chunkBB, 0, 5, 7, 0, 7, 9, CAVE_AIR, CAVE_AIR, false);
            }
            if (this.rightHigh) {
                this.generateBox(level, chunkBB, 9, 5, 7, 9, 7, 9, CAVE_AIR, CAVE_AIR, false);
            }

            this.generateBox(level, chunkBB, 5, 1, 10, 7, 3, 10, CAVE_AIR, CAVE_AIR, false);
            this.generateBox(level, chunkBB, 1, 2, 1, 8, 2, 6, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 5, 4, 4, 9, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 8, 1, 5, 8, 4, 9, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 4, 7, 3, 4, 9, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 3, 5, 3, 3, 6, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 3, 4, 3, 3, 4, Block.SMOOTH_STONE_SLAB, Block.SMOOTH_STONE_SLAB, false);
            this.generateBox(level, chunkBB, 1, 4, 6, 3, 4, 6, Block.SMOOTH_STONE_SLAB, Block.SMOOTH_STONE_SLAB, false);
            this.generateBox(level, chunkBB, 5, 1, 7, 7, 1, 8, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 5, 1, 9, 7, 1, 9, Block.SMOOTH_STONE_SLAB, Block.SMOOTH_STONE_SLAB, false);
            this.generateBox(level, chunkBB, 5, 2, 7, 7, 2, 7, Block.SMOOTH_STONE_SLAB, Block.SMOOTH_STONE_SLAB, false);
            this.generateBox(level, chunkBB, 4, 5, 7, 4, 5, 9, Block.SMOOTH_STONE_SLAB, Block.SMOOTH_STONE_SLAB, false);
            this.generateBox(level, chunkBB, 8, 5, 7, 8, 5, 9, Block.SMOOTH_STONE_SLAB, Block.SMOOTH_STONE_SLAB, false);
            var doubleSlab = Block.SMOOTH_STONE_SLAB.withProperty("type", "double");
            this.generateBox(level, chunkBB, 5, 5, 7, 7, 5, 9, doubleSlab, doubleSlab, false);
            this.placeBlock(level, Block.WALL_TORCH.withProperty("facing", "south"), 6, 5, 6, chunkBB);
        }
    }

    static final class LeftTurn extends Turn {
        LeftTurn(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        static LeftTurn createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 5, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new LeftTurn(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            var orientation = this.orientation();
            if (orientation != Direction.NORTH && orientation != Direction.EAST) {
                this.generateSmallDoorChildRight(startPiece, generator, random, 1, 1);
            } else {
                this.generateSmallDoorChildLeft(startPiece, generator, random, 1, 1);
            }
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 4, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            var orientation = this.orientation();
            if (orientation != Direction.NORTH && orientation != Direction.EAST) {
                this.generateBox(level, chunkBB, 4, 1, 1, 4, 3, 3, CAVE_AIR, CAVE_AIR, false);
            } else {
                this.generateBox(level, chunkBB, 0, 1, 1, 0, 3, 3, CAVE_AIR, CAVE_AIR, false);
            }
        }
    }

    static final class RightTurn extends Turn {
        RightTurn(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        static RightTurn createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 5, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new RightTurn(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            var orientation = this.orientation();
            if (orientation != Direction.NORTH && orientation != Direction.EAST) {
                this.generateSmallDoorChildLeft(startPiece, generator, random, 1, 1);
            } else {
                this.generateSmallDoorChildRight(startPiece, generator, random, 1, 1);
            }
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 4, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            var orientation = this.orientation();
            if (orientation != Direction.NORTH && orientation != Direction.EAST) {
                this.generateBox(level, chunkBB, 0, 1, 1, 0, 3, 3, CAVE_AIR, CAVE_AIR, false);
            } else {
                this.generateBox(level, chunkBB, 4, 1, 1, 4, 3, 3, CAVE_AIR, CAVE_AIR, false);
            }
        }
    }

    static final class Library extends StrongholdPiece {
        private final boolean isTall;

        Library(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.isTall = boundingBox.getYSpan() > 6;
        }

        static Library createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -4, -1, 0, 14, 11, 15, direction);
            if (!isOkBox(box) || generator.findCollisionPiece(box) != null) {
                box = orientBox(footX, footY, footZ, -4, -1, 0, 14, 6, 15, direction);
                if (!isOkBox(box) || generator.findCollisionPiece(box) != null) {
                    return null;
                }
            }
            return new Library(genDepth, random, box, direction);
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            var currentHeight = this.isTall ? 11 : 6;

            this.generateBox(level, chunkBB, 0, 0, 0, 13, currentHeight - 1, 14, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 4, 1, 0);
            this.generateMaybeBox(level, chunkBB, random, 0.07F, 2, 1, 1, 11, 4, 13, Block.COBWEB, Block.COBWEB, false, false);

            for (var d = 1; d <= 13; d++) {
                if ((d - 1) % 4 == 0) {
                    this.generateBox(level, chunkBB, 1, 1, d, 1, 4, d, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                    this.generateBox(level, chunkBB, 12, 1, d, 12, 4, d, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                    this.placeBlock(level, Block.WALL_TORCH.withProperty("facing", "east"), 2, 3, d, chunkBB);
                    this.placeBlock(level, Block.WALL_TORCH.withProperty("facing", "west"), 11, 3, d, chunkBB);
                    if (this.isTall) {
                        this.generateBox(level, chunkBB, 1, 6, d, 1, 9, d, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                        this.generateBox(level, chunkBB, 12, 6, d, 12, 9, d, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                    }
                } else {
                    this.generateBox(level, chunkBB, 1, 1, d, 1, 4, d, Block.BOOKSHELF, Block.BOOKSHELF, false);
                    this.generateBox(level, chunkBB, 12, 1, d, 12, 4, d, Block.BOOKSHELF, Block.BOOKSHELF, false);
                    if (this.isTall) {
                        this.generateBox(level, chunkBB, 1, 6, d, 1, 9, d, Block.BOOKSHELF, Block.BOOKSHELF, false);
                        this.generateBox(level, chunkBB, 12, 6, d, 12, 9, d, Block.BOOKSHELF, Block.BOOKSHELF, false);
                    }
                }
            }

            for (var dx = 3; dx < 12; dx += 2) {
                this.generateBox(level, chunkBB, 3, 1, dx, 4, 3, dx, Block.BOOKSHELF, Block.BOOKSHELF, false);
                this.generateBox(level, chunkBB, 6, 1, dx, 7, 3, dx, Block.BOOKSHELF, Block.BOOKSHELF, false);
                this.generateBox(level, chunkBB, 9, 1, dx, 10, 3, dx, Block.BOOKSHELF, Block.BOOKSHELF, false);
            }

            if (this.isTall) {
                this.generateBox(level, chunkBB, 1, 5, 1, 3, 5, 13, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                this.generateBox(level, chunkBB, 10, 5, 1, 12, 5, 13, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                this.generateBox(level, chunkBB, 4, 5, 1, 9, 5, 2, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                this.generateBox(level, chunkBB, 4, 5, 12, 9, 5, 13, Block.OAK_PLANKS, Block.OAK_PLANKS, false);
                this.placeBlock(level, Block.OAK_PLANKS, 9, 5, 11, chunkBB);
                this.placeBlock(level, Block.OAK_PLANKS, 8, 5, 11, chunkBB);
                this.placeBlock(level, Block.OAK_PLANKS, 9, 5, 10, chunkBB);

                var weFence = Block.OAK_FENCE.withProperty("west", "true").withProperty("east", "true");
                var nsFence = Block.OAK_FENCE.withProperty("north", "true").withProperty("south", "true");
                this.generateBox(level, chunkBB, 3, 6, 3, 3, 6, 11, nsFence, nsFence, false);
                this.generateBox(level, chunkBB, 10, 6, 3, 10, 6, 9, nsFence, nsFence, false);
                this.generateBox(level, chunkBB, 4, 6, 2, 9, 6, 2, weFence, weFence, false);
                this.generateBox(level, chunkBB, 4, 6, 12, 7, 6, 12, weFence, weFence, false);
                this.placeBlock(level, Block.OAK_FENCE.withProperty("north", "true").withProperty("east", "true"), 3, 6, 2, chunkBB);
                this.placeBlock(level, Block.OAK_FENCE.withProperty("south", "true").withProperty("east", "true"), 3, 6, 12, chunkBB);
                this.placeBlock(level, Block.OAK_FENCE.withProperty("north", "true").withProperty("west", "true"), 10, 6, 2, chunkBB);

                for (var i = 0; i <= 2; i++) {
                    this.placeBlock(level, Block.OAK_FENCE.withProperty("south", "true").withProperty("west", "true"),
                            8 + i, 6, 12 - i, chunkBB);
                    if (i != 2) {
                        this.placeBlock(level, Block.OAK_FENCE.withProperty("north", "true").withProperty("east", "true"),
                                8 + i, 6, 11 - i, chunkBB);
                    }
                }

                var ladder = Block.LADDER.withProperty("facing", "south");
                this.placeBlock(level, ladder, 10, 1, 13, chunkBB);
                this.placeBlock(level, ladder, 10, 2, 13, chunkBB);
                this.placeBlock(level, ladder, 10, 3, 13, chunkBB);
                this.placeBlock(level, ladder, 10, 4, 13, chunkBB);
                this.placeBlock(level, ladder, 10, 5, 13, chunkBB);
                this.placeBlock(level, ladder, 10, 6, 13, chunkBB);
                this.placeBlock(level, ladder, 10, 7, 13, chunkBB);

                var eFence = Block.OAK_FENCE.withProperty("east", "true");
                var wFence = Block.OAK_FENCE.withProperty("west", "true");
                this.placeBlock(level, eFence, 6, 9, 7, chunkBB);
                this.placeBlock(level, wFence, 7, 9, 7, chunkBB);
                this.placeBlock(level, eFence, 6, 8, 7, chunkBB);
                this.placeBlock(level, wFence, 7, 8, 7, chunkBB);
                var nsweFence = nsFence.withProperty("west", "true").withProperty("east", "true");
                this.placeBlock(level, nsweFence, 6, 7, 7, chunkBB);
                this.placeBlock(level, nsweFence, 7, 7, 7, chunkBB);
                this.placeBlock(level, eFence, 5, 7, 7, chunkBB);
                this.placeBlock(level, wFence, 8, 7, 7, chunkBB);
                this.placeBlock(level, eFence.withProperty("north", "true"), 6, 7, 6, chunkBB);
                this.placeBlock(level, eFence.withProperty("south", "true"), 6, 7, 8, chunkBB);
                this.placeBlock(level, wFence.withProperty("north", "true"), 7, 7, 6, chunkBB);
                this.placeBlock(level, wFence.withProperty("south", "true"), 7, 7, 8, chunkBB);
                this.placeBlock(level, Block.TORCH, 5, 8, 7, chunkBB);
                this.placeBlock(level, Block.TORCH, 8, 8, 7, chunkBB);
                this.placeBlock(level, Block.TORCH, 6, 8, 6, chunkBB);
                this.placeBlock(level, Block.TORCH, 6, 8, 8, chunkBB);
                this.placeBlock(level, Block.TORCH, 7, 8, 6, chunkBB);
                this.placeBlock(level, Block.TORCH, 7, 8, 8, chunkBB);
            }

            this.createChest(level, chunkBB, random, 3, 3, 5);
            if (this.isTall) {
                this.placeBlock(level, CAVE_AIR, 12, 9, 1, chunkBB);
                this.createChest(level, chunkBB, random, 12, 8, 1);
            }
        }
    }

    static final class PortalRoom extends StrongholdPiece {
        private boolean hasPlacedSpawner;

        PortalRoom(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        static PortalRoom createPiece(Generator generator, int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -4, -1, 0, 11, 8, 16, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new PortalRoom(genDepth, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            if (startPiece != null) {
                startPiece.portalRoomPiece = this;
            }
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 10, 7, 15, false, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, SmallDoorType.GRATES, 4, 1, 0);
            this.generateBox(level, chunkBB, 1, 6, 1, 1, 6, 14, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 9, 6, 1, 9, 6, 14, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 2, 6, 1, 8, 6, 2, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 2, 6, 14, 8, 6, 14, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 1, 1, 2, 1, 4, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 8, 1, 1, 9, 1, 4, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 1, 1, 1, 1, 1, 3, Block.LAVA, Block.LAVA, false);
            this.generateBox(level, chunkBB, 9, 1, 1, 9, 1, 3, Block.LAVA, Block.LAVA, false);
            this.generateBox(level, chunkBB, 3, 1, 8, 7, 1, 12, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 9, 6, 1, 11, Block.LAVA, Block.LAVA, false);

            var nsBars = Block.IRON_BARS.withProperty("north", "true").withProperty("south", "true");
            var weBars = Block.IRON_BARS.withProperty("west", "true").withProperty("east", "true");

            for (var z = 3; z < 14; z += 2) {
                this.generateBox(level, chunkBB, 0, 3, z, 0, 4, z, nsBars, nsBars, false);
                this.generateBox(level, chunkBB, 10, 3, z, 10, 4, z, nsBars, nsBars, false);
            }
            for (var x = 2; x < 9; x += 2) {
                this.generateBox(level, chunkBB, x, 3, 15, x, 4, 15, weBars, weBars, false);
            }

            var stairsState = Block.STONE_BRICK_STAIRS.withProperty("facing", "north");
            this.generateBox(level, chunkBB, 4, 1, 5, 6, 1, 7, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 2, 6, 6, 2, 7, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 3, 7, 6, 3, 7, false, random, SMOOTH_STONE_SELECTOR);

            for (var x = 4; x <= 6; x++) {
                this.placeBlock(level, stairsState, x, 1, 4, chunkBB);
                this.placeBlock(level, stairsState, x, 2, 5, chunkBB);
                this.placeBlock(level, stairsState, x, 3, 6, chunkBB);
            }

            var northFrame = Block.END_PORTAL_FRAME.withProperty("facing", "north");
            var southFrame = Block.END_PORTAL_FRAME.withProperty("facing", "south");
            var eastFrame = Block.END_PORTAL_FRAME.withProperty("facing", "east");
            var westFrame = Block.END_PORTAL_FRAME.withProperty("facing", "west");
            var allEyes = true;
            var eyes = new boolean[12];

            for (var i = 0; i < eyes.length; i++) {
                eyes[i] = random.nextFloat() > 0.9F;
                allEyes &= eyes[i];
            }

            this.placeBlock(level, northFrame.withProperty("eye", String.valueOf(eyes[0])), 4, 3, 8, chunkBB);
            this.placeBlock(level, northFrame.withProperty("eye", String.valueOf(eyes[1])), 5, 3, 8, chunkBB);
            this.placeBlock(level, northFrame.withProperty("eye", String.valueOf(eyes[2])), 6, 3, 8, chunkBB);
            this.placeBlock(level, southFrame.withProperty("eye", String.valueOf(eyes[3])), 4, 3, 12, chunkBB);
            this.placeBlock(level, southFrame.withProperty("eye", String.valueOf(eyes[4])), 5, 3, 12, chunkBB);
            this.placeBlock(level, southFrame.withProperty("eye", String.valueOf(eyes[5])), 6, 3, 12, chunkBB);
            this.placeBlock(level, eastFrame.withProperty("eye", String.valueOf(eyes[6])), 3, 3, 9, chunkBB);
            this.placeBlock(level, eastFrame.withProperty("eye", String.valueOf(eyes[7])), 3, 3, 10, chunkBB);
            this.placeBlock(level, eastFrame.withProperty("eye", String.valueOf(eyes[8])), 3, 3, 11, chunkBB);
            this.placeBlock(level, westFrame.withProperty("eye", String.valueOf(eyes[9])), 7, 3, 9, chunkBB);
            this.placeBlock(level, westFrame.withProperty("eye", String.valueOf(eyes[10])), 7, 3, 10, chunkBB);
            this.placeBlock(level, westFrame.withProperty("eye", String.valueOf(eyes[11])), 7, 3, 11, chunkBB);

            if (allEyes) {
                var portal = Block.END_PORTAL;
                this.placeBlock(level, portal, 4, 3, 9, chunkBB);
                this.placeBlock(level, portal, 5, 3, 9, chunkBB);
                this.placeBlock(level, portal, 6, 3, 9, chunkBB);
                this.placeBlock(level, portal, 4, 3, 10, chunkBB);
                this.placeBlock(level, portal, 5, 3, 10, chunkBB);
                this.placeBlock(level, portal, 6, 3, 10, chunkBB);
                this.placeBlock(level, portal, 4, 3, 11, chunkBB);
                this.placeBlock(level, portal, 5, 3, 11, chunkBB);
                this.placeBlock(level, portal, 6, 3, 11, chunkBB);
            }

            if (!this.hasPlacedSpawner) {
                var wx = this.worldX(5, 6);
                var wy = this.worldY(3);
                var wz = this.worldZ(5, 6);
                if (isInside(chunkBB, wx, wy, wz)) {
                    this.hasPlacedSpawner = true;
                    level.setBlock(wx, wy, wz, Block.SPAWNER);
                }
            }
        }
    }

    static final class PrisonHall extends StrongholdPiece {
        PrisonHall(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        static PrisonHall createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -1, 0, 9, 5, 11, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new PrisonHall(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            this.generateSmallDoorChildForward(startPiece, generator, random, 1, 1);
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 8, 4, 10, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            this.generateBox(level, chunkBB, 1, 1, 10, 3, 3, 10, CAVE_AIR, CAVE_AIR, false);
            this.generateBox(level, chunkBB, 4, 1, 1, 4, 3, 1, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 3, 4, 3, 3, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 7, 4, 3, 7, false, random, SMOOTH_STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 1, 9, 4, 3, 9, false, random, SMOOTH_STONE_SELECTOR);

            for (var y = 1; y <= 3; y++) {
                this.placeBlock(level, Block.IRON_BARS.withProperty("north", "true").withProperty("south", "true"),
                        4, y, 4, chunkBB);
                this.placeBlock(level, Block.IRON_BARS.withProperty("north", "true").withProperty("south", "true")
                        .withProperty("east", "true"), 4, y, 5, chunkBB);
                this.placeBlock(level, Block.IRON_BARS.withProperty("north", "true").withProperty("south", "true"),
                        4, y, 6, chunkBB);
                this.placeBlock(level, Block.IRON_BARS.withProperty("west", "true").withProperty("east", "true"),
                        5, y, 5, chunkBB);
                this.placeBlock(level, Block.IRON_BARS.withProperty("west", "true").withProperty("east", "true"),
                        6, y, 5, chunkBB);
                this.placeBlock(level, Block.IRON_BARS.withProperty("west", "true").withProperty("east", "true"),
                        7, y, 5, chunkBB);
            }

            this.placeBlock(level, Block.IRON_BARS.withProperty("north", "true").withProperty("south", "true"), 4, 3, 2, chunkBB);
            this.placeBlock(level, Block.IRON_BARS.withProperty("north", "true").withProperty("south", "true"), 4, 3, 8, chunkBB);

            var doorBottom = Block.IRON_DOOR.withProperty("facing", "west");
            var doorTop = Block.IRON_DOOR.withProperty("facing", "west").withProperty("half", "upper");
            this.placeBlock(level, doorBottom, 4, 1, 2, chunkBB);
            this.placeBlock(level, doorTop, 4, 2, 2, chunkBB);
            this.placeBlock(level, doorBottom, 4, 1, 8, chunkBB);
            this.placeBlock(level, doorTop, 4, 2, 8, chunkBB);
        }
    }

    static final class RoomCrossing extends StrongholdPiece {
        private final int type;

        RoomCrossing(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.type = random.nextInt(5);
        }

        static RoomCrossing createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -4, -1, 0, 11, 7, 11, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new RoomCrossing(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            this.generateSmallDoorChildForward(startPiece, generator, random, 4, 1);
            this.generateSmallDoorChildLeft(startPiece, generator, random, 1, 4);
            this.generateSmallDoorChildRight(startPiece, generator, random, 1, 4);
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 10, 6, 10, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 4, 1, 0);
            this.generateBox(level, chunkBB, 4, 1, 10, 6, 3, 10, CAVE_AIR, CAVE_AIR, false);
            this.generateBox(level, chunkBB, 0, 1, 4, 0, 3, 6, CAVE_AIR, CAVE_AIR, false);
            this.generateBox(level, chunkBB, 10, 1, 4, 10, 3, 6, CAVE_AIR, CAVE_AIR, false);

            switch (this.type) {
                case 0 -> {
                    this.placeBlock(level, Block.STONE_BRICKS, 5, 1, 5, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, 5, 2, 5, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, 5, 3, 5, chunkBB);
                    this.placeBlock(level, Block.WALL_TORCH.withProperty("facing", "west"), 4, 3, 5, chunkBB);
                    this.placeBlock(level, Block.WALL_TORCH.withProperty("facing", "east"), 6, 3, 5, chunkBB);
                    this.placeBlock(level, Block.WALL_TORCH.withProperty("facing", "south"), 5, 3, 4, chunkBB);
                    this.placeBlock(level, Block.WALL_TORCH.withProperty("facing", "north"), 5, 3, 6, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 4, 1, 4, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 4, 1, 5, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 4, 1, 6, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 6, 1, 4, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 6, 1, 5, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 6, 1, 6, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 5, 1, 4, chunkBB);
                    this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 5, 1, 6, chunkBB);
                }
                case 1 -> {
                    for (var i = 0; i < 5; i++) {
                        this.placeBlock(level, Block.STONE_BRICKS, 3, 1, 3 + i, chunkBB);
                        this.placeBlock(level, Block.STONE_BRICKS, 7, 1, 3 + i, chunkBB);
                        this.placeBlock(level, Block.STONE_BRICKS, 3 + i, 1, 3, chunkBB);
                        this.placeBlock(level, Block.STONE_BRICKS, 3 + i, 1, 7, chunkBB);
                    }
                    this.placeBlock(level, Block.STONE_BRICKS, 5, 1, 5, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, 5, 2, 5, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, 5, 3, 5, chunkBB);
                    this.placeBlock(level, Block.WATER, 5, 4, 5, chunkBB);
                }
                case 2 -> {
                    for (var z = 1; z <= 9; z++) {
                        this.placeBlock(level, Block.COBBLESTONE, 1, 3, z, chunkBB);
                        this.placeBlock(level, Block.COBBLESTONE, 9, 3, z, chunkBB);
                    }
                    for (var x = 1; x <= 9; x++) {
                        this.placeBlock(level, Block.COBBLESTONE, x, 3, 1, chunkBB);
                        this.placeBlock(level, Block.COBBLESTONE, x, 3, 9, chunkBB);
                    }
                    this.placeBlock(level, Block.COBBLESTONE, 5, 1, 4, chunkBB);
                    this.placeBlock(level, Block.COBBLESTONE, 5, 1, 6, chunkBB);
                    this.placeBlock(level, Block.COBBLESTONE, 5, 3, 4, chunkBB);
                    this.placeBlock(level, Block.COBBLESTONE, 5, 3, 6, chunkBB);
                    this.placeBlock(level, Block.COBBLESTONE, 4, 1, 5, chunkBB);
                    this.placeBlock(level, Block.COBBLESTONE, 6, 1, 5, chunkBB);
                    this.placeBlock(level, Block.COBBLESTONE, 4, 3, 5, chunkBB);
                    this.placeBlock(level, Block.COBBLESTONE, 6, 3, 5, chunkBB);

                    for (var y = 1; y <= 3; y++) {
                        this.placeBlock(level, Block.COBBLESTONE, 4, y, 4, chunkBB);
                        this.placeBlock(level, Block.COBBLESTONE, 6, y, 4, chunkBB);
                        this.placeBlock(level, Block.COBBLESTONE, 4, y, 6, chunkBB);
                        this.placeBlock(level, Block.COBBLESTONE, 6, y, 6, chunkBB);
                    }

                    this.placeBlock(level, Block.WALL_TORCH, 5, 3, 5, chunkBB);

                    for (var z = 2; z <= 8; z++) {
                        this.placeBlock(level, Block.OAK_PLANKS, 2, 3, z, chunkBB);
                        this.placeBlock(level, Block.OAK_PLANKS, 3, 3, z, chunkBB);
                        if (z <= 3 || z >= 7) {
                            this.placeBlock(level, Block.OAK_PLANKS, 4, 3, z, chunkBB);
                            this.placeBlock(level, Block.OAK_PLANKS, 5, 3, z, chunkBB);
                            this.placeBlock(level, Block.OAK_PLANKS, 6, 3, z, chunkBB);
                        }
                        this.placeBlock(level, Block.OAK_PLANKS, 7, 3, z, chunkBB);
                        this.placeBlock(level, Block.OAK_PLANKS, 8, 3, z, chunkBB);
                    }

                    var ladder = Block.LADDER.withProperty("facing", "west");
                    this.placeBlock(level, ladder, 9, 1, 3, chunkBB);
                    this.placeBlock(level, ladder, 9, 2, 3, chunkBB);
                    this.placeBlock(level, ladder, 9, 3, 3, chunkBB);
                    this.createChest(level, chunkBB, random, 3, 4, 8);
                }
                default -> {
                }
            }
        }
    }

    static class StairsDown extends StrongholdPiece {
        private final boolean isSource;

        StairsDown(int genDepth, int west, int north, Direction direction) {
            super(genDepth, makeBoundingBox(west, 64, north, direction, 5, 11, 5));
            this.isSource = true;
            this.setOrientation(direction);
            this.entryDoor = SmallDoorType.OPENING;
        }

        StairsDown(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.isSource = false;
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        static StairsDown createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -7, 0, 5, 11, 5, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new StairsDown(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            if (this.isSource) {
                generator.imposeFiveCrossing();
            }
            this.generateSmallDoorChildForward(startPiece, generator, random, 1, 1);
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 10, 4, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 7, 0);
            this.generateSmallDoor(level, random, chunkBB, SmallDoorType.OPENING, 1, 1, 4);
            this.placeBlock(level, Block.STONE_BRICKS, 2, 6, 1, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 1, 5, 1, chunkBB);
            this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 1, 6, 1, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 1, 5, 2, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 1, 4, 3, chunkBB);
            this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 1, 5, 3, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 2, 4, 3, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 3, 3, 3, chunkBB);
            this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 3, 4, 3, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 3, 3, 2, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 3, 2, 1, chunkBB);
            this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 3, 3, 1, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 2, 2, 1, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 1, 1, 1, chunkBB);
            this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 1, 2, 1, chunkBB);
            this.placeBlock(level, Block.STONE_BRICKS, 1, 1, 2, chunkBB);
            this.placeBlock(level, Block.SMOOTH_STONE_SLAB, 1, 1, 3, chunkBB);
        }
    }

    static final class StartPiece extends StairsDown {
        private PieceWeight previousPieceWeight;
        PortalRoom portalRoomPiece;
        final List<StrongholdPiece> pendingChildren = new ArrayList<>();

        StartPiece(RandomSource random, int west, int north) {
            super(0, west, north, randomHorizontalDirection(random));
        }
    }

    static final class Straight extends StrongholdPiece {
        private final boolean leftChild;
        private final boolean rightChild;

        Straight(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
            this.leftChild = random.nextInt(2) == 0;
            this.rightChild = random.nextInt(2) == 0;
        }

        static Straight createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -1, 0, 5, 5, 7, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new Straight(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            this.generateSmallDoorChildForward(startPiece, generator, random, 1, 1);
            if (this.leftChild) {
                this.generateSmallDoorChildLeft(startPiece, generator, random, 1, 2);
            }
            if (this.rightChild) {
                this.generateSmallDoorChildRight(startPiece, generator, random, 1, 2);
            }
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 4, 6, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 1, 0);
            this.generateSmallDoor(level, random, chunkBB, SmallDoorType.OPENING, 1, 1, 6);
            var eastTorch = Block.WALL_TORCH.withProperty("facing", "east");
            var westTorch = Block.WALL_TORCH.withProperty("facing", "west");
            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 1, 2, 1, eastTorch);
            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 3, 2, 1, westTorch);
            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 1, 2, 5, eastTorch);
            this.maybeGenerateBlock(level, chunkBB, random, 0.1F, 3, 2, 5, westTorch);
            if (this.leftChild) {
                this.generateBox(level, chunkBB, 0, 1, 2, 0, 3, 4, CAVE_AIR, CAVE_AIR, false);
            }
            if (this.rightChild) {
                this.generateBox(level, chunkBB, 4, 1, 2, 4, 3, 4, CAVE_AIR, CAVE_AIR, false);
            }
        }
    }

    static final class StraightStairsDown extends StrongholdPiece {
        StraightStairsDown(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.entryDoor = this.randomSmallDoor(random);
        }

        static StraightStairsDown createPiece(Generator generator, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -7, 0, 5, 11, 8, direction);
            return isOkBox(box) && generator.findCollisionPiece(box) == null
                    ? new StraightStairsDown(genDepth, random, box, direction) : null;
        }

        @Override
        void addChildren(StartPiece startPiece, Generator generator, RandomSource random) {
            this.generateSmallDoorChildForward(startPiece, generator, random, 1, 1);
        }

        @Override
        void postProcess(StrongholdLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 10, 7, true, random, SMOOTH_STONE_SELECTOR);
            this.generateSmallDoor(level, random, chunkBB, this.entryDoor, 1, 7, 0);
            this.generateSmallDoor(level, random, chunkBB, SmallDoorType.OPENING, 1, 1, 7);
            var stairs = Block.COBBLESTONE_STAIRS.withProperty("facing", "south");

            for (var i = 0; i < 6; i++) {
                this.placeBlock(level, stairs, 1, 6 - i, 1 + i, chunkBB);
                this.placeBlock(level, stairs, 2, 6 - i, 1 + i, chunkBB);
                this.placeBlock(level, stairs, 3, 6 - i, 1 + i, chunkBB);
                if (i < 5) {
                    this.placeBlock(level, Block.STONE_BRICKS, 1, 5 - i, 1 + i, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, 2, 5 - i, 1 + i, chunkBB);
                    this.placeBlock(level, Block.STONE_BRICKS, 3, 5 - i, 1 + i, chunkBB);
                }
            }
        }
    }
}
