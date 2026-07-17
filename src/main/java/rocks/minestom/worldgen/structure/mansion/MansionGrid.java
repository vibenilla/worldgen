package rocks.minestom.worldgen.structure.mansion;

import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code WoodlandMansionPieces.MansionGrid}: the flag-matrix
 * layout solver that lays out the mansion's three floors as an 11x11 grid of
 * corridor, room and blocked cells, then assigns rooms and doors.
 *
 * <p>Random call order matches vanilla exactly so a seeded random reproduces
 * vanilla layouts. All of this class's state and helpers are package-private;
 * only {@link MansionPiecePlacer} reads it.
 */
final class MansionGrid {
    private static final int CLEAR = 0;
    private static final int CORRIDOR = 1;
    private static final int ROOM = 2;
    private static final int START_ROOM = 3;
    private static final int TEST_ROOM = 4;
    private static final int BLOCKED = 5;
    static final int ROOM_1x1 = 65536;
    static final int ROOM_1x2 = 131072;
    static final int ROOM_2x2 = 262144;
    static final int ROOM_ORIGIN_FLAG = 1048576;
    static final int ROOM_DOOR_FLAG = 2097152;
    static final int ROOM_STAIRS_FLAG = 4194304;
    static final int ROOM_CORRIDOR_FLAG = 8388608;
    private static final int ROOM_TYPE_MASK = 983040;
    private static final int ROOM_ID_MASK = 65535;

    private final RandomSource random;
    final SimpleGrid baseGrid;
    final SimpleGrid thirdFloorGrid;
    final SimpleGrid[] floorRooms;
    final int entranceX;
    final int entranceY;

