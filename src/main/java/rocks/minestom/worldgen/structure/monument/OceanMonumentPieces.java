package rocks.minestom.worldgen.structure.monument;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.StructureRng;
import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Port of vanilla {@code OceanMonumentPieces}: the room-graph generator and
 * piece set that make up an ocean monument. Random call order matches
 * vanilla exactly so a seeded {@code WorldgenRandom} reproduces vanilla room
 * graphs and decoration block-for-block, mirroring
 * {@code rocks.minestom.worldgen.structure.fortress.NetherFortressPieces}.
 *
 * <p>One deviation from vanilla: elder guardians are entities and are not
 * spawned by {@link MonumentPiece#spawnElder}. Vanilla's {@code spawnElder}
 * consumes no randomness (entity placement is deterministic from block
 * position alone), so omitting it does not desynchronize any later draw.
 */
public final class OceanMonumentPieces {
    private OceanMonumentPieces() {
    }

    /** Vanilla {@code OceanMonumentStructure.createTopPiece}. */
    public static MonumentBuilding generateBuilding(RandomSource random, int west, int north, Direction direction) {
        return new MonumentBuilding(random, west, north, direction);
    }

    private interface MonumentRoomFitter {
        boolean fits(RoomDefinition definition);

        MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random);
    }

    private static final class FitDoubleXRoom implements MonumentRoomFitter {
        @Override
        public boolean fits(RoomDefinition definition) {
            return definition.hasOpening[Direction.EAST.ordinal()] && !definition.connections[Direction.EAST.ordinal()].claimed;
        }

        @Override
        public MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.EAST.ordinal()].claimed = true;
            return new OceanMonumentDoubleXRoom(orientation, definition);
        }
    }

    private static final class FitDoubleXYRoom implements MonumentRoomFitter {
        @Override
        public boolean fits(RoomDefinition definition) {
            if (definition.hasOpening[Direction.EAST.ordinal()]
                    && !definition.connections[Direction.EAST.ordinal()].claimed
                    && definition.hasOpening[Direction.UP.ordinal()]
                    && !definition.connections[Direction.UP.ordinal()].claimed) {
                var east = definition.connections[Direction.EAST.ordinal()];
                return east.hasOpening[Direction.UP.ordinal()] && !east.connections[Direction.UP.ordinal()].claimed;
            }
            return false;
        }

        @Override
        public MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.EAST.ordinal()].claimed = true;
            definition.connections[Direction.UP.ordinal()].claimed = true;
            definition.connections[Direction.EAST.ordinal()].connections[Direction.UP.ordinal()].claimed = true;
            return new OceanMonumentDoubleXYRoom(orientation, definition);
        }
    }

    private static final class FitDoubleYRoom implements MonumentRoomFitter {
        @Override
        public boolean fits(RoomDefinition definition) {
            return definition.hasOpening[Direction.UP.ordinal()] && !definition.connections[Direction.UP.ordinal()].claimed;
        }

        @Override
        public MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.UP.ordinal()].claimed = true;
            return new OceanMonumentDoubleYRoom(orientation, definition);
        }
    }

    private static final class FitDoubleYZRoom implements MonumentRoomFitter {
        @Override
        public boolean fits(RoomDefinition definition) {
            if (definition.hasOpening[Direction.NORTH.ordinal()]
                    && !definition.connections[Direction.NORTH.ordinal()].claimed
                    && definition.hasOpening[Direction.UP.ordinal()]
                    && !definition.connections[Direction.UP.ordinal()].claimed) {
                var north = definition.connections[Direction.NORTH.ordinal()];
                return north.hasOpening[Direction.UP.ordinal()] && !north.connections[Direction.UP.ordinal()].claimed;
            }
            return false;
        }

        @Override
        public MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            definition.connections[Direction.NORTH.ordinal()].claimed = true;
            definition.connections[Direction.UP.ordinal()].claimed = true;
            definition.connections[Direction.NORTH.ordinal()].connections[Direction.UP.ordinal()].claimed = true;
            return new OceanMonumentDoubleYZRoom(orientation, definition);
        }
    }

    private static final class FitDoubleZRoom implements MonumentRoomFitter {
        @Override
        public boolean fits(RoomDefinition definition) {
            return definition.hasOpening[Direction.NORTH.ordinal()] && !definition.connections[Direction.NORTH.ordinal()].claimed;
        }

        @Override
        public MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random) {
            var source = definition;
            if (!definition.hasOpening[Direction.NORTH.ordinal()] || definition.connections[Direction.NORTH.ordinal()].claimed) {
                source = definition.connections[Direction.SOUTH.ordinal()];
            }
            source.claimed = true;
            source.connections[Direction.NORTH.ordinal()].claimed = true;
            return new OceanMonumentDoubleZRoom(orientation, source);
        }
    }

    private static final class FitSimpleRoom implements MonumentRoomFitter {
        @Override
        public boolean fits(RoomDefinition definition) {
            return true;
        }

        @Override
        public MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            return new OceanMonumentSimpleRoom(orientation, definition, random);
        }
    }

    private static final class FitSimpleTopRoom implements MonumentRoomFitter {
        @Override
        public boolean fits(RoomDefinition definition) {
            return !definition.hasOpening[Direction.WEST.ordinal()]
                    && !definition.hasOpening[Direction.EAST.ordinal()]
                    && !definition.hasOpening[Direction.NORTH.ordinal()]
                    && !definition.hasOpening[Direction.SOUTH.ordinal()]
                    && !definition.hasOpening[Direction.UP.ordinal()];
        }

        @Override
        public MonumentPiece create(Direction orientation, RoomDefinition definition, RandomSource random) {
            definition.claimed = true;
            return new OceanMonumentSimpleTopRoom(orientation, definition);
        }
    }

    public static final class MonumentBuilding extends MonumentPiece {
        private static final int GRIDROOM_SOURCE_INDEX = getRoomIndex(2, 0, 0);
        private static final int GRIDROOM_TOP_CONNECT_INDEX = getRoomIndex(2, 2, 0);
        private static final int GRIDROOM_LEFTWING_CONNECT_INDEX = getRoomIndex(0, 1, 0);
        private static final int GRIDROOM_RIGHTWING_CONNECT_INDEX = getRoomIndex(4, 1, 0);

        private RoomDefinition sourceRoom;
        private RoomDefinition coreRoom;
        private final List<MonumentPiece> childPieces = new ArrayList<>();

        MonumentBuilding(RandomSource random, int west, int north, Direction direction) {
            super(direction, 0, makeBoundingBox(west, 39, north, direction, 58, 23, 58));
            this.setOrientation(direction);
            var roomDefinitions = this.generateRoomGraph(random);
            this.sourceRoom.claimed = true;
            this.childPieces.add(new OceanMonumentEntryRoom(direction, this.sourceRoom));
            this.childPieces.add(new OceanMonumentCoreRoom(direction, this.coreRoom));

            var fitters = new ArrayList<MonumentRoomFitter>();
            fitters.add(new FitDoubleXYRoom());
            fitters.add(new FitDoubleYZRoom());
            fitters.add(new FitDoubleZRoom());
            fitters.add(new FitDoubleXRoom());
            fitters.add(new FitDoubleYRoom());
            fitters.add(new FitSimpleTopRoom());
            fitters.add(new FitSimpleRoom());

            for (var definition : roomDefinitions) {
                if (!definition.claimed && !definition.isSpecial()) {
                    for (var fitter : fitters) {
                        if (fitter.fits(definition)) {
                            this.childPieces.add(fitter.create(direction, definition, random));
                            break;
                        }
                    }
                }
            }

            var offset = this.getWorldPos(9, 0, 22);
            for (var child : this.childPieces) {
                child.boundingBox.move(offset);
            }

            var leftWing = BoundingBox.fromCorners(this.getWorldPos(1, 1, 1), this.getWorldPos(23, 8, 21));
            var rightWing = BoundingBox.fromCorners(this.getWorldPos(34, 1, 1), this.getWorldPos(56, 8, 21));
            var penthouse = BoundingBox.fromCorners(this.getWorldPos(22, 13, 22), this.getWorldPos(35, 17, 35));
            var wingRandom = random.nextInt();
            this.childPieces.add(new OceanMonumentWingRoom(direction, leftWing, wingRandom++));
            this.childPieces.add(new OceanMonumentWingRoom(direction, rightWing, wingRandom++));
            this.childPieces.add(new OceanMonumentPenthouse(direction, penthouse));
        }

        public List<MonumentPiece> childPieces() {
            return this.childPieces;
        }

        /** Vanilla {@code MonumentBuilding.generateRoomGraph}. */
        private List<RoomDefinition> generateRoomGraph(RandomSource random) {
            var roomGrid = new RoomDefinition[75];

            for (var x = 0; x < 5; x++) {
                for (var z = 0; z < 4; z++) {
                    var pos = getRoomIndex(x, 0, z);
                    roomGrid[pos] = new RoomDefinition(pos);
                }
            }

            for (var x = 0; x < 5; x++) {
                for (var z = 0; z < 4; z++) {
                    var pos = getRoomIndex(x, 1, z);
                    roomGrid[pos] = new RoomDefinition(pos);
                }
            }

            for (var x = 1; x < 4; x++) {
                for (var z = 0; z < 2; z++) {
                    var pos = getRoomIndex(x, 2, z);
                    roomGrid[pos] = new RoomDefinition(pos);
                }
            }

            this.sourceRoom = roomGrid[GRIDROOM_SOURCE_INDEX];

            for (var x = 0; x < 5; x++) {
                for (var z = 0; z < 5; z++) {
                    for (var y = 0; y < 3; y++) {
                        var pos = getRoomIndex(x, y, z);
                        if (roomGrid[pos] != null) {
                            for (var direction : Direction.values()) {
                                var neighX = x + direction.stepX();
                                var neighY = y + direction.stepY();
                                var neighZ = z + direction.stepZ();
                                if (neighX >= 0 && neighX < 5 && neighZ >= 0 && neighZ < 5 && neighY >= 0 && neighY < 3) {
                                    var neighPos = getRoomIndex(neighX, neighY, neighZ);
                                    if (roomGrid[neighPos] != null) {
                                        if (neighZ == z) {
                                            roomGrid[pos].setConnection(direction, roomGrid[neighPos]);
                                        } else {
                                            roomGrid[pos].setConnection(direction.opposite(), roomGrid[neighPos]);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            var roofRoom = new RoomDefinition(1003);
            var leftWing = new RoomDefinition(1001);
            var rightWing = new RoomDefinition(1002);
            roomGrid[GRIDROOM_TOP_CONNECT_INDEX].setConnection(Direction.UP, roofRoom);
            roomGrid[GRIDROOM_LEFTWING_CONNECT_INDEX].setConnection(Direction.SOUTH, leftWing);
            roomGrid[GRIDROOM_RIGHTWING_CONNECT_INDEX].setConnection(Direction.SOUTH, rightWing);
            roofRoom.claimed = true;
            leftWing.claimed = true;
            rightWing.claimed = true;
            this.sourceRoom.isSource = true;
            this.coreRoom = roomGrid[getRoomIndex(random.nextInt(4), 0, 2)];
            this.coreRoom.claimed = true;
            this.coreRoom.connections[Direction.EAST.ordinal()].claimed = true;
            this.coreRoom.connections[Direction.NORTH.ordinal()].claimed = true;
            this.coreRoom.connections[Direction.EAST.ordinal()].connections[Direction.NORTH.ordinal()].claimed = true;
            this.coreRoom.connections[Direction.UP.ordinal()].claimed = true;
            this.coreRoom.connections[Direction.EAST.ordinal()].connections[Direction.UP.ordinal()].claimed = true;
            this.coreRoom.connections[Direction.NORTH.ordinal()].connections[Direction.UP.ordinal()].claimed = true;
            this.coreRoom.connections[Direction.EAST.ordinal()].connections[Direction.NORTH.ordinal()].connections[Direction.UP.ordinal()].claimed = true;

            var roomDefs = new ArrayList<RoomDefinition>();
            for (var definition : roomGrid) {
                if (definition != null) {
                    definition.updateOpenings();
                    roomDefs.add(definition);
                }
            }

            roofRoom.updateOpenings();
            StructureRng.shuffle(roomDefs, random);
            var scanIndex = 1;

            for (var definitionx : roomDefs) {
                var closeCount = 0;
                var attemptCount = 0;

                while (closeCount < 2 && attemptCount < 5) {
                    attemptCount++;
                    var f = random.nextInt(6);
                    if (definitionx.hasOpening[f]) {
                        var of = Direction.values()[f].opposite().ordinal();
                        definitionx.hasOpening[f] = false;
                        definitionx.connections[f].hasOpening[of] = false;
                        if (definitionx.findSource(scanIndex++) && definitionx.connections[f].findSource(scanIndex++)) {
                            closeCount++;
                        } else {
                            definitionx.hasOpening[f] = true;
                            definitionx.connections[f].hasOpening[of] = true;
                        }
                    }
                }
            }

            roomDefs.add(roofRoom);
            roomDefs.add(leftWing);
            roomDefs.add(rightWing);
            return roomDefs;
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            var waterHeight = Math.max(level.seaLevel(), 64) - this.boundingBox.minY();
            this.generateWaterBox(level, chunkBB, 0, 0, 0, 58, waterHeight, 58);
            this.generateWing(false, 0, level, chunkBB);
            this.generateWing(true, 33, level, chunkBB);
            this.generateEntranceArchs(level, chunkBB);
            this.generateEntranceWall(level, chunkBB);
            this.generateRoofPiece(level, chunkBB);
            this.generateLowerWall(level, chunkBB);
            this.generateMiddleWall(level, chunkBB);
            this.generateUpperWall(level, chunkBB);

            for (var pillarX = 0; pillarX < 7; pillarX++) {
                var pillarZ = 0;

                while (pillarZ < 7) {
                    if (pillarZ == 0 && pillarX == 3) {
                        pillarZ = 6;
                    }

                    var bx = pillarX * 9;
                    var bz = pillarZ * 9;

                    for (var w = 0; w < 4; w++) {
                        for (var d = 0; d < 4; d++) {
                            this.placeBlock(level, BASE_LIGHT, bx + w, 0, bz + d, chunkBB);
                            this.fillColumnDown(level, BASE_LIGHT, bx + w, -1, bz + d, chunkBB);
                        }
                    }

                    if (pillarX != 0 && pillarX != 6) {
                        pillarZ += 6;
                    } else {
                        pillarZ++;
                    }
                }
            }

            for (var i = 0; i < 5; i++) {
                this.generateWaterBox(level, chunkBB, -1 - i, 0 + i * 2, -1 - i, -1 - i, 23, 58 + i);
                this.generateWaterBox(level, chunkBB, 58 + i, 0 + i * 2, -1 - i, 58 + i, 23, 58 + i);
                this.generateWaterBox(level, chunkBB, 0 - i, 0 + i * 2, -1 - i, 57 + i, 23, -1 - i);
                this.generateWaterBox(level, chunkBB, 0 - i, 0 + i * 2, 58 + i, 57 + i, 23, 58 + i);
            }

            for (var child : this.childPieces) {
                if (child.boundingBox.intersects(chunkBB)) {
                    child.postProcess(level, random, chunkBB);
                }
            }
        }

        private void generateWing(boolean isFlipped, int xoff, MonumentLevel level, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, xoff, 0, xoff + 23, 20)) {
                this.generateBox(level, chunkBB, xoff, 0, 0, xoff + 24, 0, 20, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, xoff, 1, 0, xoff + 24, 10, 20);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, xoff + i, i + 1, i, xoff + i, i + 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + i + 7, i + 5, i + 7, xoff + i + 7, i + 5, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + 17 - i, i + 5, i + 7, xoff + 17 - i, i + 5, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + 24 - i, i + 1, i, xoff + 24 - i, i + 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + i + 1, i + 1, i, xoff + 23 - i, i + 1, i, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xoff + i + 8, i + 5, i + 7, xoff + 16 - i, i + 5, i + 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, xoff + 4, 4, 4, xoff + 6, 4, 20, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 7, 4, 4, xoff + 17, 4, 6, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 18, 4, 4, xoff + 20, 4, 20, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 11, 8, 11, xoff + 13, 8, 20, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, DOT_DECO_DATA, xoff + 12, 9, 12, chunkBB);
                this.placeBlock(level, DOT_DECO_DATA, xoff + 12, 9, 15, chunkBB);
                this.placeBlock(level, DOT_DECO_DATA, xoff + 12, 9, 18, chunkBB);
                var leftPos = xoff + (isFlipped ? 19 : 5);
                var rightPos = xoff + (isFlipped ? 5 : 19);

                for (var z = 20; z >= 5; z -= 3) {
                    this.placeBlock(level, DOT_DECO_DATA, leftPos, 5, z, chunkBB);
                }

                for (var z = 19; z >= 7; z -= 3) {
                    this.placeBlock(level, DOT_DECO_DATA, rightPos, 5, z, chunkBB);
                }

                for (var i = 0; i < 4; i++) {
                    var pos = isFlipped ? xoff + 24 - (17 - i * 3) : xoff + 17 - i * 3;
                    this.placeBlock(level, DOT_DECO_DATA, pos, 5, 5, chunkBB);
                }

                this.placeBlock(level, DOT_DECO_DATA, rightPos, 5, 5, chunkBB);
                this.generateBox(level, chunkBB, xoff + 11, 1, 12, xoff + 13, 7, 12, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xoff + 12, 1, 11, xoff + 12, 7, 13, BASE_GRAY, BASE_GRAY, false);
            }
        }

        private void generateEntranceArchs(MonumentLevel level, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 22, 5, 35, 17)) {
                this.generateWaterBox(level, chunkBB, 25, 0, 0, 32, 8, 20);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, 24, 2, 5 + i * 4, 24, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 22, 4, 5 + i * 4, 23, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.placeBlock(level, BASE_LIGHT, 25, 5, 5 + i * 4, chunkBB);
                    this.placeBlock(level, BASE_LIGHT, 26, 6, 5 + i * 4, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, 26, 5, 5 + i * 4, chunkBB);
                    this.generateBox(level, chunkBB, 33, 2, 5 + i * 4, 33, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 34, 4, 5 + i * 4, 35, 4, 5 + i * 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.placeBlock(level, BASE_LIGHT, 32, 5, 5 + i * 4, chunkBB);
                    this.placeBlock(level, BASE_LIGHT, 31, 6, 5 + i * 4, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, 31, 5, 5 + i * 4, chunkBB);
                    this.generateBox(level, chunkBB, 27, 6, 5 + i * 4, 30, 6, 5 + i * 4, BASE_GRAY, BASE_GRAY, false);
                }
            }
        }

        private void generateEntranceWall(MonumentLevel level, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 15, 20, 42, 21)) {
                this.generateBox(level, chunkBB, 15, 0, 21, 42, 0, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 26, 1, 21, 31, 3, 21);
                this.generateBox(level, chunkBB, 21, 12, 21, 36, 12, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 17, 11, 21, 40, 11, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 16, 10, 21, 41, 10, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 15, 7, 21, 42, 9, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 16, 6, 21, 41, 6, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 17, 5, 21, 40, 5, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 21, 4, 21, 36, 4, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 22, 3, 21, 26, 3, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 31, 3, 21, 35, 3, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 23, 2, 21, 25, 2, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 32, 2, 21, 34, 2, 21, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 28, 4, 20, 29, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, BASE_LIGHT, 27, 3, 21, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 30, 3, 21, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 26, 2, 21, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 31, 2, 21, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 25, 1, 21, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 32, 1, 21, chunkBB);

                for (var i = 0; i < 7; i++) {
                    this.placeBlock(level, BASE_BLACK, 28 - i, 6 + i, 21, chunkBB);
                    this.placeBlock(level, BASE_BLACK, 29 + i, 6 + i, 21, chunkBB);
                }

                for (var i = 0; i < 4; i++) {
                    this.placeBlock(level, BASE_BLACK, 28 - i, 9 + i, 21, chunkBB);
                    this.placeBlock(level, BASE_BLACK, 29 + i, 9 + i, 21, chunkBB);
                }

                this.placeBlock(level, BASE_BLACK, 28, 12, 21, chunkBB);
                this.placeBlock(level, BASE_BLACK, 29, 12, 21, chunkBB);

                for (var i = 0; i < 3; i++) {
                    this.placeBlock(level, BASE_BLACK, 22 - i * 2, 8, 21, chunkBB);
                    this.placeBlock(level, BASE_BLACK, 22 - i * 2, 9, 21, chunkBB);
                    this.placeBlock(level, BASE_BLACK, 35 + i * 2, 8, 21, chunkBB);
                    this.placeBlock(level, BASE_BLACK, 35 + i * 2, 9, 21, chunkBB);
                }

                this.generateWaterBox(level, chunkBB, 15, 13, 21, 42, 15, 21);
                this.generateWaterBox(level, chunkBB, 15, 1, 21, 15, 6, 21);
                this.generateWaterBox(level, chunkBB, 16, 1, 21, 16, 5, 21);
                this.generateWaterBox(level, chunkBB, 17, 1, 21, 20, 4, 21);
                this.generateWaterBox(level, chunkBB, 21, 1, 21, 21, 3, 21);
                this.generateWaterBox(level, chunkBB, 22, 1, 21, 22, 2, 21);
                this.generateWaterBox(level, chunkBB, 23, 1, 21, 24, 1, 21);
                this.generateWaterBox(level, chunkBB, 42, 1, 21, 42, 6, 21);
                this.generateWaterBox(level, chunkBB, 41, 1, 21, 41, 5, 21);
                this.generateWaterBox(level, chunkBB, 37, 1, 21, 40, 4, 21);
                this.generateWaterBox(level, chunkBB, 36, 1, 21, 36, 3, 21);
                this.generateWaterBox(level, chunkBB, 33, 1, 21, 34, 1, 21);
                this.generateWaterBox(level, chunkBB, 35, 1, 21, 35, 2, 21);
            }
        }

        private void generateRoofPiece(MonumentLevel level, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 21, 21, 36, 36)) {
                this.generateBox(level, chunkBB, 21, 0, 22, 36, 0, 36, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 21, 1, 22, 36, 23, 36);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, 21 + i, 13 + i, 21 + i, 36 - i, 13 + i, 21 + i, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 21 + i, 13 + i, 36 - i, 36 - i, 13 + i, 36 - i, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 21 + i, 13 + i, 22 + i, 21 + i, 13 + i, 35 - i, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 36 - i, 13 + i, 22 + i, 36 - i, 13 + i, 35 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, 25, 16, 25, 32, 16, 32, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 25, 17, 25, 25, 19, 25, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 32, 17, 25, 32, 19, 25, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 25, 17, 32, 25, 19, 32, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 32, 17, 32, 32, 19, 32, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, BASE_LIGHT, 26, 20, 26, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 27, 21, 27, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 27, 20, 27, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 26, 20, 31, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 27, 21, 30, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 27, 20, 30, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 31, 20, 31, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 30, 21, 30, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 30, 20, 30, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 31, 20, 26, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 30, 21, 27, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 30, 20, 27, chunkBB);
                this.generateBox(level, chunkBB, 28, 21, 27, 29, 21, 27, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 27, 21, 28, 27, 21, 29, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 28, 21, 30, 29, 21, 30, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 30, 21, 28, 30, 21, 29, BASE_GRAY, BASE_GRAY, false);
            }
        }

        private void generateLowerWall(MonumentLevel level, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 0, 21, 6, 58)) {
                this.generateBox(level, chunkBB, 0, 0, 21, 6, 0, 57, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 0, 1, 21, 6, 7, 57);
                this.generateBox(level, chunkBB, 4, 4, 21, 6, 4, 53, BASE_GRAY, BASE_GRAY, false);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, i, i + 1, 21, i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (var z = 23; z < 53; z += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 5, 5, z, chunkBB);
                }

                this.placeBlock(level, DOT_DECO_DATA, 5, 5, 52, chunkBB);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, i, i + 1, 21, i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, 4, 1, 52, 6, 3, 52, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 5, 1, 51, 5, 3, 53, BASE_GRAY, BASE_GRAY, false);
            }

            if (this.chunkIntersects(chunkBB, 51, 21, 58, 58)) {
                this.generateBox(level, chunkBB, 51, 0, 21, 57, 0, 57, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 51, 1, 21, 57, 7, 57);
                this.generateBox(level, chunkBB, 51, 4, 21, 53, 4, 53, BASE_GRAY, BASE_GRAY, false);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, 57 - i, i + 1, 21, 57 - i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (var z = 23; z < 53; z += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 52, 5, z, chunkBB);
                }

                this.placeBlock(level, DOT_DECO_DATA, 52, 5, 52, chunkBB);
                this.generateBox(level, chunkBB, 51, 1, 52, 53, 3, 52, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 52, 1, 51, 52, 3, 53, BASE_GRAY, BASE_GRAY, false);
            }

            if (this.chunkIntersects(chunkBB, 0, 51, 57, 57)) {
                this.generateBox(level, chunkBB, 7, 0, 51, 50, 0, 57, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 7, 1, 51, 50, 10, 57);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, i + 1, i + 1, 57 - i, 56 - i, i + 1, 57 - i, BASE_LIGHT, BASE_LIGHT, false);
                }
            }
        }

        private void generateMiddleWall(MonumentLevel level, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 7, 21, 13, 50)) {
                this.generateBox(level, chunkBB, 7, 0, 21, 13, 0, 50, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 7, 1, 21, 13, 10, 50);
                this.generateBox(level, chunkBB, 11, 8, 21, 13, 8, 53, BASE_GRAY, BASE_GRAY, false);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, i + 7, i + 5, 21, i + 7, i + 5, 54, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (var z = 21; z <= 45; z += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 12, 9, z, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 44, 21, 50, 54)) {
                this.generateBox(level, chunkBB, 44, 0, 21, 50, 0, 50, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 44, 1, 21, 50, 10, 50);
                this.generateBox(level, chunkBB, 44, 8, 21, 46, 8, 53, BASE_GRAY, BASE_GRAY, false);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, 50 - i, i + 5, 21, 50 - i, i + 5, 54, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (var z = 21; z <= 45; z += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 45, 9, z, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 8, 44, 49, 54)) {
                this.generateBox(level, chunkBB, 14, 0, 44, 43, 0, 50, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 14, 1, 44, 43, 10, 50);

                for (var x = 12; x <= 45; x += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, x, 9, 45, chunkBB);
                    this.placeBlock(level, DOT_DECO_DATA, x, 9, 52, chunkBB);
                    if (x == 12 || x == 18 || x == 24 || x == 33 || x == 39 || x == 45) {
                        this.placeBlock(level, DOT_DECO_DATA, x, 9, 47, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 9, 50, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 10, 45, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 10, 46, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 10, 51, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 10, 52, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 11, 47, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 11, 50, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 12, 48, chunkBB);
                        this.placeBlock(level, DOT_DECO_DATA, x, 12, 49, chunkBB);
                    }
                }

                for (var i = 0; i < 3; i++) {
                    this.generateBox(level, chunkBB, 8 + i, 5 + i, 54, 49 - i, 5 + i, 54, BASE_GRAY, BASE_GRAY, false);
                }

                this.generateBox(level, chunkBB, 11, 8, 54, 46, 8, 54, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 14, 8, 44, 43, 8, 53, BASE_GRAY, BASE_GRAY, false);
            }
        }

        private void generateUpperWall(MonumentLevel level, BoundingBox chunkBB) {
            if (this.chunkIntersects(chunkBB, 14, 21, 20, 43)) {
                this.generateBox(level, chunkBB, 14, 0, 21, 20, 0, 43, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 14, 1, 22, 20, 14, 43);
                this.generateBox(level, chunkBB, 18, 12, 22, 20, 12, 39, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 18, 12, 21, 20, 12, 21, BASE_LIGHT, BASE_LIGHT, false);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, i + 14, i + 9, 21, i + 14, i + 9, 43 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (var z = 23; z <= 39; z += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 19, 13, z, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 37, 21, 43, 43)) {
                this.generateBox(level, chunkBB, 37, 0, 21, 43, 0, 43, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 37, 1, 22, 43, 14, 43);
                this.generateBox(level, chunkBB, 37, 12, 22, 39, 12, 39, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 37, 12, 21, 39, 12, 21, BASE_LIGHT, BASE_LIGHT, false);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, 43 - i, i + 9, 21, 43 - i, i + 9, 43 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (var z = 23; z <= 39; z += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, 38, 13, z, chunkBB);
                }
            }

            if (this.chunkIntersects(chunkBB, 15, 37, 42, 43)) {
                this.generateBox(level, chunkBB, 21, 0, 37, 36, 0, 43, BASE_GRAY, BASE_GRAY, false);
                this.generateWaterBox(level, chunkBB, 21, 1, 37, 36, 14, 43);
                this.generateBox(level, chunkBB, 21, 12, 37, 36, 12, 39, BASE_GRAY, BASE_GRAY, false);

                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, 15 + i, i + 9, 43 - i, 42 - i, i + 9, 43 - i, BASE_LIGHT, BASE_LIGHT, false);
                }

                for (var x = 21; x <= 36; x += 3) {
                    this.placeBlock(level, DOT_DECO_DATA, x, 13, 38, chunkBB);
                }
            }
        }
    }

    public abstract static class MonumentPiece {
        static final Block BASE_GRAY = Block.PRISMARINE;
        static final Block BASE_LIGHT = Block.PRISMARINE_BRICKS;
        static final Block BASE_BLACK = Block.DARK_PRISMARINE;
        static final Block DOT_DECO_DATA = BASE_LIGHT;
        static final Block LAMP_BLOCK = Block.SEA_LANTERN;
        static final Block FILL_BLOCK = Block.WATER;
        private static final Set<Block> FILL_KEEP = Set.of(Block.ICE, Block.PACKED_ICE, Block.BLUE_ICE, Block.WATER);

        private static final int GRIDROOM_WIDTH = 8;
        private static final int GRIDROOM_DEPTH = 8;
        private static final int GRIDROOM_HEIGHT = 4;

        final int genDepth;
        BoundingBox boundingBox;
        RoomDefinition roomDefinition;
        private Direction orientation;

        MonumentPiece(Direction orientation, int genDepth, BoundingBox boundingBox) {
            this.genDepth = genDepth;
            this.boundingBox = boundingBox;
            this.setOrientation(orientation);
        }

        MonumentPiece(int genDepth, Direction orientation, RoomDefinition roomDefinition,
                int roomWidth, int roomHeight, int roomDepth) {
            this.genDepth = genDepth;
            this.boundingBox = makeRoomBoundingBox(orientation, roomDefinition, roomWidth, roomHeight, roomDepth);
            this.roomDefinition = roomDefinition;
            this.setOrientation(orientation);
        }

        static int getRoomIndex(int roomX, int roomY, int roomZ) {
            return roomY * 25 + roomZ * 5 + roomX;
        }

        private static BoundingBox makeRoomBoundingBox(Direction orientation, RoomDefinition roomDefinition,
                int roomWidth, int roomHeight, int roomDepth) {
            var roomIndex = roomDefinition.index;
            var roomX = roomIndex % 5;
            var roomZ = roomIndex / 5 % 5;
            var roomY = roomIndex / 25;
            var box = makeBoundingBox(0, 0, 0, orientation,
                    roomWidth * GRIDROOM_WIDTH, roomHeight * GRIDROOM_HEIGHT, roomDepth * GRIDROOM_DEPTH);
            switch (orientation) {
                case NORTH -> box.move(roomX * 8, roomY * 4, -(roomZ + roomDepth) * 8 + 1);
                case SOUTH -> box.move(roomX * 8, roomY * 4, roomZ * 8);
                case WEST -> box.move(-(roomZ + roomDepth) * 8 + 1, roomY * 4, roomX * 8);
                default -> box.move(roomZ * 8, roomY * 4, roomX * 8);
            }
            return box;
        }

        /** Vanilla {@code StructurePiece.makeBoundingBox}. */
        static BoundingBox makeBoundingBox(int x, int y, int z, Direction direction, int width, int height, int depth) {
            return direction == Direction.NORTH || direction == Direction.SOUTH
                    ? new BoundingBox(x, y, z, x + width - 1, y + height - 1, z + depth - 1)
                    : new BoundingBox(x, y, z, x + depth - 1, y + height - 1, z + width - 1);
        }

        void setOrientation(Direction orientation) {
            this.orientation = orientation;
        }

        public Direction orientation() {
            return this.orientation;
        }

        public BoundingBox boundingBox() {
            return this.boundingBox;
        }

        abstract void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB);

        int worldX(int x, int z) {
            var direction = this.orientation;
            if (direction == null) {
                return x;
            }
            return switch (direction) {
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
            var direction = this.orientation;
            if (direction == null) {
                return z;
            }
            return switch (direction) {
                case NORTH -> this.boundingBox.maxZ() - z;
                case SOUTH -> this.boundingBox.minZ() + z;
                case WEST, EAST -> this.boundingBox.minZ() + x;
                default -> z;
            };
        }

        BlockVec getWorldPos(int x, int y, int z) {
            return new BlockVec(this.worldX(x, z), this.worldY(y), this.worldZ(x, z));
        }

        void placeBlock(MonumentLevel level, Block block, int x, int y, int z, BoundingBox chunkBB) {
            var pos = this.getWorldPos(x, y, z);
            if (chunkBB.isInside(pos)) {
                level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), block);
            }
        }

        Block getBlock(MonumentLevel level, int x, int y, int z, BoundingBox chunkBB) {
            var pos = this.getWorldPos(x, y, z);
            return !chunkBB.isInside(pos) ? Block.AIR : level.getBlock(pos.blockX(), pos.blockY(), pos.blockZ());
        }

        void generateBox(MonumentLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
                int x1, int y1, int z1, Block edgeBlock, Block fillBlock, boolean skipAir) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        if (!skipAir || !this.getBlock(level, x, y, z, chunkBB).air()) {
                            if (y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1) {
                                this.placeBlock(level, fillBlock, x, y, z, chunkBB);
                            } else {
                                this.placeBlock(level, edgeBlock, x, y, z, chunkBB);
                            }
                        }
                    }
                }
            }
        }

        void fillColumnDown(MonumentLevel level, Block block, int x, int startY, int z, BoundingBox chunkBB) {
            var wx = this.worldX(x, z);
            var wy = this.worldY(startY);
            var wz = this.worldZ(x, z);
            var pos = new BlockVec(wx, wy, wz);
            if (!chunkBB.isInside(pos)) {
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

        void generateWaterBox(MonumentLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        var existing = this.getBlock(level, x, y, z, chunkBB);
                        if (!FILL_KEEP.contains(existing)) {
                            if (this.worldY(y) >= level.seaLevel() && !existing.compare(Block.WATER)) {
                                this.placeBlock(level, Block.AIR, x, y, z, chunkBB);
                            } else {
                                this.placeBlock(level, FILL_BLOCK, x, y, z, chunkBB);
                            }
                        }
                    }
                }
            }
        }

        void generateDefaultFloor(MonumentLevel level, BoundingBox chunkBB, int xOff, int zOff, boolean downOpening) {
            if (downOpening) {
                this.generateBox(level, chunkBB, xOff, 0, zOff, xOff + 2, 0, zOff + 7, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 5, 0, zOff, xOff + 7, 0, zOff + 7, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff, xOff + 4, 0, zOff + 2, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff + 5, xOff + 4, 0, zOff + 7, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff + 2, xOff + 4, 0, zOff + 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, xOff + 3, 0, zOff + 5, xOff + 4, 0, zOff + 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, xOff + 2, 0, zOff + 3, xOff + 2, 0, zOff + 4, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, xOff + 5, 0, zOff + 3, xOff + 5, 0, zOff + 4, BASE_LIGHT, BASE_LIGHT, false);
            } else {
                this.generateBox(level, chunkBB, xOff, 0, zOff, xOff + 7, 0, zOff + 7, BASE_GRAY, BASE_GRAY, false);
            }
        }

        void generateBoxOnFillOnly(MonumentLevel level, BoundingBox chunkBB, int x0, int y0, int z0,
                int x1, int y1, int z1, Block targetBlock) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    for (var z = z0; z <= z1; z++) {
                        if (this.getBlock(level, x, y, z, chunkBB).compare(FILL_BLOCK)) {
                            this.placeBlock(level, targetBlock, x, y, z, chunkBB);
                        }
                    }
                }
            }
        }

        boolean chunkIntersects(BoundingBox chunkBB, int x0, int z0, int x1, int z1) {
            var wx0 = this.worldX(x0, z0);
            var wz0 = this.worldZ(x0, z0);
            var wx1 = this.worldX(x1, z1);
            var wz1 = this.worldZ(x1, z1);
            return chunkBB.intersects(Math.min(wx0, wx1), Math.min(wz0, wz1), Math.max(wx0, wx1), Math.max(wz0, wz1));
        }

        /**
         * Vanilla {@code OceanMonumentPiece.spawnElder}: spawns an elder
         * guardian entity. Entity placement is out of scope for this port
         * and vanilla's version consumes no randomness, so omitting it does
         * not desynchronize any later block placement.
         */
        void spawnElder(MonumentLevel level, BoundingBox chunkBB, int x, int y, int z) {
        }
    }

    public static final class OceanMonumentCoreRoom extends MonumentPiece {
        OceanMonumentCoreRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 2, 2, 2);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 0, 14, 8, 14, BASE_GRAY);
            var block = BASE_LIGHT;
            this.generateBox(level, chunkBB, 0, 7, 0, 0, 7, 15, block, block, false);
            this.generateBox(level, chunkBB, 15, 7, 0, 15, 7, 15, block, block, false);
            this.generateBox(level, chunkBB, 1, 7, 0, 15, 7, 0, block, block, false);
            this.generateBox(level, chunkBB, 1, 7, 15, 14, 7, 15, block, block, false);

            for (var yx = 1; yx <= 6; yx++) {
                block = BASE_LIGHT;
                if (yx == 2 || yx == 6) {
                    block = BASE_GRAY;
                }

                for (var x = 0; x <= 15; x += 15) {
                    this.generateBox(level, chunkBB, x, yx, 0, x, yx, 1, block, block, false);
                    this.generateBox(level, chunkBB, x, yx, 6, x, yx, 9, block, block, false);
                    this.generateBox(level, chunkBB, x, yx, 14, x, yx, 15, block, block, false);
                }

                this.generateBox(level, chunkBB, 1, yx, 0, 1, yx, 0, block, block, false);
                this.generateBox(level, chunkBB, 6, yx, 0, 9, yx, 0, block, block, false);
                this.generateBox(level, chunkBB, 14, yx, 0, 14, yx, 0, block, block, false);
                this.generateBox(level, chunkBB, 1, yx, 15, 14, yx, 15, block, block, false);
            }

            this.generateBox(level, chunkBB, 6, 3, 6, 9, 6, 9, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 7, 4, 7, 8, 5, 8, Block.GOLD_BLOCK, Block.GOLD_BLOCK, false);

            for (var yx = 3; yx <= 6; yx += 3) {
                for (var x = 6; x <= 9; x += 3) {
                    this.placeBlock(level, LAMP_BLOCK, x, yx, 6, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, x, yx, 9, chunkBB);
                }
            }

            this.generateBox(level, chunkBB, 5, 1, 6, 5, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 9, 5, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 1, 6, 10, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 1, 9, 10, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 5, 6, 2, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 1, 5, 9, 2, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 10, 6, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 1, 10, 9, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 2, 5, 5, 6, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 2, 10, 5, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 2, 5, 10, 6, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 2, 10, 10, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 7, 1, 5, 7, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 7, 1, 10, 7, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 7, 9, 5, 7, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 7, 9, 10, 7, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 7, 5, 6, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 7, 10, 6, 7, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 7, 5, 14, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 7, 10, 14, 7, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 2, 2, 1, 3, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 2, 3, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 1, 2, 13, 1, 3, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 12, 1, 2, 12, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 12, 2, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 13, 3, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 1, 12, 13, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 12, 1, 13, 12, 1, 13, BASE_LIGHT, BASE_LIGHT, false);
        }
    }

    public static final class OceanMonumentDoubleXRoom extends MonumentPiece {
        OceanMonumentDoubleXRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 2, 1, 1);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            var east = this.roomDefinition.connections[Direction.EAST.ordinal()];
            var west = this.roomDefinition;
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 8, 0, east.hasOpening[Direction.DOWN.ordinal()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, west.hasOpening[Direction.DOWN.ordinal()]);
            }

            if (west.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 7, 4, 6, BASE_GRAY);
            }

            if (east.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 8, 4, 1, 14, 4, 6, BASE_GRAY);
            }

            this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 15, 3, 0, 15, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 0, 15, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 7, 14, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 7, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 15, 2, 0, 15, 2, 7, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 0, 15, 2, 0, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 7, 14, 2, 7, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 15, 1, 0, 15, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 15, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 7, 14, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 0, 10, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 2, 0, 9, 2, 3, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 5, 3, 0, 10, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, LAMP_BLOCK, 6, 2, 3, chunkBB);
            this.placeBlock(level, LAMP_BLOCK, 9, 2, 3, chunkBB);
            if (west.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (west.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
            }

            if (west.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (east.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 0, 12, 2, 0);
            }

            if (east.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 7, 12, 2, 7);
            }

            if (east.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 15, 1, 3, 15, 2, 4);
            }
        }
    }

    public static final class OceanMonumentDoubleXYRoom extends MonumentPiece {
        OceanMonumentDoubleXYRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 2, 2, 1);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            var east = this.roomDefinition.connections[Direction.EAST.ordinal()];
            var west = this.roomDefinition;
            var westUp = west.connections[Direction.UP.ordinal()];
            var eastUp = east.connections[Direction.UP.ordinal()];
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 8, 0, east.hasOpening[Direction.DOWN.ordinal()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, west.hasOpening[Direction.DOWN.ordinal()]);
            }

            if (westUp.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 1, 7, 8, 6, BASE_GRAY);
            }

            if (eastUp.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 8, 8, 1, 14, 8, 6, BASE_GRAY);
            }

            for (var y = 1; y <= 7; y++) {
                var block = BASE_LIGHT;
                if (y == 2 || y == 6) {
                    block = BASE_GRAY;
                }

                this.generateBox(level, chunkBB, 0, y, 0, 0, y, 7, block, block, false);
                this.generateBox(level, chunkBB, 15, y, 0, 15, y, 7, block, block, false);
                this.generateBox(level, chunkBB, 1, y, 0, 15, y, 0, block, block, false);
                this.generateBox(level, chunkBB, 1, y, 7, 14, y, 7, block, block, false);
            }

            this.generateBox(level, chunkBB, 2, 1, 3, 2, 7, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 2, 4, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 1, 5, 4, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 1, 3, 13, 7, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 11, 1, 2, 12, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 11, 1, 5, 12, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 3, 5, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 1, 3, 10, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 7, 2, 10, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 5, 2, 5, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 5, 2, 10, 7, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 5, 5, 5, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 10, 5, 5, 10, 7, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, BASE_LIGHT, 6, 6, 2, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 9, 6, 2, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 6, 6, 5, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 9, 6, 5, chunkBB);
            this.generateBox(level, chunkBB, 5, 4, 3, 6, 4, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 4, 3, 10, 4, 4, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, LAMP_BLOCK, 5, 4, 2, chunkBB);
            this.placeBlock(level, LAMP_BLOCK, 5, 4, 5, chunkBB);
            this.placeBlock(level, LAMP_BLOCK, 10, 4, 2, chunkBB);
            this.placeBlock(level, LAMP_BLOCK, 10, 4, 5, chunkBB);
            if (west.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (west.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
            }

            if (west.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (east.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 0, 12, 2, 0);
            }

            if (east.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 11, 1, 7, 12, 2, 7);
            }

            if (east.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 15, 1, 3, 15, 2, 4);
            }

            if (westUp.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 0, 4, 6, 0);
            }

            if (westUp.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 7, 4, 6, 7);
            }

            if (westUp.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 5, 3, 0, 6, 4);
            }

            if (eastUp.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 11, 5, 0, 12, 6, 0);
            }

            if (eastUp.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 11, 5, 7, 12, 6, 7);
            }

            if (eastUp.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 15, 5, 3, 15, 6, 4);
            }
        }
    }

    public static final class OceanMonumentDoubleYRoom extends MonumentPiece {
        OceanMonumentDoubleYRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 1, 2, 1);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.ordinal()]);
            }

            var above = this.roomDefinition.connections[Direction.UP.ordinal()];
            if (above.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 1, 6, 8, 6, BASE_GRAY);
            }

            this.generateBox(level, chunkBB, 0, 4, 0, 0, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 4, 0, 7, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 0, 6, 4, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 7, 6, 4, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 4, 1, 2, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 2, 1, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 4, 1, 5, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 4, 2, 6, 4, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 4, 5, 2, 4, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 4, 5, 1, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 4, 5, 5, 4, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 4, 5, 6, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
            var definition = this.roomDefinition;

            for (var y = 1; y <= 5; y += 4) {
                var z = 0;
                if (definition.hasOpening[Direction.SOUTH.ordinal()]) {
                    this.generateBox(level, chunkBB, 2, y, z, 2, y + 2, z, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 5, y, z, 5, y + 2, z, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, y + 2, z, 4, y + 2, z, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 0, y, z, 7, y + 2, z, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, y + 1, z, 7, y + 1, z, BASE_GRAY, BASE_GRAY, false);
                }

                var zHigh = 7;
                if (definition.hasOpening[Direction.NORTH.ordinal()]) {
                    this.generateBox(level, chunkBB, 2, y, zHigh, 2, y + 2, zHigh, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 5, y, zHigh, 5, y + 2, zHigh, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, y + 2, zHigh, 4, y + 2, zHigh, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 0, y, zHigh, 7, y + 2, zHigh, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, y + 1, zHigh, 7, y + 1, zHigh, BASE_GRAY, BASE_GRAY, false);
                }

                var x = 0;
                if (definition.hasOpening[Direction.WEST.ordinal()]) {
                    this.generateBox(level, chunkBB, x, y, 2, x, y + 2, 2, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, x, y, 5, x, y + 2, 5, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, x, y + 2, 3, x, y + 2, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, x, y, 0, x, y + 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, x, y + 1, 0, x, y + 1, 7, BASE_GRAY, BASE_GRAY, false);
                }

                var xHigh = 7;
                if (definition.hasOpening[Direction.EAST.ordinal()]) {
                    this.generateBox(level, chunkBB, xHigh, y, 2, xHigh, y + 2, 2, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xHigh, y, 5, xHigh, y + 2, 5, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xHigh, y + 2, 3, xHigh, y + 2, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, xHigh, y, 0, xHigh, y + 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, xHigh, y + 1, 0, xHigh, y + 1, 7, BASE_GRAY, BASE_GRAY, false);
                }

                definition = above;
            }
        }
    }

    public static final class OceanMonumentDoubleYZRoom extends MonumentPiece {
        OceanMonumentDoubleYZRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 1, 2, 2);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            var north = this.roomDefinition.connections[Direction.NORTH.ordinal()];
            var south = this.roomDefinition;
            var northUp = north.connections[Direction.UP.ordinal()];
            var southUp = south.connections[Direction.UP.ordinal()];
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 8, north.hasOpening[Direction.DOWN.ordinal()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, south.hasOpening[Direction.DOWN.ordinal()]);
            }

            if (southUp.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 1, 6, 8, 7, BASE_GRAY);
            }

            if (northUp.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 8, 8, 6, 8, 14, BASE_GRAY);
            }

            for (var y = 1; y <= 7; y++) {
                var block = BASE_LIGHT;
                if (y == 2 || y == 6) {
                    block = BASE_GRAY;
                }

                this.generateBox(level, chunkBB, 0, y, 0, 0, y, 15, block, block, false);
                this.generateBox(level, chunkBB, 7, y, 0, 7, y, 15, block, block, false);
                this.generateBox(level, chunkBB, 1, y, 0, 6, y, 0, block, block, false);
                this.generateBox(level, chunkBB, 1, y, 15, 6, y, 15, block, block, false);
            }

            for (var y = 1; y <= 7; y++) {
                var block = BASE_BLACK;
                if (y == 2 || y == 6) {
                    block = LAMP_BLOCK;
                }

                this.generateBox(level, chunkBB, 3, y, 7, 4, y, 8, block, block, false);
            }

            if (south.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (south.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 3, 7, 2, 4);
            }

            if (south.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (north.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 15, 4, 2, 15);
            }

            if (north.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 11, 0, 2, 12);
            }

            if (north.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 11, 7, 2, 12);
            }

            if (southUp.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 0, 4, 6, 0);
            }

            if (southUp.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 7, 5, 3, 7, 6, 4);
                this.generateBox(level, chunkBB, 5, 4, 2, 6, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 2, 6, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 5, 6, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (southUp.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 5, 3, 0, 6, 4);
                this.generateBox(level, chunkBB, 1, 4, 2, 2, 4, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 2, 1, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 5, 1, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (northUp.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 5, 15, 4, 6, 15);
            }

            if (northUp.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 5, 11, 0, 6, 12);
                this.generateBox(level, chunkBB, 1, 4, 10, 2, 4, 13, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 10, 1, 3, 10, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 13, 1, 3, 13, BASE_LIGHT, BASE_LIGHT, false);
            }

            if (northUp.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 7, 5, 11, 7, 6, 12);
                this.generateBox(level, chunkBB, 5, 4, 10, 6, 4, 13, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 10, 6, 3, 10, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 13, 6, 3, 13, BASE_LIGHT, BASE_LIGHT, false);
            }
        }
    }

    public static final class OceanMonumentDoubleZRoom extends MonumentPiece {
        OceanMonumentDoubleZRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 1, 1, 2);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            var north = this.roomDefinition.connections[Direction.NORTH.ordinal()];
            var south = this.roomDefinition;
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 8, north.hasOpening[Direction.DOWN.ordinal()]);
                this.generateDefaultFloor(level, chunkBB, 0, 0, south.hasOpening[Direction.DOWN.ordinal()]);
            }

            if (south.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 6, 4, 7, BASE_GRAY);
            }

            if (north.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 8, 6, 4, 14, BASE_GRAY);
            }

            this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 3, 0, 7, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 0, 7, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 15, 6, 3, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 15, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 15, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 0, 7, 2, 0, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 1, 2, 15, 6, 2, 15, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 7, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 15, 6, 1, 15, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 1, 1, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 1, 6, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 1, 1, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 3, 1, 6, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 13, 1, 1, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 1, 13, 6, 1, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 13, 1, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 3, 13, 6, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 6, 2, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 6, 5, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 1, 9, 2, 3, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 9, 5, 3, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 2, 6, 4, 2, 6, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 3, 2, 9, 4, 2, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 2, 2, 7, 2, 2, 8, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 2, 7, 5, 2, 8, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, LAMP_BLOCK, 2, 2, 5, chunkBB);
            this.placeBlock(level, LAMP_BLOCK, 5, 2, 5, chunkBB);
            this.placeBlock(level, LAMP_BLOCK, 2, 2, 10, chunkBB);
            this.placeBlock(level, LAMP_BLOCK, 5, 2, 10, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 2, 3, 5, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 5, 3, 5, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 2, 3, 10, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 5, 3, 10, chunkBB);
            if (south.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }

            if (south.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 3, 7, 2, 4);
            }

            if (south.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
            }

            if (north.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 15, 4, 2, 15);
            }

            if (north.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 11, 0, 2, 12);
            }

            if (north.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 7, 1, 11, 7, 2, 12);
            }
        }
    }

    public static final class OceanMonumentEntryRoom extends MonumentPiece {
        OceanMonumentEntryRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 1, 1, 1);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 0, 3, 0, 2, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 1, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, 2, 0, 7, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 1, 7, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 2, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 5, 1, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            if (this.roomDefinition.hasOpening[Direction.NORTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
            }

            if (this.roomDefinition.hasOpening[Direction.WEST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 0, 1, 3, 1, 2, 4);
            }

            if (this.roomDefinition.hasOpening[Direction.EAST.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 6, 1, 3, 7, 2, 4);
            }
        }
    }

    public static final class OceanMonumentPenthouse extends MonumentPiece {
        OceanMonumentPenthouse(Direction orientation, BoundingBox boundingBox) {
            super(orientation, 1, boundingBox);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            this.generateBox(level, chunkBB, 2, -1, 2, 11, -1, 11, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, -1, 0, 1, -1, 11, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 12, -1, 0, 13, -1, 11, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 2, -1, 0, 11, -1, 1, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 2, -1, 12, 11, -1, 13, BASE_GRAY, BASE_GRAY, false);
            this.generateBox(level, chunkBB, 0, 0, 0, 0, 0, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 13, 0, 0, 13, 0, 13, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 0, 0, 12, 0, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 0, 13, 12, 0, 13, BASE_LIGHT, BASE_LIGHT, false);

            for (var i = 2; i <= 11; i += 3) {
                this.placeBlock(level, LAMP_BLOCK, 0, 0, i, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 13, 0, i, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, i, 0, 0, chunkBB);
            }

            this.generateBox(level, chunkBB, 2, 0, 3, 4, 0, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 9, 0, 3, 11, 0, 9, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 4, 0, 9, 9, 0, 11, BASE_LIGHT, BASE_LIGHT, false);
            this.placeBlock(level, BASE_LIGHT, 5, 0, 8, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 8, 0, 8, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 10, 0, 10, chunkBB);
            this.placeBlock(level, BASE_LIGHT, 3, 0, 10, chunkBB);
            this.generateBox(level, chunkBB, 3, 0, 3, 3, 0, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 10, 0, 3, 10, 0, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 6, 0, 10, 7, 0, 10, BASE_BLACK, BASE_BLACK, false);
            var x = 3;

            for (var i = 0; i < 2; i++) {
                for (var z = 2; z <= 8; z += 3) {
                    this.generateBox(level, chunkBB, x, 0, z, x, 2, z, BASE_LIGHT, BASE_LIGHT, false);
                }

                x = 10;
            }

            this.generateBox(level, chunkBB, 5, 0, 10, 5, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 8, 0, 10, 8, 2, 10, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 6, -1, 7, 7, -1, 8, BASE_BLACK, BASE_BLACK, false);
            this.generateWaterBox(level, chunkBB, 6, -1, 3, 7, -1, 4);
            this.spawnElder(level, chunkBB, 6, 1, 6);
        }
    }

    public static final class OceanMonumentSimpleRoom extends MonumentPiece {
        private final int mainDesign;

        OceanMonumentSimpleRoom(Direction orientation, RoomDefinition definition, RandomSource random) {
            super(1, orientation, definition, 1, 1, 1);
            this.mainDesign = random.nextInt(3);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.ordinal()]);
            }

            if (this.roomDefinition.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 6, 4, 6, BASE_GRAY);
            }

            var centerPillar = this.mainDesign != 0
                    && random.nextBoolean()
                    && !this.roomDefinition.hasOpening[Direction.DOWN.ordinal()]
                    && !this.roomDefinition.hasOpening[Direction.UP.ordinal()]
                    && this.roomDefinition.countOpenings() > 1;
            if (this.mainDesign == 0) {
                this.generateBox(level, chunkBB, 0, 1, 0, 2, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 3, 0, 2, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 2, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 1, 2, 0, 2, 2, 0, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 1, 2, 1, chunkBB);
                this.generateBox(level, chunkBB, 5, 1, 0, 7, 1, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 3, 0, 7, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 2, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 5, 2, 0, 6, 2, 0, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 6, 2, 1, chunkBB);
                this.generateBox(level, chunkBB, 0, 1, 5, 2, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 3, 5, 2, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 2, 5, 0, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 1, 2, 7, 2, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 1, 2, 6, chunkBB);
                this.generateBox(level, chunkBB, 5, 1, 5, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 3, 5, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 2, 5, 7, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 5, 2, 7, 6, 2, 7, BASE_GRAY, BASE_GRAY, false);
                this.placeBlock(level, LAMP_BLOCK, 6, 2, 6, chunkBB);
                if (this.roomDefinition.hasOpening[Direction.SOUTH.ordinal()]) {
                    this.generateBox(level, chunkBB, 3, 3, 0, 4, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 3, 3, 0, 4, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, 2, 0, 4, 2, 0, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 3, 1, 0, 4, 1, 1, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.NORTH.ordinal()]) {
                    this.generateBox(level, chunkBB, 3, 3, 7, 4, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 3, 3, 6, 4, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 3, 2, 7, 4, 2, 7, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 3, 1, 6, 4, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.WEST.ordinal()]) {
                    this.generateBox(level, chunkBB, 0, 3, 3, 0, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 0, 3, 3, 1, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, 2, 3, 0, 2, 4, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 0, 1, 3, 1, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (this.roomDefinition.hasOpening[Direction.EAST.ordinal()]) {
                    this.generateBox(level, chunkBB, 7, 3, 3, 7, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                } else {
                    this.generateBox(level, chunkBB, 6, 3, 3, 7, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 7, 2, 3, 7, 2, 4, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 6, 1, 3, 7, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                }
            } else if (this.mainDesign == 1) {
                this.generateBox(level, chunkBB, 2, 1, 2, 2, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 2, 1, 5, 2, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 1, 5, 5, 3, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 5, 1, 2, 5, 3, 2, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, LAMP_BLOCK, 2, 2, 2, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 2, 2, 5, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 5, 2, 5, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 5, 2, 2, chunkBB);
                this.generateBox(level, chunkBB, 0, 1, 0, 1, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 1, 0, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 7, 1, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 6, 0, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 7, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 6, 7, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 1, 0, 7, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 1, 7, 3, 1, BASE_LIGHT, BASE_LIGHT, false);
                this.placeBlock(level, BASE_GRAY, 1, 2, 0, chunkBB);
                this.placeBlock(level, BASE_GRAY, 0, 2, 1, chunkBB);
                this.placeBlock(level, BASE_GRAY, 1, 2, 7, chunkBB);
                this.placeBlock(level, BASE_GRAY, 0, 2, 6, chunkBB);
                this.placeBlock(level, BASE_GRAY, 6, 2, 7, chunkBB);
                this.placeBlock(level, BASE_GRAY, 7, 2, 6, chunkBB);
                this.placeBlock(level, BASE_GRAY, 6, 2, 0, chunkBB);
                this.placeBlock(level, BASE_GRAY, 7, 2, 1, chunkBB);
                if (!this.roomDefinition.hasOpening[Direction.SOUTH.ordinal()]) {
                    this.generateBox(level, chunkBB, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 1, 2, 0, 6, 2, 0, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.NORTH.ordinal()]) {
                    this.generateBox(level, chunkBB, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 1, 2, 7, 6, 2, 7, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.WEST.ordinal()]) {
                    this.generateBox(level, chunkBB, 0, 3, 1, 0, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 0, 2, 1, 0, 2, 6, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 0, 1, 1, 0, 1, 6, BASE_LIGHT, BASE_LIGHT, false);
                }

                if (!this.roomDefinition.hasOpening[Direction.EAST.ordinal()]) {
                    this.generateBox(level, chunkBB, 7, 3, 1, 7, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, 7, 2, 1, 7, 2, 6, BASE_GRAY, BASE_GRAY, false);
                    this.generateBox(level, chunkBB, 7, 1, 1, 7, 1, 6, BASE_LIGHT, BASE_LIGHT, false);
                }
            } else if (this.mainDesign == 2) {
                this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 1, 2, 0, 6, 2, 0, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 1, 2, 7, 6, 2, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 0, 1, 3, 0, 2, 4, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 7, 1, 3, 7, 2, 4, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 3, 1, 0, 4, 2, 0, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 3, 1, 7, 4, 2, 7, BASE_BLACK, BASE_BLACK, false);
                if (this.roomDefinition.hasOpening[Direction.SOUTH.ordinal()]) {
                    this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
                }

                if (this.roomDefinition.hasOpening[Direction.NORTH.ordinal()]) {
                    this.generateWaterBox(level, chunkBB, 3, 1, 7, 4, 2, 7);
                }

                if (this.roomDefinition.hasOpening[Direction.WEST.ordinal()]) {
                    this.generateWaterBox(level, chunkBB, 0, 1, 3, 0, 2, 4);
                }

                if (this.roomDefinition.hasOpening[Direction.EAST.ordinal()]) {
                    this.generateWaterBox(level, chunkBB, 7, 1, 3, 7, 2, 4);
                }
            }

            if (centerPillar) {
                this.generateBox(level, chunkBB, 3, 1, 3, 4, 1, 4, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 3, 2, 3, 4, 2, 4, BASE_GRAY, BASE_GRAY, false);
                this.generateBox(level, chunkBB, 3, 3, 3, 4, 3, 4, BASE_LIGHT, BASE_LIGHT, false);
            }
        }
    }

    public static final class OceanMonumentSimpleTopRoom extends MonumentPiece {
        OceanMonumentSimpleTopRoom(Direction orientation, RoomDefinition definition) {
            super(1, orientation, definition, 1, 1, 1);
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.roomDefinition.index / 25 > 0) {
                this.generateDefaultFloor(level, chunkBB, 0, 0, this.roomDefinition.hasOpening[Direction.DOWN.ordinal()]);
            }

            if (this.roomDefinition.connections[Direction.UP.ordinal()] == null) {
                this.generateBoxOnFillOnly(level, chunkBB, 1, 4, 1, 6, 4, 6, BASE_GRAY);
            }

            for (var x = 1; x <= 6; x++) {
                for (var z = 1; z <= 6; z++) {
                    if (random.nextInt(3) != 0) {
                        var y0 = 2 + (random.nextInt(4) == 0 ? 0 : 1);
                        var wetSponge = Block.WET_SPONGE;
                        this.generateBox(level, chunkBB, x, y0, z, x, 3, z, wetSponge, wetSponge, false);
                    }
                }
            }

            this.generateBox(level, chunkBB, 0, 1, 0, 0, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 1, 0, 7, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 0, 6, 1, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 1, 7, 6, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 2, 0, 0, 2, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 7, 2, 0, 7, 2, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 1, 2, 0, 6, 2, 0, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 1, 2, 7, 6, 2, 7, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 0, 3, 0, 0, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 7, 3, 0, 7, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 0, 6, 3, 0, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 1, 3, 7, 6, 3, 7, BASE_LIGHT, BASE_LIGHT, false);
            this.generateBox(level, chunkBB, 0, 1, 3, 0, 2, 4, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 7, 1, 3, 7, 2, 4, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 3, 1, 0, 4, 2, 0, BASE_BLACK, BASE_BLACK, false);
            this.generateBox(level, chunkBB, 3, 1, 7, 4, 2, 7, BASE_BLACK, BASE_BLACK, false);
            if (this.roomDefinition.hasOpening[Direction.SOUTH.ordinal()]) {
                this.generateWaterBox(level, chunkBB, 3, 1, 0, 4, 2, 0);
            }
        }
    }

    public static final class OceanMonumentWingRoom extends MonumentPiece {
        private final int mainDesign;

        OceanMonumentWingRoom(Direction orientation, BoundingBox boundingBox, int randomValue) {
            super(orientation, 1, boundingBox);
            this.mainDesign = randomValue & 1;
        }

        @Override
        void postProcess(MonumentLevel level, RandomSource random, BoundingBox chunkBB) {
            if (this.mainDesign == 0) {
                for (var i = 0; i < 4; i++) {
                    this.generateBox(level, chunkBB, 10 - i, 3 - i, 20 - i, 12 + i, 3 - i, 20, BASE_LIGHT, BASE_LIGHT, false);
                }

                this.generateBox(level, chunkBB, 7, 0, 6, 15, 0, 16, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 6, 0, 6, 6, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 16, 0, 6, 16, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 7, 7, 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 15, 1, 7, 15, 1, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 7, 1, 6, 9, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 13, 1, 6, 15, 3, 6, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 8, 1, 7, 9, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 13, 1, 7, 14, 1, 7, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 9, 0, 5, 13, 0, 5, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 10, 0, 7, 12, 0, 7, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 8, 0, 10, 8, 0, 12, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 14, 0, 10, 14, 0, 12, BASE_BLACK, BASE_BLACK, false);

                for (var z = 18; z >= 7; z -= 3) {
                    this.placeBlock(level, LAMP_BLOCK, 6, 3, z, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, 16, 3, z, chunkBB);
                }

                this.placeBlock(level, LAMP_BLOCK, 10, 0, 10, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 12, 0, 10, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 10, 0, 12, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 12, 0, 12, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 8, 3, 6, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 14, 3, 6, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 4, 2, 4, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 4, 1, 4, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 4, 0, 4, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 18, 2, 4, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 18, 1, 4, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 18, 0, 4, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 4, 2, 18, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 4, 1, 18, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 4, 0, 18, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 18, 2, 18, chunkBB);
                this.placeBlock(level, LAMP_BLOCK, 18, 1, 18, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 18, 0, 18, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 9, 7, 20, chunkBB);
                this.placeBlock(level, BASE_LIGHT, 13, 7, 20, chunkBB);
                this.generateBox(level, chunkBB, 6, 0, 21, 7, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 15, 0, 21, 16, 4, 21, BASE_LIGHT, BASE_LIGHT, false);
                this.spawnElder(level, chunkBB, 11, 2, 16);
            } else if (this.mainDesign == 1) {
                this.generateBox(level, chunkBB, 9, 3, 18, 13, 3, 20, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 9, 0, 18, 9, 2, 18, BASE_LIGHT, BASE_LIGHT, false);
                this.generateBox(level, chunkBB, 13, 0, 18, 13, 2, 18, BASE_LIGHT, BASE_LIGHT, false);
                var x = 9;

                for (var i = 0; i < 2; i++) {
                    this.placeBlock(level, BASE_LIGHT, x, 6, 20, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, x, 5, 20, chunkBB);
                    this.placeBlock(level, BASE_LIGHT, x, 4, 20, chunkBB);
                    x = 13;
                }

                this.generateBox(level, chunkBB, 7, 3, 7, 15, 3, 14, BASE_LIGHT, BASE_LIGHT, false);
                var column = 10;

                for (var i = 0; i < 2; i++) {
                    this.generateBox(level, chunkBB, column, 0, 10, column, 6, 10, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, column, 0, 12, column, 6, 12, BASE_LIGHT, BASE_LIGHT, false);
                    this.placeBlock(level, LAMP_BLOCK, column, 0, 10, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, column, 0, 12, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, column, 4, 10, chunkBB);
                    this.placeBlock(level, LAMP_BLOCK, column, 4, 12, chunkBB);
                    column = 12;
                }

                column = 8;

                for (var i = 0; i < 2; i++) {
                    this.generateBox(level, chunkBB, column, 0, 7, column, 2, 7, BASE_LIGHT, BASE_LIGHT, false);
                    this.generateBox(level, chunkBB, column, 0, 14, column, 2, 14, BASE_LIGHT, BASE_LIGHT, false);
                    column = 14;
                }

                this.generateBox(level, chunkBB, 8, 3, 8, 8, 3, 13, BASE_BLACK, BASE_BLACK, false);
                this.generateBox(level, chunkBB, 14, 3, 8, 14, 3, 13, BASE_BLACK, BASE_BLACK, false);
                this.spawnElder(level, chunkBB, 11, 5, 13);
            }
        }
    }

    static final class RoomDefinition {
        final int index;
        final RoomDefinition[] connections = new RoomDefinition[6];
        final boolean[] hasOpening = new boolean[6];
        boolean claimed;
        boolean isSource;
        private int scanIndex;

        RoomDefinition(int roomIndex) {
            this.index = roomIndex;
        }

        void setConnection(Direction direction, RoomDefinition definition) {
            this.connections[direction.ordinal()] = definition;
            definition.connections[direction.opposite().ordinal()] = this;
        }

        void updateOpenings() {
            for (var i = 0; i < 6; i++) {
                this.hasOpening[i] = this.connections[i] != null;
            }
        }

        boolean findSource(int scanIndex) {
            if (this.isSource) {
                return true;
            }
            this.scanIndex = scanIndex;

            for (var i = 0; i < 6; i++) {
                if (this.connections[i] != null && this.hasOpening[i] && this.connections[i].scanIndex != scanIndex
                        && this.connections[i].findSource(scanIndex)) {
                    return true;
                }
            }

            return false;
        }

        boolean isSpecial() {
            return this.index >= 75;
        }

        int countOpenings() {
            var count = 0;
            for (var i = 0; i < 6; i++) {
                if (this.hasOpening[i]) {
                    count++;
                }
            }
            return count;
        }
    }
}
