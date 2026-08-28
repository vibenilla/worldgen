package rocks.minestom.worldgen.structure.fortress;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code NetherFortressPieces}: the procedural bridge and
 * castle pieces that make up a nether fortress. Random call order matches
 * vanilla exactly so a seeded {@code WorldgenRandom} reproduces vanilla
 * layouts and decoration block-for-block.
 *
 * <p>Two deviations from vanilla, both noted at their call sites: chests are
 * placed without loot table NBT, and the monster-throne / castle-entrance
 * mob spawners are placed without spawn-potential NBT.
 */
public final class NetherFortressPieces {
    private static final int MAX_DEPTH = 30;
    private static final int MAGIC_START_Y = 64;
    private static final int MAX_DISTANCE_FROM_START = 112;

    private NetherFortressPieces() {
    }

    /**
     * Vanilla {@code NetherFortressStructure.generatePieces}: builds the
     * start piece, processes its pending children breadth over a random
     * shuffled queue, then centers the whole structure inside [48, 70].
     */
    public static List<FortressPiece> generatePieces(RandomSource random, int chunkX, int chunkZ) {
        var start = new StartPiece(random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
        var pieces = new ArrayList<FortressPiece>();
        pieces.add(start);
        start.addChildren(start, pieces, random);

        var pendingChildren = start.pendingChildren;
        while (!pendingChildren.isEmpty()) {
            var index = random.nextInt(pendingChildren.size());
            var piece = pendingChildren.remove(index);
            piece.addChildren(start, pieces, random);
        }

        moveInsideHeights(pieces, random, 48, 70);
        return pieces;
    }

    static BoundingBox boundsOf(List<FortressPiece> pieces) {
        var first = pieces.getFirst().boundingBox;
        var bounds = new BoundingBox(first.minX(), first.minY(), first.minZ(), first.maxX(), first.maxY(), first.maxZ());
        for (var index = 1; index < pieces.size(); index++) {
            bounds.encapsulate(pieces.get(index).boundingBox);
        }
        return bounds;
    }

    /** Vanilla {@code StructurePiecesBuilder.moveInsideHeights}. */
    private static void moveInsideHeights(List<FortressPiece> pieces, RandomSource random, int lowestAllowed, int highestAllowed) {
        var bounds = boundsOf(pieces);
        var heightSpan = highestAllowed - lowestAllowed + 1 - bounds.getYSpan();
        var y0 = heightSpan > 1 ? lowestAllowed + random.nextInt(heightSpan) : lowestAllowed;
        var dy = y0 - bounds.minY();
        for (var piece : pieces) {
            piece.move(dy);
        }
    }

    private static boolean isInside(BoundingBox box, int x, int y, int z) {
        return x >= box.minX() && x <= box.maxX()
                && y >= box.minY() && y <= box.maxY()
                && z >= box.minZ() && z <= box.maxZ();
    }

    private static Block fence(String... connections) {
        var block = Block.NETHER_BRICK_FENCE;
        for (var connection : connections) {
            block = block.withProperty(connection, "true");
        }
        return block;
    }

    private static Block stairs(String facing) {
        return Block.NETHER_BRICK_STAIRS.withProperty("facing", facing);
    }

    private static FortressPiece findCollisionPiece(List<FortressPiece> pieces, BoundingBox box) {
        for (var piece : pieces) {
            if (piece.boundingBox.intersects(box)) {
                return piece;
            }
        }
        return null;
    }

    private static boolean isOkBox(BoundingBox box) {
        return box.minY() > 10;
    }

    /** Vanilla {@code BoundingBox.orientBox}. */
    private static BoundingBox orientBox(int footX, int footY, int footZ, int offX, int offY, int offZ,
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

    /** Vanilla {@code StructurePiece.makeBoundingBox}. */
    private static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int width, int height, int depth) {
        return direction == Direction.NORTH || direction == Direction.SOUTH
                ? new BoundingBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1)
                : new BoundingBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
    }

    private interface PieceFactory {
        FortressPiece create(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth);
    }

    private static final class PieceWeight {
        final PieceFactory factory;
        final int weight;
        final int maxPlaceCount;
        final boolean allowInRow;
        int placeCount;

        PieceWeight(PieceFactory factory, int weight, int maxPlaceCount) {
            this(factory, weight, maxPlaceCount, false);
        }

        PieceWeight(PieceFactory factory, int weight, int maxPlaceCount, boolean allowInRow) {
            this.factory = factory;
            this.weight = weight;
            this.maxPlaceCount = maxPlaceCount;
            this.allowInRow = allowInRow;
        }

        boolean doPlace(int depth) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }

    private static List<PieceWeight> bridgePieceWeights() {
        var weights = new ArrayList<PieceWeight>();
        weights.add(new PieceWeight(BridgeStraight::createPiece, 30, 0, true));
        weights.add(new PieceWeight(BridgeCrossing::createPiece, 10, 4));
        weights.add(new PieceWeight(RoomCrossing::createPiece, 10, 4));
        weights.add(new PieceWeight(StairsRoom::createPiece, 10, 3));
        weights.add(new PieceWeight(MonsterThrone::createPiece, 5, 2));
        weights.add(new PieceWeight(CastleEntrance::createPiece, 5, 1));
        return weights;
    }