    MansionGrid(RandomSource random) {
        this.random = random;
        this.entranceX = 7;
        this.entranceY = 4;
        this.baseGrid = new SimpleGrid(11, 11, BLOCKED);
        this.baseGrid.set(this.entranceX, this.entranceY, this.entranceX + 1, this.entranceY + 1, START_ROOM);
        this.baseGrid.set(this.entranceX - 1, this.entranceY, this.entranceX - 1, this.entranceY + 1, ROOM);
        this.baseGrid.set(this.entranceX + 2, this.entranceY - 2, this.entranceX + 3, this.entranceY + 3, BLOCKED);
        this.baseGrid.set(this.entranceX + 1, this.entranceY - 2, this.entranceX + 1, this.entranceY - 1, CORRIDOR);
        this.baseGrid.set(this.entranceX + 1, this.entranceY + 2, this.entranceX + 1, this.entranceY + 3, CORRIDOR);
        this.baseGrid.set(this.entranceX - 1, this.entranceY - 1, CORRIDOR);
        this.baseGrid.set(this.entranceX - 1, this.entranceY + 2, CORRIDOR);
        this.baseGrid.set(0, 0, 11, 1, BLOCKED);
        this.baseGrid.set(0, 9, 11, 11, BLOCKED);
        this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY - 2, Direction.WEST, 6);
        this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY + 3, Direction.WEST, 6);
        this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY - 1, Direction.WEST, 3);
        this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY + 2, Direction.WEST, 3);

        while (this.cleanEdges(this.baseGrid)) {
        }

        this.floorRooms = new SimpleGrid[3];
        this.floorRooms[0] = new SimpleGrid(11, 11, BLOCKED);
        this.floorRooms[1] = new SimpleGrid(11, 11, BLOCKED);
        this.floorRooms[2] = new SimpleGrid(11, 11, BLOCKED);
        this.identifyRooms(this.baseGrid, this.floorRooms[0]);
        this.identifyRooms(this.baseGrid, this.floorRooms[1]);
        this.floorRooms[0].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, ROOM_CORRIDOR_FLAG);
        this.floorRooms[1].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, ROOM_CORRIDOR_FLAG);
        this.thirdFloorGrid = new SimpleGrid(this.baseGrid.width, this.baseGrid.height, BLOCKED);
        this.setupThirdFloor();
        this.identifyRooms(this.thirdFloorGrid, this.floorRooms[2]);
    }

    static boolean isHouse(SimpleGrid grid, int x, int y) {
        var value = grid.get(x, y);
        return value == CORRIDOR || value == ROOM || value == START_ROOM || value == TEST_ROOM;
    }

    boolean isRoomId(SimpleGrid grid, int x, int y, int floor, int roomId) {
        return (this.floorRooms[floor].get(x, y) & ROOM_ID_MASK) == roomId;
    }

    Direction get1x2RoomDirection(SimpleGrid grid, int x, int y, int floorNum, int roomId) {
        for (var direction : Direction.HORIZONTAL) {
            if (this.isRoomId(grid, x + direction.stepX(), y + direction.stepZ(), floorNum, roomId)) {
                return direction;
            }
        }
        return null;
    }

    private void recursiveCorridor(SimpleGrid grid, int x, int y, Direction heading, int depth) {
        if (depth <= 0) {
            return;
        }

        grid.set(x, y, CORRIDOR);
        grid.setif(x + heading.stepX(), y + heading.stepZ(), CLEAR, CORRIDOR);

        for (var attempt = 0; attempt < 8; attempt++) {
            var nextDir = Direction.from2DDataValue(this.random.nextInt(4));
            if (nextDir != heading.opposite() && (nextDir != Direction.EAST || !this.random.nextBoolean())) {
                var nx = x + heading.stepX();
                var ny = y + heading.stepZ();
                if (grid.get(nx + nextDir.stepX(), ny + nextDir.stepZ()) == CLEAR
                        && grid.get(nx + nextDir.stepX() * 2, ny + nextDir.stepZ() * 2) == CLEAR) {
                    this.recursiveCorridor(grid, x + heading.stepX() + nextDir.stepX(), y + heading.stepZ() + nextDir.stepZ(), nextDir, depth - 1);
                    break;
                }
            }
        }

        var cw = heading.getClockWise();
        var ccw = heading.getCounterClockWise();
        grid.setif(x + cw.stepX(), y + cw.stepZ(), CLEAR, ROOM);
        grid.setif(x + ccw.stepX(), y + ccw.stepZ(), CLEAR, ROOM);
        grid.setif(x + heading.stepX() + cw.stepX(), y + heading.stepZ() + cw.stepZ(), CLEAR, ROOM);
        grid.setif(x + heading.stepX() + ccw.stepX(), y + heading.stepZ() + ccw.stepZ(), CLEAR, ROOM);
        grid.setif(x + heading.stepX() * 2, y + heading.stepZ() * 2, CLEAR, ROOM);
        grid.setif(x + cw.stepX() * 2, y + cw.stepZ() * 2, CLEAR, ROOM);
        grid.setif(x + ccw.stepX() * 2, y + ccw.stepZ() * 2, CLEAR, ROOM);
    }

    private boolean cleanEdges(SimpleGrid grid) {
        var touched = false;

        for (var y = 0; y < grid.height; y++) {
            for (var x = 0; x < grid.width; x++) {
                if (grid.get(x, y) != CLEAR) {
                    continue;
                }

                var directNeighbors = 0;
                directNeighbors += isHouse(grid, x + 1, y) ? 1 : 0;
                directNeighbors += isHouse(grid, x - 1, y) ? 1 : 0;
                directNeighbors += isHouse(grid, x, y + 1) ? 1 : 0;
                directNeighbors += isHouse(grid, x, y - 1) ? 1 : 0;
                if (directNeighbors >= 3) {
                    grid.set(x, y, ROOM);
                    touched = true;
                } else if (directNeighbors == 2) {
                    var diagonalNeighbors = 0;
                    diagonalNeighbors += isHouse(grid, x + 1, y + 1) ? 1 : 0;
                    diagonalNeighbors += isHouse(grid, x - 1, y + 1) ? 1 : 0;
                    diagonalNeighbors += isHouse(grid, x + 1, y - 1) ? 1 : 0;
                    diagonalNeighbors += isHouse(grid, x - 1, y - 1) ? 1 : 0;
                    if (diagonalNeighbors <= 1) {
                        grid.set(x, y, ROOM);
                        touched = true;
                    }
                }
            }
        }

        return touched;
    }

    private void setupThirdFloor() {
        var potentialRooms = new ArrayList<GridPos>();
        var floor = this.floorRooms[1];

        for (var y = 0; y < this.thirdFloorGrid.height; y++) {
            for (var x = 0; x < this.thirdFloorGrid.width; x++) {
                var roomData = floor.get(x, y);
                var roomType = roomData & ROOM_TYPE_MASK;
                if (roomType == ROOM_1x2 && (roomData & ROOM_DOOR_FLAG) == ROOM_DOOR_FLAG) {
                    potentialRooms.add(new GridPos(x, y));
                }
            }
        }

        if (potentialRooms.isEmpty()) {
            this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, BLOCKED);
            return;
        }

        var roomPos = potentialRooms.get(this.random.nextInt(potentialRooms.size()));
        var roomData = floor.get(roomPos.x(), roomPos.y());
        floor.set(roomPos.x(), roomPos.y(), roomData | ROOM_STAIRS_FLAG);
        var roomDir = this.get1x2RoomDirection(this.baseGrid, roomPos.x(), roomPos.y(), 1, roomData & ROOM_ID_MASK);
        var roomEndX = roomPos.x() + roomDir.stepX();
        var roomEndY = roomPos.y() + roomDir.stepZ();

        for (var y = 0; y < this.thirdFloorGrid.height; y++) {
            for (var x = 0; x < this.thirdFloorGrid.width; x++) {
                if (!isHouse(this.baseGrid, x, y)) {
                    this.thirdFloorGrid.set(x, y, BLOCKED);
                } else if (x == roomPos.x() && y == roomPos.y()) {
                    this.thirdFloorGrid.set(x, y, START_ROOM);
                } else if (x == roomEndX && y == roomEndY) {
                    this.thirdFloorGrid.set(x, y, START_ROOM);
                    this.floorRooms[2].set(x, y, ROOM_CORRIDOR_FLAG);
                }
            }
        }

        var potentialCorridors = new ArrayList<Direction>();
        for (var direction : Direction.HORIZONTAL) {
            if (this.thirdFloorGrid.get(roomEndX + direction.stepX(), roomEndY + direction.stepZ()) == CLEAR) {
                potentialCorridors.add(direction);
            }
        }

        if (potentialCorridors.isEmpty()) {
            this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, BLOCKED);
            floor.set(roomPos.x(), roomPos.y(), roomData);
            return;
        }

        var corridorDir = potentialCorridors.get(this.random.nextInt(potentialCorridors.size()));
        this.recursiveCorridor(this.thirdFloorGrid, roomEndX + corridorDir.stepX(), roomEndY + corridorDir.stepZ(), corridorDir, 4);

        while (this.cleanEdges(this.thirdFloorGrid)) {
        }
    }

    private void identifyRooms(SimpleGrid fromGrid, SimpleGrid roomGrid) {
        var roomPositions = new ArrayList<GridPos>();

        for (var y = 0; y < fromGrid.height; y++) {
            for (var x = 0; x < fromGrid.width; x++) {
                if (fromGrid.get(x, y) == ROOM) {
                    roomPositions.add(new GridPos(x, y));
                }
            }
        }

        shuffle(roomPositions, this.random);
        var roomId = 10;

        for (var pos : roomPositions) {
            var x = pos.x();
            var y = pos.y();
            if (roomGrid.get(x, y) != CLEAR) {
                continue;
            }

            var x0 = x;
            var x1 = x;
            var y0 = y;
            var y1 = y;
            var type = ROOM_1x1;
            if (roomGrid.get(x + 1, y) == CLEAR
                    && roomGrid.get(x, y + 1) == CLEAR
                    && roomGrid.get(x + 1, y + 1) == CLEAR
                    && fromGrid.get(x + 1, y) == ROOM
                    && fromGrid.get(x, y + 1) == ROOM
                    && fromGrid.get(x + 1, y + 1) == ROOM) {
                x1 = x + 1;
                y1 = y + 1;
                type = ROOM_2x2;
            } else if (roomGrid.get(x - 1, y) == CLEAR
                    && roomGrid.get(x, y + 1) == CLEAR
                    && roomGrid.get(x - 1, y + 1) == CLEAR
                    && fromGrid.get(x - 1, y) == ROOM
                    && fromGrid.get(x, y + 1) == ROOM
                    && fromGrid.get(x - 1, y + 1) == ROOM) {
                x0 = x - 1;
                y1 = y + 1;
                type = ROOM_2x2;
            } else if (roomGrid.get(x - 1, y) == CLEAR
                    && roomGrid.get(x, y - 1) == CLEAR
                    && roomGrid.get(x - 1, y - 1) == CLEAR
                    && fromGrid.get(x - 1, y) == ROOM
                    && fromGrid.get(x, y - 1) == ROOM
                    && fromGrid.get(x - 1, y - 1) == ROOM) {
                x0 = x - 1;
                y0 = y - 1;
                type = ROOM_2x2;
            } else if (roomGrid.get(x + 1, y) == CLEAR && fromGrid.get(x + 1, y) == ROOM) {
                x1 = x + 1;
                type = ROOM_1x2;
            } else if (roomGrid.get(x, y + 1) == CLEAR && fromGrid.get(x, y + 1) == ROOM) {
                y1 = y + 1;
                type = ROOM_1x2;
            } else if (roomGrid.get(x - 1, y) == CLEAR && fromGrid.get(x - 1, y) == ROOM) {
                x0 = x - 1;
                type = ROOM_1x2;
            } else if (roomGrid.get(x, y - 1) == CLEAR && fromGrid.get(x, y - 1) == ROOM) {
                y0 = y - 1;
                type = ROOM_1x2;
            }

            var doorX = this.random.nextBoolean() ? x0 : x1;
            var doorY = this.random.nextBoolean() ? y0 : y1;
            var doorFlag = ROOM_DOOR_FLAG;
            if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                doorX = doorX == x0 ? x1 : x0;
                doorY = doorY == y0 ? y1 : y0;
                if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                    doorY = doorY == y0 ? y1 : y0;
                    if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                        doorX = doorX == x0 ? x1 : x0;
                        doorY = doorY == y0 ? y1 : y0;
                        if (!fromGrid.edgesTo(doorX, doorY, CORRIDOR)) {
                            doorFlag = 0;
                            doorX = x0;
                            doorY = y0;
                        }
                    }
                }
            }

            for (var ry = y0; ry <= y1; ry++) {
                for (var rx = x0; rx <= x1; rx++) {
                    if (rx == doorX && ry == doorY) {
                        roomGrid.set(rx, ry, ROOM_ORIGIN_FLAG | doorFlag | type | roomId);
                    } else {
                        roomGrid.set(rx, ry, type | roomId);
                    }
                }
            }

            roomId++;
        }
    }

    /** Vanilla {@code Util.shuffle}: an in-place Fisher-Yates shuffle. */
    private static void shuffle(List<GridPos> list, RandomSource random) {
        for (var index = list.size(); index > 1; index--) {
            var swapIndex = random.nextInt(index);
            var temp = list.get(index - 1);
            list.set(index - 1, list.get(swapIndex));
            list.set(swapIndex, temp);
        }
    }

    private record GridPos(int x, int y) {
    }

    /** Port of vanilla {@code WoodlandMansionPieces.SimpleGrid}. */
    static final class SimpleGrid {
        private final int[][] grid;
        final int width;
        final int height;
        private final int valueIfOutside;

        SimpleGrid(int width, int height, int valueIfOutside) {
            this.width = width;
            this.height = height;
            this.valueIfOutside = valueIfOutside;
            this.grid = new int[width][height];
        }

        void set(int x, int y, int value) {
            if (x >= 0 && x < this.width && y >= 0 && y < this.height) {
                this.grid[x][y] = value;
            }
        }

        void set(int x0, int y0, int x1, int y1, int value) {
            for (var y = y0; y <= y1; y++) {
                for (var x = x0; x <= x1; x++) {
                    this.set(x, y, value);
                }
            }
        }

        int get(int x, int y) {
            return x >= 0 && x < this.width && y >= 0 && y < this.height ? this.grid[x][y] : this.valueIfOutside;
        }

        void setif(int x, int y, int ifValue, int value) {
            if (this.get(x, y) == ifValue) {
                this.set(x, y, value);
            }
        }

        boolean edgesTo(int x, int y, int ifValue) {
            return this.get(x - 1, y) == ifValue || this.get(x + 1, y) == ifValue
                    || this.get(x, y + 1) == ifValue || this.get(x, y - 1) == ifValue;
        }
    }
}
