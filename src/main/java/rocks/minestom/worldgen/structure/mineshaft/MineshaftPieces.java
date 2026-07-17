package rocks.minestom.worldgen.structure.mineshaft;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.LinkedList;
import java.util.List;

/**
 * Port of vanilla {@code MineshaftPieces}: the procedural room, corridor,
 * crossing and stairs pieces. Random call order matches vanilla exactly so a
 * seeded {@code WorldgenRandom} reproduces vanilla layouts and decoration
 * block-for-block.
 */
public final class MineshaftPieces {
    private static final int MAX_PILLAR_HEIGHT = 20;
    private static final int MAX_CHAIN_HEIGHT = 50;
    private static final int MAX_DEPTH = 8;
    static final Block CAVE_AIR = Block.CAVE_AIR;

    private MineshaftPieces() {
    }

    private static MineshaftPiece createRandomShaftPiece(List<MineshaftPiece> pieces, RandomSource random,
            int footX, int footY, int footZ, Direction direction, int genDepth, MineshaftType type) {
        var selection = random.nextInt(100);
        if (selection >= 80) {
            var crossingBox = Crossing.findCrossing(pieces, random, footX, footY, footZ, direction);
            if (crossingBox != null) {
                return new Crossing(genDepth, crossingBox, direction, type);
            }
        } else if (selection >= 70) {
            var stairsBox = Stairs.findStairs(pieces, random, footX, footY, footZ, direction);
            if (stairsBox != null) {
                return new Stairs(genDepth, stairsBox, direction, type);
            }
        } else {
            var corridorBox = Corridor.findCorridorSize(pieces, random, footX, footY, footZ, direction);
            if (corridorBox != null) {
                return new Corridor(genDepth, random, corridorBox, direction, type);
            }
        }

        return null;
    }

    static MineshaftPiece generateAndAddPiece(MineshaftPiece startPiece, List<MineshaftPiece> pieces,
            RandomSource random, int footX, int footY, int footZ, Direction direction, int depth) {
        if (depth > MAX_DEPTH) {
            return null;
        }

        if (Math.abs(footX - startPiece.boundingBox.minX()) > 80 || Math.abs(footZ - startPiece.boundingBox.minZ()) > 80) {
            return null;
        }

        var newPiece = createRandomShaftPiece(pieces, random, footX, footY, footZ, direction, depth + 1, startPiece.type);
        if (newPiece != null) {
            pieces.add(newPiece);
            newPiece.addChildren(startPiece, pieces, random);
        }

        return newPiece;
    }