    private static List<PieceWeight> castlePieceWeights() {
        var weights = new ArrayList<PieceWeight>();
        weights.add(new PieceWeight(CastleSmallCorridorPiece::createPiece, 25, 0, true));
        weights.add(new PieceWeight(CastleSmallCorridorCrossingPiece::createPiece, 15, 5));
        weights.add(new PieceWeight(CastleSmallCorridorRightTurnPiece::createPiece, 5, 10));
        weights.add(new PieceWeight(CastleSmallCorridorLeftTurnPiece::createPiece, 5, 10));
        weights.add(new PieceWeight(CastleCorridorStairsPiece::createPiece, 10, 3, true));
        weights.add(new PieceWeight(CastleCorridorTBalconyPiece::createPiece, 7, 2));
        weights.add(new PieceWeight(CastleStalkRoom::createPiece, 5, 2));
        return weights;
    }

    public abstract static class FortressPiece {
        final int genDepth;
        BoundingBox boundingBox;
        private Direction orientation;
        private boolean mirrorLeftRight;
        private boolean rotateClockwise;

        FortressPiece(int genDepth, BoundingBox boundingBox) {
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
            return this.getOrientation();
        }

        void move(int dy) {
            this.boundingBox = this.boundingBox.moved(0, dy, 0);
        }

        void setOrientation(Direction orientation) {
            this.orientation = orientation;
            if (orientation == null) {
                this.mirrorLeftRight = false;
                this.rotateClockwise = false;
                return;
            }
            switch (orientation) {
                case SOUTH -> {
                    this.mirrorLeftRight = true;
                    this.rotateClockwise = false;
                }
                case WEST -> {
                    this.mirrorLeftRight = true;
                    this.rotateClockwise = true;
                }
                case EAST -> {
                    this.mirrorLeftRight = false;
                    this.rotateClockwise = true;
                }
                default -> {
                    this.mirrorLeftRight = false;
                    this.rotateClockwise = false;
                }
            }
        }

        Direction getOrientation() {
            return this.orientation;
        }

        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
        }