    private static boolean hasCollision(List<MineshaftPiece> pieces, BoundingBox box) {
        for (var piece : pieces) {
            if (piece.boundingBox.intersects(box)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInside(BoundingBox box, int x, int y, int z) {
        return x >= box.minX() && x <= box.maxX()
                && y >= box.minY() && y <= box.maxY()
                && z >= box.minZ() && z <= box.maxZ();
    }

    /**
     * Approximation of vanilla {@code isFaceSturdy(UP)} for the block set seen
     * during mineshaft placement (terrain, fluids and the mineshaft's own
     * blocks): any motion-blocking full block except fences and chains.
     */
    private static boolean isFaceSturdy(Block block) {
        return block.isSolid() && !isFence(block) && !block.compare(Block.IRON_CHAIN);
    }

    /**
     * Approximation of vanilla {@code isSolidRender()} for the same block set.
     */
    private static boolean isSolidRender(Block block) {
        return block.isSolid() && !isFence(block) && !block.compare(Block.IRON_CHAIN)
                && !block.compare(Block.SPAWNER);
    }

    private static boolean isFence(Block block) {
        return block.compare(Block.OAK_FENCE) || block.compare(Block.DARK_OAK_FENCE);
    }

    private static boolean isReplaceableByStructures(Block block) {
        return block.isAir() || block.isLiquid() || block.compare(Block.GLOW_LICHEN)
                || block.compare(Block.SEAGRASS) || block.compare(Block.TALL_SEAGRASS);
    }

    private static boolean isFallingBlock(Block block) {
        return block.compare(Block.SAND) || block.compare(Block.RED_SAND) || block.compare(Block.GRAVEL)
                || block.compare(Block.SUSPICIOUS_SAND) || block.compare(Block.SUSPICIOUS_GRAVEL);
    }

    public abstract static class MineshaftPiece {
        protected final MineshaftType type;
        protected final int genDepth;
        protected BoundingBox boundingBox;
        private Direction orientation;
        private boolean mirrorLeftRight;
        private boolean rotateClockwise;

        protected MineshaftPiece(int genDepth, MineshaftType type, BoundingBox boundingBox) {
            this.genDepth = genDepth;
            this.type = type;
            this.boundingBox = boundingBox;
        }

        public BoundingBox boundingBox() {
            return this.boundingBox;
        }

        public void move(int dx, int dy, int dz) {
            this.boundingBox = this.boundingBox.moved(dx, dy, dz);
        }

        protected void setOrientation(Direction orientation) {
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

        protected Direction getOrientation() {
            return this.orientation;
        }

        abstract void addChildren(MineshaftPiece startPiece, List<MineshaftPiece> pieces, RandomSource random);

        abstract void postProcess(MineshaftLevel level, RandomSource random, BoundingBox chunkBB);

        protected int worldX(int x, int z) {
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

        protected int worldY(int y) {
            return this.orientation == null ? y : y + this.boundingBox.minY();
        }

        protected int worldZ(int x, int z) {
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

        protected void placeBlock(MineshaftLevel level, Block block, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return;
            }
            if (!this.canBeReplaced(level, wx, wy, wz)) {
                return;
            }
            level.setBlock(wx, wy, wz, this.transform(block));
        }

        /**
         * Vanilla mineshaft {@code canBeReplaced}: never replaces the
         * structure's own planks, wood, fences or chains.
         */
        private boolean canBeReplaced(MineshaftLevel level, int wx, int wy, int wz) {
            var state = level.getBlock(wx, wy, wz);
            return !state.compare(this.type.planksState())
                    && !state.compare(this.type.woodState())
                    && !state.compare(this.type.fenceState())
                    && !state.compare(Block.IRON_CHAIN);
        }

        protected Block getBlock(MineshaftLevel level, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return Block.AIR;
            }
            return level.getBlock(wx, wy, wz);
        }

        protected boolean isInterior(MineshaftLevel level, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y + 1);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, wy, wz)) {
                return false;
            }
            return wy < level.oceanFloorHeight(wx, wz);
        }

        protected void generateBox(MineshaftLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
                int x1, int y1, int z1, Block edgeBlock, Block fillBlock, boolean skipAir) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        if (skipAir && this.getBlock(level, x, y, z, chunkBB).isAir()) {
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

        protected void generateMaybeBox(MineshaftLevel level, BoundingBox chunkBB, RandomSource random,
                float probability, int x0, int y0, int z0, int x1, int y1, int z1,
                Block edgeBlock, Block fillBlock, boolean skipAir, boolean hasToBeInside) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        if (random.nextFloat() > probability) {
                            continue;
                        }
                        if (skipAir && this.getBlock(level, x, y, z, chunkBB).isAir()) {
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

        protected void maybeGenerateBlock(MineshaftLevel level, BoundingBox chunkBB, RandomSource random,
                float probability, int x, int y, int z, Block block) {
            if (random.nextFloat() < probability) {
                this.placeBlock(level, block, x, y, z, chunkBB);
            }
        }

        protected void generateUpperHalfSphere(MineshaftLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
                int x1, int y1, int z1, Block fillBlock, boolean skipAir) {
            var diagX = (float) (x1 - x0 + 1);
            var diagY = (float) (y1 - y0 + 1);
            var diagZ = (float) (z1 - z0 + 1);
            var cx = x0 + diagX / 2.0F;
            var cz = z0 + diagZ / 2.0F;

            for (var y = y0; y <= y1; y++) {
                var normalizedY = (y - y0) / diagY;

                for (var x = x0; x <= x1; x++) {
                    var normalizedX = (x - cx) / (diagX * 0.5F);

                    for (var z = z0; z <= z1; z++) {
                        var normalizedZ = (z - cz) / (diagZ * 0.5F);
                        if (skipAir && this.getBlock(level, x, y, z, chunkBB).isAir()) {
                            continue;
                        }
                        var distance = normalizedX * normalizedX + normalizedY * normalizedY + normalizedZ * normalizedZ;
                        if (distance <= 1.05F) {
                            this.placeBlock(level, fillBlock, x, y, z, chunkBB);
                        }
                    }
                }
            }
        }

        protected boolean isSupportingBox(MineshaftLevel level, BoundingBox chunkBB, int x0, int x1, int y1, int z0) {
            for (var x = x0; x <= x1; x++) {
                if (this.getBlock(level, x, y1 + 1, z0, chunkBB).isAir()) {
                    return false;
                }
            }
            return true;
        }

        protected boolean isInInvalidLocation(MineshaftLevel level, BoundingBox chunkBB) {
            var x0 = Math.max(this.boundingBox.minX() - 1, chunkBB.minX());
            var y0 = Math.max(this.boundingBox.minY() - 1, chunkBB.minY());
            var z0 = Math.max(this.boundingBox.minZ() - 1, chunkBB.minZ());
            var x1 = Math.min(this.boundingBox.maxX() + 1, chunkBB.maxX());
            var y1 = Math.min(this.boundingBox.maxY() + 1, chunkBB.maxY());
            var z1 = Math.min(this.boundingBox.maxZ() + 1, chunkBB.maxZ());
            if (level.isMineshaftBlockingBiome((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)) {
                return true;
            }

            for (var x = x0; x <= x1; x++) {
                for (var z = z0; z <= z1; z++) {
                    if (level.getBlock(x, y0, z).isLiquid()) {
                        return true;
                    }
                    if (level.getBlock(x, y1, z).isLiquid()) {
                        return true;
                    }
                }
            }

            for (var x = x0; x <= x1; x++) {
                for (var y = y0; y <= y1; y++) {
                    if (level.getBlock(x, y, z0).isLiquid()) {
                        return true;
                    }
                    if (level.getBlock(x, y, z1).isLiquid()) {
                        return true;
                    }
                }
            }

            for (var z = z0; z <= z1; z++) {
                for (var y = y0; y <= y1; y++) {
                    if (level.getBlock(x0, y, z).isLiquid()) {
                        return true;
                    }
                    if (level.getBlock(x1, y, z).isLiquid()) {
                        return true;
                    }
                }
            }

            return false;
        }

        protected void setPlanksBlock(MineshaftLevel level, BoundingBox chunkBB, Block planks, int x, int y, int z) {
            if (!this.isInterior(level, x, y, z, chunkBB)) {
                return;
            }
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            var existing = level.getBlock(wx, wy, wz);
            if (!isFaceSturdy(existing)) {
                level.setBlock(wx, wy, wz, planks);
            }
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

            // Rail north_south / east_west shapes are unchanged by a left-right mirror
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

            var shape = block.getProperty("shape");
            if (shape != null) {
                return switch (shape) {
                    case "north_south" -> block.withProperty("shape", "east_west");
                    case "east_west" -> block.withProperty("shape", "north_south");
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

    public static final class Room extends MineshaftPiece {
        private final List<BoundingBox> childEntranceBoxes = new LinkedList<>();

        Room(int genDepth, RandomSource random, int west, int north, MineshaftType type) {
            super(genDepth, type, roomBox(random, west, north));
        }

        private static BoundingBox roomBox(RandomSource random, int west, int north) {
            var maxX = west + 7 + random.nextInt(6);
            var maxY = 54 + random.nextInt(6);
            var maxZ = north + 7 + random.nextInt(6);
            return new BoundingBox(west, 50, north, maxX, maxY, maxZ);
        }

        @Override
        public void move(int dx, int dy, int dz) {
            super.move(dx, dy, dz);
            this.childEntranceBoxes.replaceAll(box -> box.moved(dx, dy, dz));
        }

        @Override
        void addChildren(MineshaftPiece startPiece, List<MineshaftPiece> pieces, RandomSource random) {
            var depth = this.genDepth;
            var heightSpace = this.boundingBox.getYSpan() - 3 - 1;
            if (heightSpace <= 0) {
                heightSpace = 1;
            }

            var pos = 0;
            while (pos < this.boundingBox.getXSpan()) {
                pos += random.nextInt(this.boundingBox.getXSpan());
                if (pos + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                var child = MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() + pos,
                        this.boundingBox.minY() + random.nextInt(heightSpace) + 1,
                        this.boundingBox.minZ() - 1,
                        Direction.NORTH, depth);
                if (child != null) {
                    var childBox = child.boundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(childBox.minX(), childBox.minY(), this.boundingBox.minZ(),
                            childBox.maxX(), childBox.maxY(), this.boundingBox.minZ() + 1));
                }

                pos += 4;
            }

            pos = 0;
            while (pos < this.boundingBox.getXSpan()) {
                pos += random.nextInt(this.boundingBox.getXSpan());
                if (pos + 3 > this.boundingBox.getXSpan()) {
                    break;
                }

                var child = MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() + pos,
                        this.boundingBox.minY() + random.nextInt(heightSpace) + 1,
                        this.boundingBox.maxZ() + 1,
                        Direction.SOUTH, depth);
                if (child != null) {
                    var childBox = child.boundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(childBox.minX(), childBox.minY(), this.boundingBox.maxZ() - 1,
                            childBox.maxX(), childBox.maxY(), this.boundingBox.maxZ()));
                }

                pos += 4;
            }

            pos = 0;
            while (pos < this.boundingBox.getZSpan()) {
                pos += random.nextInt(this.boundingBox.getZSpan());
                if (pos + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                var child = MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.minX() - 1,
                        this.boundingBox.minY() + random.nextInt(heightSpace) + 1,
                        this.boundingBox.minZ() + pos,
                        Direction.WEST, depth);
                if (child != null) {
                    var childBox = child.boundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(this.boundingBox.minX(), childBox.minY(), childBox.minZ(),
                            this.boundingBox.minX() + 1, childBox.maxY(), childBox.maxZ()));
                }

                pos += 4;
            }

            pos = 0;
            while (pos < this.boundingBox.getZSpan()) {
                pos += random.nextInt(this.boundingBox.getZSpan());
                if (pos + 3 > this.boundingBox.getZSpan()) {
                    break;
                }

                var child = MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                        this.boundingBox.maxX() + 1,
                        this.boundingBox.minY() + random.nextInt(heightSpace) + 1,
                        this.boundingBox.minZ() + pos,
                        Direction.EAST, depth);
                if (child != null) {
                    var childBox = child.boundingBox();
                    this.childEntranceBoxes.add(new BoundingBox(this.boundingBox.maxX() - 1, childBox.minY(), childBox.minZ(),
                            this.boundingBox.maxX(), childBox.maxY(), childBox.maxZ()));
                }

                pos += 4;
            }
        }

        @Override
        void postProcess(MineshaftLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.isInInvalidLocation(level, chunkBB)) {
                return;
            }

            this.generateBox(level, chunkBB,
                    this.boundingBox.minX(), this.boundingBox.minY() + 1, this.boundingBox.minZ(),
                    this.boundingBox.maxX(), Math.min(this.boundingBox.minY() + 3, this.boundingBox.maxY()), this.boundingBox.maxZ(),
                    CAVE_AIR, CAVE_AIR, false);

            for (var entranceBox : this.childEntranceBoxes) {
                this.generateBox(level, chunkBB,
                        entranceBox.minX(), entranceBox.maxY() - 2, entranceBox.minZ(),
                        entranceBox.maxX(), entranceBox.maxY(), entranceBox.maxZ(),
                        CAVE_AIR, CAVE_AIR, false);
            }

            this.generateUpperHalfSphere(level, chunkBB,
                    this.boundingBox.minX(), this.boundingBox.minY() + 4, this.boundingBox.minZ(),
                    this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ(),
                    CAVE_AIR, false);
        }
    }

    public static final class Corridor extends MineshaftPiece {
        private final boolean hasRails;
        private final boolean spiderCorridor;
        private boolean hasPlacedSpider;
        private final int numSections;

        Corridor(int genDepth, RandomSource random, BoundingBox boundingBox, Direction direction, MineshaftType type) {
            super(genDepth, type, boundingBox);
            this.setOrientation(direction);
            this.hasRails = random.nextInt(3) == 0;
            this.spiderCorridor = !this.hasRails && random.nextInt(23) == 0;
            if (direction == Direction.NORTH || direction == Direction.SOUTH) {
                this.numSections = boundingBox.getZSpan() / 5;
            } else {
                this.numSections = boundingBox.getXSpan() / 5;
            }
        }

        static BoundingBox findCorridorSize(List<MineshaftPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction) {
            for (var corridorLength = random.nextInt(3) + 2; corridorLength > 0; corridorLength--) {
                var blockLength = corridorLength * 5;
                var box = switch (direction) {
                    case SOUTH -> new BoundingBox(0, 0, 0, 2, 2, blockLength - 1);
                    case WEST -> new BoundingBox(-(blockLength - 1), 0, 0, 0, 2, 2);
                    case EAST -> new BoundingBox(0, 0, 0, blockLength - 1, 2, 2);
                    default -> new BoundingBox(0, 0, -(blockLength - 1), 2, 2, 0);
                };

                box = box.moved(footX, footY, footZ);
                if (!hasCollision(pieces, box)) {
                    return box;
                }
            }

            return null;
        }

        @Override
        void addChildren(MineshaftPiece startPiece, List<MineshaftPiece> pieces, RandomSource random) {
            var depth = this.genDepth;
            var endSelection = random.nextInt(4);
            var orientation = this.getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case SOUTH -> {
                        if (endSelection <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.maxZ() + 1, orientation, depth);
                        } else if (endSelection == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.maxZ() - 3, Direction.WEST, depth);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.maxZ() - 3, Direction.EAST, depth);
                        }
                    }
                    case WEST -> {
                        if (endSelection <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.minZ(), orientation, depth);
                        } else if (endSelection == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.minZ() - 1, Direction.NORTH, depth);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.maxZ() + 1, Direction.SOUTH, depth);
                        }
                    }
                    case EAST -> {
                        if (endSelection <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.minZ(), orientation, depth);
                        } else if (endSelection == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.maxX() - 3, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.minZ() - 1, Direction.NORTH, depth);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.maxX() - 3, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.maxZ() + 1, Direction.SOUTH, depth);
                        }
                    }
                    default -> {
                        if (endSelection <= 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX(), this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.minZ() - 1, orientation, depth);
                        } else if (endSelection == 2) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX() - 1, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.minZ(), Direction.WEST, depth);
                        } else {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.maxX() + 1, this.boundingBox.minY() - 1 + random.nextInt(3),
                                    this.boundingBox.minZ(), Direction.EAST, depth);
                        }
                    }
                }
            }

            if (depth < MAX_DEPTH) {
                if (orientation != Direction.NORTH && orientation != Direction.SOUTH) {
                    for (var x = this.boundingBox.minX() + 3; x + 3 <= this.boundingBox.maxX(); x += 5) {
                        var selection = random.nextInt(5);
                        if (selection == 0) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    x, this.boundingBox.minY(), this.boundingBox.minZ() - 1, Direction.NORTH, depth + 1);
                        } else if (selection == 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    x, this.boundingBox.minY(), this.boundingBox.maxZ() + 1, Direction.SOUTH, depth + 1);
                        }
                    }
                } else {
                    for (var z = this.boundingBox.minZ() + 3; z + 3 <= this.boundingBox.maxZ(); z += 5) {
                        var selection = random.nextInt(5);
                        if (selection == 0) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.minX() - 1, this.boundingBox.minY(), z, Direction.WEST, depth + 1);
                        } else if (selection == 1) {
                            MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                                    this.boundingBox.maxX() + 1, this.boundingBox.minY(), z, Direction.EAST, depth + 1);
                        }
                    }
                }
            }
        }

        @Override
        void postProcess(MineshaftLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.isInInvalidLocation(level, chunkBB)) {
                return;
            }

            var length = this.numSections * 5 - 1;
            var planks = this.type.planksState();
            this.generateBox(level, chunkBB, 0, 0, 0, 2, 1, length, CAVE_AIR, CAVE_AIR, false);
            this.generateMaybeBox(level, chunkBB, random, 0.8F, 0, 2, 0, 2, 2, length, CAVE_AIR, CAVE_AIR, false, false);
            if (this.spiderCorridor) {
                this.generateMaybeBox(level, chunkBB, random, 0.6F, 0, 0, 0, 2, 1, length, Block.COBWEB, CAVE_AIR, false, true);
            }

            for (var section = 0; section < this.numSections; section++) {
                var z = 2 + section * 5;
                this.placeSupport(level, chunkBB, 0, 0, z, 2, 2, random);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 0, 2, z - 1);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 2, 2, z - 1);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 0, 2, z + 1);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.1F, 2, 2, z + 1);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 0, 2, z - 2);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 2, 2, z - 2);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 0, 2, z + 2);
                this.maybePlaceCobWeb(level, chunkBB, random, 0.05F, 2, 2, z + 2);
                if (random.nextInt(100) == 0) {
                    this.createChest(level, chunkBB, random, 2, 0, z - 1);
                }

                if (random.nextInt(100) == 0) {
                    this.createChest(level, chunkBB, random, 0, 0, z + 1);
                }

                if (this.spiderCorridor && !this.hasPlacedSpider) {
                    var spiderZ = z - 1 + random.nextInt(3);
                    var wx = this.worldX(1, spiderZ);
                    var wy = this.worldY(0);
                    var wz = this.worldZ(1, spiderZ);
                    if (isInside(chunkBB, wx, wy, wz) && this.isInterior(level, 1, 0, spiderZ, chunkBB)) {
                        this.hasPlacedSpider = true;
                        level.setBlock(wx, wy, wz, Block.SPAWNER);
                    }
                }
            }

            for (var x = 0; x <= 2; x++) {
                for (var z = 0; z <= length; z++) {
                    this.setPlanksBlock(level, chunkBB, planks, x, -1, z);
                }
            }

            this.placeDoubleLowerOrUpperSupport(level, chunkBB, 0, -1, 2);
            if (this.numSections > 1) {
                this.placeDoubleLowerOrUpperSupport(level, chunkBB, 0, -1, length - 2);
            }

            if (this.hasRails) {
                var rail = Block.RAIL.withProperty("shape", "north_south");

                for (var z = 0; z <= length; z++) {
                    var floor = this.getBlock(level, 1, -1, z, chunkBB);
                    if (!floor.isAir() && isSolidRender(floor)) {
                        var probability = this.isInterior(level, 1, 0, z, chunkBB) ? 0.7F : 0.9F;
                        this.maybeGenerateBlock(level, chunkBB, random, probability, 1, 0, z, rail);
                    }
                }
            }
        }

        /**
         * Corridor chest: a rail block plus a loot minecart entity. Only the
         * rail matters for block parity; the loot seed random calls are kept.
         */
        private boolean createChest(MineshaftLevel level, BoundingBox chunkBB, RandomSource random, int x, int y, int z) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (isInside(chunkBB, wx, wy, wz) && level.getBlock(wx, wy, wz).isAir()
                    && !level.getBlock(wx, wy - 1, wz).isAir()) {
                var rail = Block.RAIL.withProperty("shape", random.nextBoolean() ? "north_south" : "east_west");
                this.placeBlock(level, rail, x, y, z, chunkBB);
                random.nextLong(); // minecart chest loot table seed
                return true;
            }

            return false;
        }

        private void placeDoubleLowerOrUpperSupport(MineshaftLevel level, BoundingBox chunkBB, int x, int y, int z) {
            var wood = this.type.woodState();
            var planks = this.type.planksState();
            if (this.getBlock(level, x, y, z, chunkBB).compare(planks)) {
                this.fillPillarDownOrChainUp(level, wood, x, y, z, chunkBB);
            }

            if (this.getBlock(level, x + 2, y, z, chunkBB).compare(planks)) {
                this.fillPillarDownOrChainUp(level, wood, x + 2, y, z, chunkBB);
            }
        }

        private void fillPillarDownOrChainUp(MineshaftLevel level, Block pillarBlock, int x, int y, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var worldY = this.worldY(y);
            var wz = this.worldZ(x, z);
            if (!isInside(chunkBB, wx, worldY, wz)) {
                return;
            }

            var distance = 1;
            var checkBelow = true;
            var checkAbove = true;
            while (checkBelow || checkAbove) {
                if (checkBelow) {
                    var yBelow = worldY - distance;
                    var belowState = level.getBlock(wx, yBelow, wz);
                    var emptyBelow = isReplaceableByStructures(belowState) && !belowState.compare(Block.LAVA);
                    if (!emptyBelow && isFaceSturdy(belowState)) {
                        fillColumnBetween(level, pillarBlock, wx, wz, yBelow + 1, worldY);
                        return;
                    }

                    checkBelow = distance <= MAX_PILLAR_HEIGHT && emptyBelow && yBelow > level.minY() + 1;
                }

                if (checkAbove) {
                    var yAbove = worldY + distance;
                    var aboveState = level.getBlock(wx, yAbove, wz);
                    var emptyAbove = isReplaceableByStructures(aboveState);
                    if (!emptyAbove && canHangChainBelow(aboveState)) {
                        level.setBlock(wx, worldY + 1, wz, this.type.fenceState());
                        fillColumnBetween(level, Block.IRON_CHAIN, wx, wz, worldY + 2, worldY + distance);
                        return;
                    }

                    checkAbove = distance <= MAX_CHAIN_HEIGHT && emptyAbove && yAbove < level.maxY();
                }

                distance++;
            }
        }

        private static void fillColumnBetween(MineshaftLevel level, Block pillarBlock, int x, int z,
                int bottomInclusive, int topExclusive) {
            for (var y = bottomInclusive; y < topExclusive; y++) {
                level.setBlock(x, y, z, pillarBlock);
            }
        }

        private static boolean canHangChainBelow(Block block) {
            return block.isSolid() && !isFallingBlock(block);
        }

        private void placeSupport(MineshaftLevel level, BoundingBox chunkBB, int x0, int y0, int z, int y1, int x1,
                RandomSource random) {
            if (!this.isSupportingBox(level, chunkBB, x0, x1, y1, z)) {
                return;
            }

            var planks = this.type.planksState();
            var fence = this.type.fenceState();
            this.generateBox(level, chunkBB, x0, y0, z, x0, y1 - 1, z, fence.withProperty("west", "true"), CAVE_AIR, false);
            this.generateBox(level, chunkBB, x1, y0, z, x1, y1 - 1, z, fence.withProperty("east", "true"), CAVE_AIR, false);
            if (random.nextInt(4) == 0) {
                this.generateBox(level, chunkBB, x0, y1, z, x0, y1, z, planks, CAVE_AIR, false);
                this.generateBox(level, chunkBB, x1, y1, z, x1, y1, z, planks, CAVE_AIR, false);
            } else {
                this.generateBox(level, chunkBB, x0, y1, z, x1, y1, z, planks, CAVE_AIR, false);
                this.maybeGenerateBlock(level, chunkBB, random, 0.05F, x0 + 1, y1, z - 1,
                        Block.WALL_TORCH.withProperty("facing", "south"));
                this.maybeGenerateBlock(level, chunkBB, random, 0.05F, x0 + 1, y1, z + 1,
                        Block.WALL_TORCH.withProperty("facing", "north"));
            }
        }

        private void maybePlaceCobWeb(MineshaftLevel level, BoundingBox chunkBB, RandomSource random,
                float probability, int x, int y, int z) {
            if (this.isInterior(level, x, y, z, chunkBB) && random.nextFloat() < probability
                    && this.hasSturdyNeighbours(level, chunkBB, x, y, z, 2)) {
                this.placeBlock(level, Block.COBWEB, x, y, z, chunkBB);
            }
        }

        private boolean hasSturdyNeighbours(MineshaftLevel level, BoundingBox chunkBB, int x, int y, int z, int count) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(y);
            var wz = this.worldZ(x, z);
            var sturdyNeighbours = 0;

            for (var direction : Direction.values()) {
                var nx = wx + direction.stepX();
                var ny = wy + direction.stepY();
                var nz = wz + direction.stepZ();
                if (isInside(chunkBB, nx, ny, nz) && isFaceSturdy(level.getBlock(nx, ny, nz))) {
                    if (++sturdyNeighbours >= count) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    public static final class Crossing extends MineshaftPiece {
        private final Direction direction;
        private final boolean isTwoFloored;

        Crossing(int genDepth, BoundingBox boundingBox, Direction direction, MineshaftType type) {
            super(genDepth, type, boundingBox);
            this.direction = direction;
            this.isTwoFloored = boundingBox.getYSpan() > 3;
        }

        static BoundingBox findCrossing(List<MineshaftPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction) {
            int y1;
            if (random.nextInt(4) == 0) {
                y1 = 6;
            } else {
                y1 = 2;
            }

            var box = switch (direction) {
                case SOUTH -> new BoundingBox(-1, 0, 0, 3, y1, 4);
                case WEST -> new BoundingBox(-4, 0, -1, 0, y1, 3);
                case EAST -> new BoundingBox(0, 0, -1, 4, y1, 3);
                default -> new BoundingBox(-1, 0, -4, 3, y1, 0);
            };

            box = box.moved(footX, footY, footZ);
            return hasCollision(pieces, box) ? null : box;
        }

        @Override
        void addChildren(MineshaftPiece startPiece, List<MineshaftPiece> pieces, RandomSource random) {
            var depth = this.genDepth;
            switch (this.direction) {
                case SOUTH -> {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1,
                            Direction.SOUTH, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                            Direction.WEST, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                            Direction.EAST, depth);
                }
                case WEST -> {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1,
                            Direction.NORTH, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1,
                            Direction.SOUTH, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                            Direction.WEST, depth);
                }
                case EAST -> {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1,
                            Direction.NORTH, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.maxZ() + 1,
                            Direction.SOUTH, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                            Direction.EAST, depth);
                }
                default -> {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() - 1,
                            Direction.NORTH, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                            Direction.WEST, depth);
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                            Direction.EAST, depth);
                }
            }

            if (this.isTwoFloored) {
                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() - 1,
                            Direction.NORTH, depth);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() - 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1,
                            Direction.WEST, depth);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.maxX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.minZ() + 1,
                            Direction.EAST, depth);
                }

                if (random.nextBoolean()) {
                    MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() + 1, this.boundingBox.minY() + 3 + 1, this.boundingBox.maxZ() + 1,
                            Direction.SOUTH, depth);
                }
            }
        }

        @Override
        void postProcess(MineshaftLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.isInInvalidLocation(level, chunkBB)) {
                return;
            }

            var planks = this.type.planksState();
            if (this.isTwoFloored) {
                this.generateBox(level, chunkBB,
                        this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1, this.boundingBox.minY() + 3 - 1, this.boundingBox.maxZ(),
                        CAVE_AIR, CAVE_AIR, false);
                this.generateBox(level, chunkBB,
                        this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(), this.boundingBox.minY() + 3 - 1, this.boundingBox.maxZ() - 1,
                        CAVE_AIR, CAVE_AIR, false);
                this.generateBox(level, chunkBB,
                        this.boundingBox.minX() + 1, this.boundingBox.maxY() - 2, this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1, this.boundingBox.maxY(), this.boundingBox.maxZ(),
                        CAVE_AIR, CAVE_AIR, false);
                this.generateBox(level, chunkBB,
                        this.boundingBox.minX(), this.boundingBox.maxY() - 2, this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ() - 1,
                        CAVE_AIR, CAVE_AIR, false);
                this.generateBox(level, chunkBB,
                        this.boundingBox.minX() + 1, this.boundingBox.minY() + 3, this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX() - 1, this.boundingBox.minY() + 3, this.boundingBox.maxZ() - 1,
                        CAVE_AIR, CAVE_AIR, false);
            } else {
                this.generateBox(level, chunkBB,
                        this.boundingBox.minX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(),
                        this.boundingBox.maxX() - 1, this.boundingBox.maxY(), this.boundingBox.maxZ(),
                        CAVE_AIR, CAVE_AIR, false);
                this.generateBox(level, chunkBB,
                        this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() + 1,
                        this.boundingBox.maxX(), this.boundingBox.maxY(), this.boundingBox.maxZ() - 1,
                        CAVE_AIR, CAVE_AIR, false);
            }

            this.placeSupportPillar(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.minY(),
                    this.boundingBox.minZ() + 1, this.boundingBox.maxY());
            this.placeSupportPillar(level, chunkBB, this.boundingBox.minX() + 1, this.boundingBox.minY(),
                    this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
            this.placeSupportPillar(level, chunkBB, this.boundingBox.maxX() - 1, this.boundingBox.minY(),
                    this.boundingBox.minZ() + 1, this.boundingBox.maxY());
            this.placeSupportPillar(level, chunkBB, this.boundingBox.maxX() - 1, this.boundingBox.minY(),
                    this.boundingBox.maxZ() - 1, this.boundingBox.maxY());
            var y = this.boundingBox.minY() - 1;

            for (var x = this.boundingBox.minX(); x <= this.boundingBox.maxX(); x++) {
                for (var z = this.boundingBox.minZ(); z <= this.boundingBox.maxZ(); z++) {
                    this.setPlanksBlock(level, chunkBB, planks, x, y, z);
                }
            }
        }

        private void placeSupportPillar(MineshaftLevel level, BoundingBox chunkBB, int x, int y0, int z, int y1) {
            if (!this.getBlock(level, x, y1 + 1, z, chunkBB).isAir()) {
                this.generateBox(level, chunkBB, x, y0, z, x, y1, z, this.type.planksState(), CAVE_AIR, false);
            }
        }
    }

    public static final class Stairs extends MineshaftPiece {
        Stairs(int genDepth, BoundingBox boundingBox, Direction direction, MineshaftType type) {
            super(genDepth, type, boundingBox);
            this.setOrientation(direction);
        }

        static BoundingBox findStairs(List<MineshaftPiece> pieces, RandomSource random,
                int footX, int footY, int footZ, Direction direction) {
            var box = switch (direction) {
                case SOUTH -> new BoundingBox(0, -5, 0, 2, 2, 8);
                case WEST -> new BoundingBox(-8, -5, 0, 0, 2, 2);
                case EAST -> new BoundingBox(0, -5, 0, 8, 2, 2);
                default -> new BoundingBox(0, -5, -8, 2, 2, 0);
            };

            box = box.moved(footX, footY, footZ);
            return hasCollision(pieces, box) ? null : box;
        }

        @Override
        void addChildren(MineshaftPiece startPiece, List<MineshaftPiece> pieces, RandomSource random) {
            var depth = this.genDepth;
            var orientation = this.getOrientation();
            if (orientation != null) {
                switch (orientation) {
                    case SOUTH -> MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.maxZ() + 1,
                            Direction.SOUTH, depth);
                    case WEST -> MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX() - 1, this.boundingBox.minY(), this.boundingBox.minZ(),
                            Direction.WEST, depth);
                    case EAST -> MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.maxX() + 1, this.boundingBox.minY(), this.boundingBox.minZ(),
                            Direction.EAST, depth);
                    default -> MineshaftPieces.generateAndAddPiece(startPiece, pieces, random,
                            this.boundingBox.minX(), this.boundingBox.minY(), this.boundingBox.minZ() - 1,
                            Direction.NORTH, depth);
                }
            }
        }

        @Override
        void postProcess(MineshaftLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.isInInvalidLocation(level, chunkBB)) {
                return;
            }

            this.generateBox(level, chunkBB, 0, 5, 0, 2, 7, 1, CAVE_AIR, CAVE_AIR, false);
            this.generateBox(level, chunkBB, 0, 0, 7, 2, 2, 8, CAVE_AIR, CAVE_AIR, false);

            for (var i = 0; i < 5; i++) {
                this.generateBox(level, chunkBB, 0, 5 - i - (i < 4 ? 1 : 0), 2 + i, 2, 7 - i, 2 + i,
                        CAVE_AIR, CAVE_AIR, false);
            }
        }
    }
}