        abstract void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB);

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

        BlockVec worldPos(int x, int y, int z) {
            return new BlockVec(this.worldX(x, z), this.worldY(y), this.worldZ(x, z));
        }

        void placeBlock(FortressLevel level, Block block, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return;
            }
            level.setBlock(wx, wy, wz, this.transform(block));
        }

        Block getBlock(FortressLevel level, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return Block.AIR;
            }
            return level.getBlock(wx, wy, wz);
        }

        void generateBox(FortressLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
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

        void fillColumnDown(FortressLevel level, Block block, int x, int startY, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(startY);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return;
            }
            var y = wy;
            while (isReplaceableByStructures(level.getBlock(wx, y, wz)) && y > level.minY() + 1) {
                level.setBlock(wx, y, wz, block);
                y--;
            }
        }

        private static boolean isReplaceableByStructures(Block block) {
            return block.air() || block.liquid() || block.compare(Block.GLOW_LICHEN)
                    || block.compare(Block.SEAGRASS) || block.compare(Block.TALL_SEAGRASS);
        }

        /**
         * Vanilla {@code StructurePiece.createChest}: places a chest whose
         * facing is oriented away from a single solid neighbor (or toward an
         * adjacent chest to pair as a double chest), without loot table NBT.
         */
        void createChest(FortressLevel level, BoundingBox chunkBB, RandomSource random, int x, int y, int z) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz) || level.getBlock(wx, wy, wz).compare(Block.CHEST)) {
                return;
            }
            level.setBlock(wx, wy, wz, this.reorientChest(level, wx, wy, wz));
            random.nextLong(); // vanilla: chest loot table seed (loot NBT omitted)
        }

        private Block reorientChest(FortressLevel level, int wx, int wy, int wz) {
            Direction solidNeighbor = null;
            for (var direction : Direction.HORIZONTAL) {
                var neighbor = level.getBlock(wx + direction.stepX(), wy, wz + direction.stepZ());
                if (neighbor.compare(Block.CHEST)) {
                    return Block.CHEST;
                }
                if (isSolidRender(neighbor)) {
                    if (solidNeighbor != null) {
                        solidNeighbor = null;
                        break;
                    }
                    solidNeighbor = direction;
                }
            }
            var facing = solidNeighbor != null ? solidNeighbor.opposite() : Direction.NORTH;
            return Block.CHEST.withProperty("facing", facing.serializedName());
        }

        private static boolean isSolidRender(Block block) {
            return block.solid() && !block.compare(Block.NETHER_BRICK_FENCE);
        }

        private Block transform(Block block) {
            var result = block;
            if (this.mirrorLeftRight) {
                result = mirrorLeftRight(result);
            }
            if (this.rotateClockwise) {
                result = rotateClockwise(result);
            }
            return result;
        }

        private static Block mirrorLeftRight(Block block) {
            var facing = block.getProperty("facing");
            if (facing != null) {
                return switch (facing) {
                    case "north" -> block.withProperty("facing", "south");
                    case "south" -> block.withProperty("facing", "north");
                    default -> block;
                };
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

        /**
         * Vanilla {@code NetherBridgePiece.updatePieceWeight}: total weight
         * of pieces that still have placements left, or -1 if none remain.
         */
        private static int updatePieceWeight(List<PieceWeight> currentPieces) {
            var hasAny = false;
            var total = 0;
            for (var piece : currentPieces) {
                if (piece.maxPlaceCount > 0 && piece.placeCount < piece.maxPlaceCount) {
                    hasAny = true;
                }
                total += piece.weight;
            }
            return hasAny ? total : -1;
        }

        /**
         * Vanilla {@code NetherBridgePiece.generatePiece}: weighted piece
         * selection with up to 5 attempts, cascading through the remaining
         * piece list in order once a weight bracket is entered but fails
         * (this mirrors vanilla's {@code weightSelection} never being reset
         * to non-negative after the first successful bracket match).
         */
        private FortressPiece generatePiece(StartPiece startPiece, List<PieceWeight> currentPieces,
                List<FortressPiece> pieces, RandomSource random, int footX, int footY, int footZ,
                Direction direction, int depth) {
            var totalWeight = updatePieceWeight(currentPieces);
            var doStuff = totalWeight > 0 && depth <= MAX_DEPTH;
            var numAttempts = 0;

            while (numAttempts < 5 && doStuff) {
                numAttempts++;
                var weightSelection = random.nextInt(totalWeight);

                for (var piece : currentPieces) {
                    weightSelection -= piece.weight;
                    if (weightSelection < 0) {
                        if (!piece.doPlace(depth) || (piece == startPiece.previousPiece && !piece.allowInRow)) {
                            break;
                        }

                        var structurePiece = piece.factory.create(pieces, random, footX, footY, footZ, direction, depth);
                        if (structurePiece != null) {
                            piece.placeCount++;
                            startPiece.previousPiece = piece;
                            if (!piece.isValid()) {
                                currentPieces.remove(piece);
                            }
                            return structurePiece;
                        }
                    }
                }
            }

            return BridgeEndFiller.createPiece(pieces, random, footX, footY, footZ, direction, depth);
        }

        /** Vanilla {@code NetherBridgePiece.generateAndAddPiece}. */
        private FortressPiece generateAndAddPiece(StartPiece startPiece, List<FortressPiece> pieces,
                RandomSource random, int footX, int footY, int footZ, Direction direction, int depth, boolean isCastle) {
            if (Math.abs(footX - startPiece.boundingBox.minX()) <= MAX_DISTANCE_FROM_START
                    && Math.abs(footZ - startPiece.boundingBox.minZ()) <= MAX_DISTANCE_FROM_START) {
                var availablePieces = isCastle ? startPiece.availableCastlePieces : startPiece.availableBridgePieces;
                var newPiece = this.generatePiece(startPiece, availablePieces, pieces, random, footX, footY, footZ, direction, depth + 1);
                if (newPiece != null) {
                    pieces.add(newPiece);
                    startPiece.pendingChildren.add(newPiece);
                }
                return newPiece;
            }

            // Vanilla: out of range - a filler may still be constructed (and
            // consume random state) but is discarded, never added to the
            // piece list or the pending-children queue.
            return BridgeEndFiller.createPiece(pieces, random, footX, footY, footZ, direction, depth);
        }

        FortressPiece generateChildForward(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random,
                int xOff, int yOff, boolean isCastle) {
            var orientation = this.getOrientation();
            if (orientation == null) {
                return null;
            }
            return switch (orientation) {
                case NORTH -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1,
                        orientation, this.genDepth, isCastle);
                case SOUTH -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() + xOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1,
                        orientation, this.genDepth, isCastle);
                case WEST -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff,
                        orientation, this.genDepth, isCastle);
                case EAST -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + xOff,
                        orientation, this.genDepth, isCastle);
                default -> null;
            };
        }

        FortressPiece generateChildLeft(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random,
                int yOff, int zOff, boolean isCastle) {
            var orientation = this.getOrientation();
            if (orientation == null) {
                return null;
            }
            return switch (orientation) {
                case NORTH, SOUTH -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() - 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff,
                        Direction.WEST, this.genDepth, isCastle);
                case WEST, EAST -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.minZ() - 1,
                        Direction.NORTH, this.genDepth, isCastle);
                default -> null;
            };
        }

        FortressPiece generateChildRight(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random,
                int yOff, int zOff, boolean isCastle) {
            var orientation = this.getOrientation();
            if (orientation == null) {
                return null;
            }
            return switch (orientation) {
                case NORTH, SOUTH -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.maxX() + 1, this.boundingBox.minY() + yOff, this.boundingBox.minZ() + zOff,
                        Direction.EAST, this.genDepth, isCastle);
                case WEST, EAST -> this.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() + zOff, this.boundingBox.minY() + yOff, this.boundingBox.maxZ() + 1,
                        Direction.SOUTH, this.genDepth, isCastle);
                default -> null;
            };
        }
    }

    public static final class StartPiece extends BridgeCrossing {
        final List<PieceWeight> availableBridgePieces = bridgePieceWeights();
        final List<PieceWeight> availableCastlePieces = castlePieceWeights();
        final List<FortressPiece> pendingChildren = new ArrayList<>();
        PieceWeight previousPiece;

        StartPiece(RandomSource random, int west, int north) {
            super(west, north, Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size())));
        }
    }

    public static class BridgeCrossing extends FortressPiece {
        BridgeCrossing(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        BridgeCrossing(int west, int north, Direction direction) {
            super(0, makeBoundingBox(west, MAGIC_START_Y, north, direction, 19, 10, 19));
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 8, 3, false);
            this.generateChildLeft(startPiece, pieces, random, 3, 8, false);
            this.generateChildRight(startPiece, pieces, random, 3, 8, false);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -8, -3, 0, 19, 10, 19, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new BridgeCrossing(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 7, 3, 0, 11, 4, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 3, 7, 18, 4, 11, bricks, bricks, false);
            this.generateBox(level, chunkBB, 8, 5, 0, 10, 7, 18, air, air, false);
            this.generateBox(level, chunkBB, 0, 5, 8, 18, 7, 10, air, air, false);
            this.generateBox(level, chunkBB, 7, 5, 0, 7, 5, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 7, 5, 11, 7, 5, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 11, 5, 0, 11, 5, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 11, 5, 11, 11, 5, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 5, 7, 7, 5, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 11, 5, 7, 18, 5, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 5, 11, 7, 5, 11, bricks, bricks, false);
            this.generateBox(level, chunkBB, 11, 5, 11, 18, 5, 11, bricks, bricks, false);
            this.generateBox(level, chunkBB, 7, 2, 0, 11, 2, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 7, 2, 13, 11, 2, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 7, 0, 0, 11, 1, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 7, 0, 15, 11, 1, 18, bricks, bricks, false);

            for (var x = 7; x <= 11; x++) {
                for (var z = 0; z <= 2; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                    this.fillColumnDown(level, bricks, x, -1, 18 - z, chunkBB);
                }
            }

            this.generateBox(level, chunkBB, 0, 2, 7, 5, 2, 11, bricks, bricks, false);
            this.generateBox(level, chunkBB, 13, 2, 7, 18, 2, 11, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 0, 7, 3, 1, 11, bricks, bricks, false);
            this.generateBox(level, chunkBB, 15, 0, 7, 18, 1, 11, bricks, bricks, false);

            for (var x = 0; x <= 2; x++) {
                for (var z = 7; z <= 11; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                    this.fillColumnDown(level, bricks, 18 - x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class BridgeEndFiller extends FortressPiece {
        private final int selfSeed;

        BridgeEndFiller(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.selfSeed = random.nextInt();
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -3, 0, 5, 10, 8, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new BridgeEndFiller(genDepth, random, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var selfRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                    new rocks.minestom.worldgen.random.LegacyRandomSource(this.selfSeed));

            for (var x = 0; x <= 4; x++) {
                for (var y = 3; y <= 4; y++) {
                    var z = selfRandom.nextInt(8);
                    this.generateBox(level, chunkBB, x, y, 0, x, y, z, bricks, bricks, false);
                }
            }

            var z1 = selfRandom.nextInt(8);
            this.generateBox(level, chunkBB, 0, 5, 0, 0, 5, z1, bricks, bricks, false);
            var z2 = selfRandom.nextInt(8);
            this.generateBox(level, chunkBB, 4, 5, 0, 4, 5, z2, bricks, bricks, false);

            for (var x = 0; x <= 4; x++) {
                var zx = selfRandom.nextInt(5);
                this.generateBox(level, chunkBB, x, 2, 0, x, 2, zx, bricks, bricks, false);
            }

            for (var x = 0; x <= 4; x++) {
                for (var y = 0; y <= 1; y++) {
                    var zx = selfRandom.nextInt(3);
                    this.generateBox(level, chunkBB, x, y, 0, x, y, zx, bricks, bricks, false);
                }
            }
        }
    }

    public static final class BridgeStraight extends FortressPiece {
        BridgeStraight(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 1, 3, false);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -3, 0, 5, 10, 19, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new BridgeStraight(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 0, 3, 0, 4, 4, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 5, 0, 3, 7, 18, air, air, false);
            this.generateBox(level, chunkBB, 0, 5, 0, 0, 5, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 5, 0, 4, 5, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 2, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 13, 4, 2, 18, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 0, 15, 4, 1, 18, bricks, bricks, false);

            for (var x = 0; x <= 4; x++) {
                for (var z = 0; z <= 2; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                    this.fillColumnDown(level, bricks, x, -1, 18 - z, chunkBB);
                }
            }

            var nseFence = fence("north", "south", "east");
            var nswFence = fence("north", "south", "west");
            this.generateBox(level, chunkBB, 0, 1, 1, 0, 4, 1, nseFence, nseFence, false);
            this.generateBox(level, chunkBB, 0, 3, 4, 0, 4, 4, nseFence, nseFence, false);
            this.generateBox(level, chunkBB, 0, 3, 14, 0, 4, 14, nseFence, nseFence, false);
            this.generateBox(level, chunkBB, 0, 1, 17, 0, 4, 17, nseFence, nseFence, false);
            this.generateBox(level, chunkBB, 4, 1, 1, 4, 4, 1, nswFence, nswFence, false);
            this.generateBox(level, chunkBB, 4, 3, 4, 4, 4, 4, nswFence, nswFence, false);
            this.generateBox(level, chunkBB, 4, 3, 14, 4, 4, 14, nswFence, nswFence, false);
            this.generateBox(level, chunkBB, 4, 1, 17, 4, 4, 17, nswFence, nswFence, false);
        }
    }

    public static final class CastleCorridorStairsPiece extends FortressPiece {
        CastleCorridorStairsPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 1, 0, true);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, -7, 0, 5, 14, 10, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleCorridorStairsPiece(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            var stairSouth = stairs("south");
            var nsFence = fence("north", "south");

            for (var step = 0; step <= 9; step++) {
                var floor = Math.max(1, 7 - step);
                var roof = Math.min(Math.max(floor + 5, 14 - step), 13);
                var z = step;
                this.generateBox(level, chunkBB, 0, 0, step, 4, floor, step, bricks, bricks, false);
                this.generateBox(level, chunkBB, 1, floor + 1, step, 3, roof - 1, step, air, air, false);
                if (step <= 6) {
                    this.placeBlock(level, stairSouth, 1, floor + 1, step, chunkBB);
                    this.placeBlock(level, stairSouth, 2, floor + 1, step, chunkBB);
                    this.placeBlock(level, stairSouth, 3, floor + 1, step, chunkBB);
                }

                this.generateBox(level, chunkBB, 0, roof, step, 4, roof, step, bricks, bricks, false);
                this.generateBox(level, chunkBB, 0, floor + 1, step, 0, roof - 1, step, bricks, bricks, false);
                this.generateBox(level, chunkBB, 4, floor + 1, step, 4, roof - 1, step, bricks, bricks, false);
                if ((step & 1) == 0) {
                    this.generateBox(level, chunkBB, 0, floor + 2, step, 0, floor + 3, step, nsFence, nsFence, false);
                    this.generateBox(level, chunkBB, 4, floor + 2, step, 4, floor + 3, step, nsFence, nsFence, false);
                }

                for (var x = 0; x <= 4; x++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class CastleCorridorTBalconyPiece extends FortressPiece {
        CastleCorridorTBalconyPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            var zOff = 1;
            var orientation = this.getOrientation();
            if (orientation == Direction.WEST || orientation == Direction.NORTH) {
                zOff = 5;
            }

            this.generateChildLeft(startPiece, pieces, random, 0, zOff, random.nextInt(8) > 0);
            this.generateChildRight(startPiece, pieces, random, 0, zOff, random.nextInt(8) > 0);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -3, 0, 0, 9, 7, 9, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleCorridorTBalconyPiece(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            var nsFence = fence("north", "south");
            var weFence = fence("west", "east");
            this.generateBox(level, chunkBB, 0, 0, 0, 8, 1, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 8, 5, 8, air, air, false);
            this.generateBox(level, chunkBB, 0, 6, 0, 8, 6, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 2, 5, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 2, 0, 8, 5, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 3, 0, 1, 4, 0, weFence, weFence, false);
            this.generateBox(level, chunkBB, 7, 3, 0, 7, 4, 0, weFence, weFence, false);
            this.generateBox(level, chunkBB, 0, 2, 4, 8, 2, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 1, 4, 2, 2, 4, air, air, false);
            this.generateBox(level, chunkBB, 6, 1, 4, 7, 2, 4, air, air, false);
            this.generateBox(level, chunkBB, 1, 3, 8, 7, 3, 8, weFence, weFence, false);
            this.placeBlock(level, fence("east", "south"), 0, 3, 8, chunkBB);
            this.placeBlock(level, fence("west", "south"), 8, 3, 8, chunkBB);
            this.generateBox(level, chunkBB, 0, 3, 6, 0, 3, 7, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 8, 3, 6, 8, 3, 7, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 0, 3, 4, 0, 5, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 8, 3, 4, 8, 5, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 3, 5, 2, 5, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 3, 5, 7, 5, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 4, 5, 1, 5, 5, weFence, weFence, false);
            this.generateBox(level, chunkBB, 7, 4, 5, 7, 5, 5, weFence, weFence, false);

            for (var z = 0; z <= 5; z++) {
                for (var x = 0; x <= 8; x++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class CastleEntrance extends FortressPiece {
        CastleEntrance(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 5, 3, true);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -5, -3, 0, 13, 14, 13, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleEntrance(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 0, 3, 0, 12, 4, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 5, 0, 12, 13, 12, air, air, false);
            this.generateBox(level, chunkBB, 0, 5, 0, 1, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 11, 5, 0, 12, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 11, 4, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 8, 5, 11, 10, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 9, 11, 7, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 12, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 8, 5, 0, 10, 12, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 9, 0, 7, 12, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 11, 2, 10, 12, 10, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 8, 0, 7, 8, 0, Block.NETHER_BRICK_FENCE, Block.NETHER_BRICK_FENCE, false);
            var weFence = fence("west", "east");
            var nsFence = fence("north", "south");

            for (var i = 1; i <= 11; i += 2) {
                this.generateBox(level, chunkBB, i, 10, 0, i, 11, 0, weFence, weFence, false);
                this.generateBox(level, chunkBB, i, 10, 12, i, 11, 12, weFence, weFence, false);
                this.generateBox(level, chunkBB, 0, 10, i, 0, 11, i, nsFence, nsFence, false);
                this.generateBox(level, chunkBB, 12, 10, i, 12, 11, i, nsFence, nsFence, false);
                this.placeBlock(level, bricks, i, 13, 0, chunkBB);
                this.placeBlock(level, bricks, i, 13, 12, chunkBB);
                this.placeBlock(level, bricks, 0, 13, i, chunkBB);
                this.placeBlock(level, bricks, 12, 13, i, chunkBB);
                if (i != 11) {
                    this.placeBlock(level, weFence, i + 1, 13, 0, chunkBB);
                    this.placeBlock(level, weFence, i + 1, 13, 12, chunkBB);
                    this.placeBlock(level, nsFence, 0, 13, i + 1, chunkBB);
                    this.placeBlock(level, nsFence, 12, 13, i + 1, chunkBB);
                }
            }

            this.placeBlock(level, fence("north", "east"), 0, 13, 0, chunkBB);
            this.placeBlock(level, fence("south", "east"), 0, 13, 12, chunkBB);
            this.placeBlock(level, fence("south", "west"), 12, 13, 12, chunkBB);
            this.placeBlock(level, fence("north", "west"), 12, 13, 0, chunkBB);

            for (var z = 3; z <= 9; z += 2) {
                this.generateBox(level, chunkBB, 1, 7, z, 1, 8, z, fence("north", "south", "west"), fence("north", "south", "west"), false);
                this.generateBox(level, chunkBB, 11, 7, z, 11, 8, z, fence("north", "south", "east"), fence("north", "south", "east"), false);
            }

            this.generateBox(level, chunkBB, 4, 2, 0, 8, 2, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 4, 12, 2, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 0, 0, 8, 1, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 0, 9, 8, 1, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 0, 4, 3, 1, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 9, 0, 4, 12, 1, 8, bricks, bricks, false);

            for (var x = 4; x <= 8; x++) {
                for (var z = 0; z <= 2; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                    this.fillColumnDown(level, bricks, x, -1, 12 - z, chunkBB);
                }
            }

            for (var x = 0; x <= 2; x++) {
                for (var z = 4; z <= 8; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                    this.fillColumnDown(level, bricks, 12 - x, -1, z, chunkBB);
                }
            }

            this.generateBox(level, chunkBB, 5, 5, 5, 7, 5, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 1, 6, 6, 4, 6, air, air, false);
            this.placeBlock(level, bricks, 6, 0, 6, chunkBB);
            // Vanilla schedules a lava fluid tick here; this port places the
            // source block only (the tick scheduler is out of scope).
            this.placeBlock(level, Block.LAVA, 6, 5, 6, chunkBB);
        }
    }

    public static final class CastleSmallCorridorCrossingPiece extends FortressPiece {
        CastleSmallCorridorCrossingPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 1, 0, true);
            this.generateChildLeft(startPiece, pieces, random, 0, 1, true);
            this.generateChildRight(startPiece, pieces, random, 0, 1, true);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleSmallCorridorCrossingPiece(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, air, air, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 4, 0, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 2, 4, 4, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, bricks, bricks, false);

            for (var x = 0; x <= 4; x++) {
                for (var z = 0; z <= 4; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class CastleSmallCorridorLeftTurnPiece extends FortressPiece {
        private boolean isNeedingChest;

        CastleSmallCorridorLeftTurnPiece(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildLeft(startPiece, pieces, random, 0, 1, true);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleSmallCorridorLeftTurnPiece(genDepth, random, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            var weFence = fence("west", "east");
            var nsFence = fence("north", "south");
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, air, air, false);
            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 3, 1, 4, 4, 1, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 4, 3, 3, 4, 4, 3, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 4, 3, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 3, 4, 1, 4, 4, weFence, weFence, false);
            this.generateBox(level, chunkBB, 3, 3, 4, 3, 4, 4, weFence, weFence, false);
            if (this.isNeedingChest) {
                var pos = this.worldPos(3, 2, 3);
                if (chunkBB.isInside(pos)) {
                    this.isNeedingChest = false;
                    this.createChest(level, chunkBB, random, 3, 2, 3);
                }
            }

            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, bricks, bricks, false);

            for (var x = 0; x <= 4; x++) {
                for (var z = 0; z <= 4; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class CastleSmallCorridorPiece extends FortressPiece {
        CastleSmallCorridorPiece(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 1, 0, true);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleSmallCorridorPiece(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            var nsFence = fence("north", "south");
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, air, air, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 3, 1, 0, 4, 1, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 0, 3, 3, 0, 4, 3, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 4, 3, 1, 4, 4, 1, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 4, 3, 3, 4, 4, 3, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, bricks, bricks, false);

            for (var x = 0; x <= 4; x++) {
                for (var z = 0; z <= 4; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class CastleSmallCorridorRightTurnPiece extends FortressPiece {
        private boolean isNeedingChest;

        CastleSmallCorridorRightTurnPiece(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
            this.isNeedingChest = random.nextInt(3) == 0;
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildRight(startPiece, pieces, random, 0, 1, true);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -1, 0, 0, 5, 7, 5, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleSmallCorridorRightTurnPiece(genDepth, random, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            var weFence = fence("west", "east");
            var nsFence = fence("north", "south");
            this.generateBox(level, chunkBB, 0, 0, 0, 4, 1, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 4, 5, 4, air, air, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 3, 1, 0, 4, 1, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 0, 3, 3, 0, 4, 3, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 4, 2, 0, 4, 5, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 2, 4, 4, 5, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 3, 4, 1, 4, 4, weFence, weFence, false);
            this.generateBox(level, chunkBB, 3, 3, 4, 3, 4, 4, weFence, weFence, false);
            if (this.isNeedingChest) {
                var pos = this.worldPos(1, 2, 3);
                if (chunkBB.isInside(pos)) {
                    this.isNeedingChest = false;
                    this.createChest(level, chunkBB, random, 1, 2, 3);
                }
            }

            this.generateBox(level, chunkBB, 0, 6, 0, 4, 6, 4, bricks, bricks, false);

            for (var x = 0; x <= 4; x++) {
                for (var z = 0; z <= 4; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class CastleStalkRoom extends FortressPiece {
        CastleStalkRoom(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 5, 3, true);
            this.generateChildForward(startPiece, pieces, random, 5, 11, true);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -5, -3, 0, 13, 14, 13, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new CastleStalkRoom(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 0, 3, 0, 12, 4, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 5, 0, 12, 13, 12, air, air, false);
            this.generateBox(level, chunkBB, 0, 5, 0, 1, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 11, 5, 0, 12, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 11, 4, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 8, 5, 11, 10, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 9, 11, 7, 12, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 12, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 8, 5, 0, 10, 12, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 9, 0, 7, 12, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 11, 2, 10, 12, 10, bricks, bricks, false);
            var weFence = fence("west", "east");
            var nsFence = fence("north", "south");
            var nswFence = fence("north", "south", "west");
            var nseFence = fence("north", "south", "east");

            for (var i = 1; i <= 11; i += 2) {
                this.generateBox(level, chunkBB, i, 10, 0, i, 11, 0, weFence, weFence, false);
                this.generateBox(level, chunkBB, i, 10, 12, i, 11, 12, weFence, weFence, false);
                this.generateBox(level, chunkBB, 0, 10, i, 0, 11, i, nsFence, nsFence, false);
                this.generateBox(level, chunkBB, 12, 10, i, 12, 11, i, nsFence, nsFence, false);
                this.placeBlock(level, bricks, i, 13, 0, chunkBB);
                this.placeBlock(level, bricks, i, 13, 12, chunkBB);
                this.placeBlock(level, bricks, 0, 13, i, chunkBB);
                this.placeBlock(level, bricks, 12, 13, i, chunkBB);
                if (i != 11) {
                    this.placeBlock(level, weFence, i + 1, 13, 0, chunkBB);
                    this.placeBlock(level, weFence, i + 1, 13, 12, chunkBB);
                    this.placeBlock(level, nsFence, 0, 13, i + 1, chunkBB);
                    this.placeBlock(level, nsFence, 12, 13, i + 1, chunkBB);
                }
            }

            this.placeBlock(level, fence("north", "east"), 0, 13, 0, chunkBB);
            this.placeBlock(level, fence("south", "east"), 0, 13, 12, chunkBB);
            this.placeBlock(level, fence("south", "west"), 12, 13, 12, chunkBB);
            this.placeBlock(level, fence("north", "west"), 12, 13, 0, chunkBB);

            for (var z = 3; z <= 9; z += 2) {
                this.generateBox(level, chunkBB, 1, 7, z, 1, 8, z, nswFence, nswFence, false);
                this.generateBox(level, chunkBB, 11, 7, z, 11, 8, z, nseFence, nseFence, false);
            }

            var stairsNorth = stairs("north");

            for (var ix = 0; ix <= 6; ix++) {
                var z = ix + 4;

                for (var x = 5; x <= 7; x++) {
                    this.placeBlock(level, stairsNorth, x, 5 + ix, z, chunkBB);
                }

                if (z >= 5 && z <= 8) {
                    this.generateBox(level, chunkBB, 5, 5, z, 7, ix + 4, z, bricks, bricks, false);
                } else if (z >= 9 && z <= 10) {
                    this.generateBox(level, chunkBB, 5, 8, z, 7, ix + 4, z, bricks, bricks, false);
                }

                if (ix >= 1) {
                    this.generateBox(level, chunkBB, 5, 6 + ix, z, 7, 9 + ix, z, air, air, false);
                }
            }

            for (var x = 5; x <= 7; x++) {
                this.placeBlock(level, stairsNorth, x, 12, 11, chunkBB);
            }

            this.generateBox(level, chunkBB, 5, 6, 7, 5, 7, 7, nseFence, nseFence, false);
            this.generateBox(level, chunkBB, 7, 6, 7, 7, 7, 7, nswFence, nswFence, false);
            this.generateBox(level, chunkBB, 5, 13, 12, 7, 13, 12, air, air, false);
            this.generateBox(level, chunkBB, 2, 5, 2, 3, 5, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 9, 3, 5, 10, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 4, 2, 5, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 9, 5, 2, 10, 5, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 9, 5, 9, 10, 5, 10, bricks, bricks, false);
            this.generateBox(level, chunkBB, 10, 5, 4, 10, 5, 8, bricks, bricks, false);
            var eastStairs = stairs("east");
            var westStairs = stairs("west");
            this.placeBlock(level, westStairs, 4, 5, 2, chunkBB);
            this.placeBlock(level, westStairs, 4, 5, 3, chunkBB);
            this.placeBlock(level, westStairs, 4, 5, 9, chunkBB);
            this.placeBlock(level, westStairs, 4, 5, 10, chunkBB);
            this.placeBlock(level, eastStairs, 8, 5, 2, chunkBB);
            this.placeBlock(level, eastStairs, 8, 5, 3, chunkBB);
            this.placeBlock(level, eastStairs, 8, 5, 9, chunkBB);
            this.placeBlock(level, eastStairs, 8, 5, 10, chunkBB);
            this.generateBox(level, chunkBB, 3, 4, 4, 4, 4, 8, Block.SOUL_SAND, Block.SOUL_SAND, false);
            this.generateBox(level, chunkBB, 8, 4, 4, 9, 4, 8, Block.SOUL_SAND, Block.SOUL_SAND, false);
            this.generateBox(level, chunkBB, 3, 5, 4, 4, 5, 8, Block.NETHER_WART, Block.NETHER_WART, false);
            this.generateBox(level, chunkBB, 8, 5, 4, 9, 5, 8, Block.NETHER_WART, Block.NETHER_WART, false);
            this.generateBox(level, chunkBB, 4, 2, 0, 8, 2, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 4, 12, 2, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 0, 0, 8, 1, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 4, 0, 9, 8, 1, 12, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 0, 4, 3, 1, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 9, 0, 4, 12, 1, 8, bricks, bricks, false);

            for (var x = 4; x <= 8; x++) {
                for (var z = 0; z <= 2; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                    this.fillColumnDown(level, bricks, x, -1, 12 - z, chunkBB);
                }
            }

            for (var x = 0; x <= 2; x++) {
                for (var z = 4; z <= 8; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                    this.fillColumnDown(level, bricks, 12 - x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class MonsterThrone extends FortressPiece {
        private boolean hasPlacedSpawner;

        MonsterThrone(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -2, 0, 0, 7, 8, 9, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new MonsterThrone(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 0, 2, 0, 6, 7, 7, air, air, false);
            this.generateBox(level, chunkBB, 1, 0, 0, 5, 1, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 2, 1, 5, 2, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 3, 2, 5, 3, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 4, 3, 5, 4, 7, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 2, 0, 1, 4, 2, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 2, 0, 5, 4, 2, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 5, 2, 1, 5, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 5, 2, 5, 5, 3, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 5, 3, 0, 5, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 5, 3, 6, 5, 8, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 5, 8, 5, 5, 8, bricks, bricks, false);
            var weFence = fence("west", "east");
            var nsFence = fence("north", "south");
            this.placeBlock(level, fence("west"), 1, 6, 3, chunkBB);
            this.placeBlock(level, fence("east"), 5, 6, 3, chunkBB);
            this.placeBlock(level, fence("east", "north"), 0, 6, 3, chunkBB);
            this.placeBlock(level, fence("west", "north"), 6, 6, 3, chunkBB);
            this.generateBox(level, chunkBB, 0, 6, 4, 0, 6, 7, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 6, 6, 4, 6, 6, 7, nsFence, nsFence, false);
            this.placeBlock(level, fence("east", "south"), 0, 6, 8, chunkBB);
            this.placeBlock(level, fence("west", "south"), 6, 6, 8, chunkBB);
            this.generateBox(level, chunkBB, 1, 6, 8, 5, 6, 8, weFence, weFence, false);
            this.placeBlock(level, fence("east"), 1, 7, 8, chunkBB);
            this.generateBox(level, chunkBB, 2, 7, 8, 4, 7, 8, weFence, weFence, false);
            this.placeBlock(level, fence("west"), 5, 7, 8, chunkBB);
            this.placeBlock(level, fence("east"), 2, 8, 8, chunkBB);
            this.placeBlock(level, weFence, 3, 8, 8, chunkBB);
            this.placeBlock(level, fence("west"), 4, 8, 8, chunkBB);
            if (!this.hasPlacedSpawner) {
                var pos = this.worldPos(3, 5, 5);
                if (chunkBB.isInside(pos)) {
                    this.hasPlacedSpawner = true;
                    // Vanilla sets the spawner's entity type to blaze via
                    // block-entity NBT; this port places the bare block.
                    level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.SPAWNER);
                }
            }

            for (var x = 0; x <= 6; x++) {
                for (var z = 0; z <= 6; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class RoomCrossing extends FortressPiece {
        RoomCrossing(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildForward(startPiece, pieces, random, 2, 0, false);
            this.generateChildLeft(startPiece, pieces, random, 0, 2, false);
            this.generateChildRight(startPiece, pieces, random, 0, 2, false);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -2, 0, 0, 7, 9, 7, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new RoomCrossing(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 0, 0, 0, 6, 1, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 6, 7, 6, air, air, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 1, 6, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 6, 1, 6, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 2, 0, 6, 6, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 2, 6, 6, 6, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 6, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 5, 0, 6, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 2, 0, 6, 6, 1, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 2, 5, 6, 6, 6, bricks, bricks, false);
            var weFence = fence("west", "east");
            var nsFence = fence("north", "south");
            this.generateBox(level, chunkBB, 2, 6, 0, 4, 6, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 5, 0, weFence, weFence, false);
            this.generateBox(level, chunkBB, 2, 6, 6, 4, 6, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 6, 4, 5, 6, weFence, weFence, false);
            this.generateBox(level, chunkBB, 0, 6, 2, 0, 6, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 5, 2, 0, 5, 4, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 6, 6, 2, 6, 6, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 5, 2, 6, 5, 4, nsFence, nsFence, false);

            for (var x = 0; x <= 6; x++) {
                for (var z = 0; z <= 6; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }

    public static final class StairsRoom extends FortressPiece {
        StairsRoom(int genDepth, BoundingBox boundingBox, Direction direction) {
            super(genDepth, boundingBox);
            this.setOrientation(direction);
        }

        @Override
        void addChildren(StartPiece startPiece, List<FortressPiece> pieces, RandomSource random) {
            this.generateChildRight(startPiece, pieces, random, 6, 2, false);
        }

        static FortressPiece createPiece(List<FortressPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction, int genDepth) {
            var box = orientBox(footX, footY, footZ, -2, 0, 0, 7, 11, 7, direction);
            return isOkBox(box) && findCollisionPiece(pieces, box) == null
                    ? new StairsRoom(genDepth, box, direction)
                    : null;
        }

        @Override
        void postProcess(FortressLevel level, RandomSource random, BoundingBox chunkBB) {
            var bricks = Block.NETHER_BRICKS;
            var air = Block.AIR;
            this.generateBox(level, chunkBB, 0, 0, 0, 6, 1, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 6, 10, 6, air, air, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 1, 8, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 5, 2, 0, 6, 8, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 0, 2, 1, 0, 8, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 2, 1, 6, 8, 6, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 2, 6, 5, 8, 6, bricks, bricks, false);
            var weFence = fence("west", "east");
            var nsFence = fence("north", "south");
            this.generateBox(level, chunkBB, 0, 3, 2, 0, 5, 4, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 6, 3, 2, 6, 5, 2, nsFence, nsFence, false);
            this.generateBox(level, chunkBB, 6, 3, 4, 6, 5, 4, nsFence, nsFence, false);
            this.placeBlock(level, bricks, 5, 2, 5, chunkBB);
            this.generateBox(level, chunkBB, 4, 2, 5, 4, 3, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 3, 2, 5, 3, 4, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 2, 5, 2, 5, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 2, 5, 1, 6, 5, bricks, bricks, false);
            this.generateBox(level, chunkBB, 1, 7, 1, 5, 7, 4, bricks, bricks, false);
            this.generateBox(level, chunkBB, 6, 8, 2, 6, 8, 4, air, air, false);
            this.generateBox(level, chunkBB, 2, 6, 0, 4, 8, 0, bricks, bricks, false);
            this.generateBox(level, chunkBB, 2, 5, 0, 4, 5, 0, weFence, weFence, false);

            for (var x = 0; x <= 6; x++) {
                for (var z = 0; z <= 6; z++) {
                    this.fillColumnDown(level, bricks, x, -1, z, chunkBB);
                }
            }
        }
    }
}
