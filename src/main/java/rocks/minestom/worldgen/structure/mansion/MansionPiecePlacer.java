package rocks.minestom.worldgen.structure.mansion;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.Mirror;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code WoodlandMansionPieces.MansionPiecePlacer}: converts
 * a solved {@link MansionGrid} into an ordered list of
 * {@link WoodlandMansionPiece} template placements.
 *
 * <p>Random call order matches vanilla exactly. This class only builds the
 * piece list (template name, position, rotation, mirror); template loading
 * and world placement are handled by {@code MansionPlacer}.
 */
final class MansionPiecePlacer {
    private final RandomSource random;
    private int startX;
    private int startY;

    MansionPiecePlacer(RandomSource random) {
        this.random = random;
    }

    static List<WoodlandMansionPiece> generateMansion(BlockVec origin, Rotation rotation, RandomSource random) {
        var grid = new MansionGrid(random);
        var placer = new MansionPiecePlacer(random);
        var pieces = new ArrayList<WoodlandMansionPiece>();
        placer.createMansion(origin, rotation, pieces, grid);
        return pieces;
    }

    private void createMansion(BlockVec origin, Rotation rotation, List<WoodlandMansionPiece> pieces, MansionGrid mansion) {
        var data = new PlacementData();
        data.position = origin;
        data.rotation = rotation;
        data.wallType = "wall_flat";
        var secondData = new PlacementData();
        this.entrance(pieces, data);
        secondData.position = above(data.position, 8);
        secondData.rotation = data.rotation;
        secondData.wallType = "wall_window";

        var baseGrid = mansion.baseGrid;
        var thirdGrid = mansion.thirdFloorGrid;
        this.startX = mansion.entranceX + 1;
        this.startY = mansion.entranceY + 1;
        var endX = mansion.entranceX + 1;
        var endY = mansion.entranceY;
        this.traverseOuterWalls(pieces, data, baseGrid, Direction.SOUTH, this.startX, this.startY, endX, endY);
        this.traverseOuterWalls(pieces, secondData, baseGrid, Direction.SOUTH, this.startX, this.startY, endX, endY);
        var thirdData = new PlacementData();
        thirdData.position = above(origin, 19);
        thirdData.rotation = data.rotation;
        thirdData.wallType = "wall_window";
        var done = false;

        outer:
        for (var y = 0; y < thirdGrid.height; y++) {
            for (var x = thirdGrid.width - 1; x >= 0; x--) {
                if (MansionGrid.isHouse(thirdGrid, x, y)) {
                    thirdData.position = relative(thirdData.position, rotate(rotation, Direction.SOUTH), 8 + (y - this.startY) * 8);
                    thirdData.position = relative(thirdData.position, rotate(rotation, Direction.EAST), (x - this.startX) * 8);
                    this.traverseWallPiece(pieces, thirdData);
                    this.traverseOuterWalls(pieces, thirdData, thirdGrid, Direction.SOUTH, x, y, x, y);
                    done = true;
                    break outer;
                }
            }
        }

        this.createRoof(pieces, above(origin, 16), rotation, baseGrid, thirdGrid);
        this.createRoof(pieces, above(origin, 27), rotation, thirdGrid, null);

        var roomCollections = new FloorRoomCollection[]{
                new FloorRoomCollection.First(),
                new FloorRoomCollection.Second(),
                new FloorRoomCollection.Third()
        };

        for (var floorNum = 0; floorNum < 3; floorNum++) {
            var floorOrigin = above(origin, 8 * floorNum + (floorNum == 2 ? 3 : 0));
            var rooms = mansion.floorRooms[floorNum];
            var grid = floorNum == 2 ? thirdGrid : baseGrid;
            var southPiece = floorNum == 0 ? "carpet_south_1" : "carpet_south_2";
            var westPiece = floorNum == 0 ? "carpet_west_1" : "carpet_west_2";

            for (var y = 0; y < grid.height; y++) {
                for (var x = 0; x < grid.width; x++) {
                    if (grid.get(x, y) != 1) {
                        continue;
                    }

                    var pos = relative(floorOrigin, rotate(rotation, Direction.SOUTH), 8 + (y - this.startY) * 8);
                    pos = relative(pos, rotate(rotation, Direction.EAST), (x - this.startX) * 8);
                    pieces.add(new WoodlandMansionPiece("corridor_floor", pos, rotation));

                    if (grid.get(x, y - 1) == 1 || (rooms.get(x, y - 1) & MansionGrid.ROOM_CORRIDOR_FLAG) == MansionGrid.ROOM_CORRIDOR_FLAG) {
                        pieces.add(new WoodlandMansionPiece("carpet_north",
                                above(relative(pos, rotate(rotation, Direction.EAST), 1), 1), rotation));
                    }

                    if (grid.get(x + 1, y) == 1 || (rooms.get(x + 1, y) & MansionGrid.ROOM_CORRIDOR_FLAG) == MansionGrid.ROOM_CORRIDOR_FLAG) {
                        var carpetPos = relative(pos, rotate(rotation, Direction.SOUTH), 1);
                        carpetPos = relative(carpetPos, rotate(rotation, Direction.EAST), 5);
                        pieces.add(new WoodlandMansionPiece("carpet_east", above(carpetPos, 1), rotation));
                    }

                    if (grid.get(x, y + 1) == 1 || (rooms.get(x, y + 1) & MansionGrid.ROOM_CORRIDOR_FLAG) == MansionGrid.ROOM_CORRIDOR_FLAG) {
                        var carpetPos = relative(pos, rotate(rotation, Direction.SOUTH), 5);
                        carpetPos = relative(carpetPos, rotate(rotation, Direction.WEST), 1);
                        pieces.add(new WoodlandMansionPiece(southPiece, carpetPos, rotation));
                    }

                    if (grid.get(x - 1, y) == 1 || (rooms.get(x - 1, y) & MansionGrid.ROOM_CORRIDOR_FLAG) == MansionGrid.ROOM_CORRIDOR_FLAG) {
                        var carpetPos = relative(pos, rotate(rotation, Direction.WEST), 1);
                        carpetPos = relative(carpetPos, rotate(rotation, Direction.NORTH), 1);
                        pieces.add(new WoodlandMansionPiece(westPiece, carpetPos, rotation));
                    }
                }
            }

            var wallPiece = floorNum == 0 ? "indoors_wall_1" : "indoors_wall_2";
            var doorPiece = floorNum == 0 ? "indoors_door_1" : "indoors_door_2";
            var doorDirs = new ArrayList<Direction>();

            for (var y = 0; y < grid.height; y++) {
                for (var x = 0; x < grid.width; x++) {
                    var thirdFloorStartRoom = floorNum == 2 && grid.get(x, y) == 3;
                    if (grid.get(x, y) != 2 && !thirdFloorStartRoom) {
                        continue;
                    }

                    var roomData = rooms.get(x, y);
                    var roomType = roomData & 983040;
                    var roomId = roomData & 65535;
                    thirdFloorStartRoom = thirdFloorStartRoom && (roomData & MansionGrid.ROOM_CORRIDOR_FLAG) == MansionGrid.ROOM_CORRIDOR_FLAG;
                    doorDirs.clear();
                    if ((roomData & MansionGrid.ROOM_DOOR_FLAG) == MansionGrid.ROOM_DOOR_FLAG) {
                        for (var direction : Direction.HORIZONTAL) {
                            if (grid.get(x + direction.stepX(), y + direction.stepZ()) == 1) {
                                doorDirs.add(direction);
                            }
                        }
                    }

                    Direction doorDir = null;
                    if (!doorDirs.isEmpty()) {
                        doorDir = doorDirs.get(this.random.nextInt(doorDirs.size()));
                    } else if ((roomData & MansionGrid.ROOM_ORIGIN_FLAG) == MansionGrid.ROOM_ORIGIN_FLAG) {
                        doorDir = Direction.UP;
                    }

                    var roomPos = relative(floorOrigin, rotate(rotation, Direction.SOUTH), 8 + (y - this.startY) * 8);
                    roomPos = relative(roomPos, rotate(rotation, Direction.EAST), -1 + (x - this.startX) * 8);

                    if (MansionGrid.isHouse(grid, x - 1, y) && !mansion.isRoomId(grid, x - 1, y, floorNum, roomId)) {
                        pieces.add(new WoodlandMansionPiece(doorDir == Direction.WEST ? doorPiece : wallPiece, roomPos, rotation));
                    }

                    if (grid.get(x + 1, y) == 1 && !thirdFloorStartRoom) {
                        var posx = relative(roomPos, rotate(rotation, Direction.EAST), 8);
                        pieces.add(new WoodlandMansionPiece(doorDir == Direction.EAST ? doorPiece : wallPiece, posx, rotation));
                    }

                    if (MansionGrid.isHouse(grid, x, y + 1) && !mansion.isRoomId(grid, x, y + 1, floorNum, roomId)) {
                        var posx = relative(roomPos, rotate(rotation, Direction.SOUTH), 7);
                        posx = relative(posx, rotate(rotation, Direction.EAST), 7);
                        pieces.add(new WoodlandMansionPiece(doorDir == Direction.SOUTH ? doorPiece : wallPiece, posx,
                                rotation.getRotated(Rotation.CLOCKWISE_90)));
                    }

                    if (grid.get(x, y - 1) == 1 && !thirdFloorStartRoom) {
                        var posx = relative(roomPos, rotate(rotation, Direction.NORTH), 1);
                        posx = relative(posx, rotate(rotation, Direction.EAST), 7);
                        pieces.add(new WoodlandMansionPiece(doorDir == Direction.NORTH ? doorPiece : wallPiece, posx,
                                rotation.getRotated(Rotation.CLOCKWISE_90)));
                    }

                    if (roomType == MansionGrid.ROOM_1x1) {
                        this.addRoom1x1(pieces, roomPos, rotation, doorDir, roomCollections[floorNum]);
                    } else if (roomType == MansionGrid.ROOM_1x2 && doorDir != null) {
                        var roomDir = mansion.get1x2RoomDirection(grid, x, y, floorNum, roomId);
                        var isStairsRoom = (roomData & MansionGrid.ROOM_STAIRS_FLAG) == MansionGrid.ROOM_STAIRS_FLAG;
                        this.addRoom1x2(pieces, roomPos, rotation, roomDir, doorDir, roomCollections[floorNum], isStairsRoom);
                    } else if (roomType == MansionGrid.ROOM_2x2 && doorDir != null && doorDir != Direction.UP) {
                        var roomDir = doorDir.getClockWise();
                        if (!mansion.isRoomId(grid, x + roomDir.stepX(), y + roomDir.stepZ(), floorNum, roomId)) {
                            roomDir = roomDir.opposite();
                        }
                        this.addRoom2x2(pieces, roomPos, rotation, roomDir, doorDir, roomCollections[floorNum]);
                    } else if (roomType == MansionGrid.ROOM_2x2 && doorDir == Direction.UP) {
                        this.addRoom2x2Secret(pieces, roomPos, rotation, roomCollections[floorNum]);
                    }
                }
            }
        }
    }

    private void traverseOuterWalls(List<WoodlandMansionPiece> pieces, PlacementData data, MansionGrid.SimpleGrid grid,
            Direction gridDirection, int startX, int startY, int endX, int endY) {
        var gridX = startX;
        var gridY = startY;
        var startDirection = gridDirection;

        do {
            if (!MansionGrid.isHouse(grid, gridX + gridDirection.stepX(), gridY + gridDirection.stepZ())) {
                this.traverseTurn(pieces, data);
                gridDirection = gridDirection.getClockWise();
                if (gridX != endX || gridY != endY || startDirection != gridDirection) {
                    this.traverseWallPiece(pieces, data);
                }
            } else if (MansionGrid.isHouse(grid, gridX + gridDirection.stepX(), gridY + gridDirection.stepZ())
                    && MansionGrid.isHouse(grid,
                            gridX + gridDirection.stepX() + gridDirection.getCounterClockWise().stepX(),
                            gridY + gridDirection.stepZ() + gridDirection.getCounterClockWise().stepZ())) {
                this.traverseInnerTurn(pieces, data);
                gridX += gridDirection.stepX();
                gridY += gridDirection.stepZ();
                gridDirection = gridDirection.getCounterClockWise();
            } else {
                gridX += gridDirection.stepX();
                gridY += gridDirection.stepZ();
                if (gridX != endX || gridY != endY || startDirection != gridDirection) {
                    this.traverseWallPiece(pieces, data);
                }
            }
        } while (gridX != endX || gridY != endY || startDirection != gridDirection);
    }

    private void createRoof(List<WoodlandMansionPiece> pieces, BlockVec roofOrigin, Rotation rotation,
            MansionGrid.SimpleGrid grid, MansionGrid.SimpleGrid aboveGrid) {
        for (var y = 0; y < grid.height; y++) {
            for (var x = 0; x < grid.width; x++) {
                var position = relative(roofOrigin, rotate(rotation, Direction.SOUTH), 8 + (y - this.startY) * 8);
                position = relative(position, rotate(rotation, Direction.EAST), (x - this.startX) * 8);
                var isAbove = aboveGrid != null && MansionGrid.isHouse(aboveGrid, x, y);
                if (MansionGrid.isHouse(grid, x, y) && !isAbove) {
                    pieces.add(new WoodlandMansionPiece("roof", above(position, 3), rotation));

                    if (!MansionGrid.isHouse(grid, x + 1, y)) {
                        var p2 = relative(position, rotate(rotation, Direction.EAST), 6);
                        pieces.add(new WoodlandMansionPiece("roof_front", p2, rotation));
                    }

                    if (!MansionGrid.isHouse(grid, x - 1, y)) {
                        var p2 = relative(position, rotate(rotation, Direction.EAST), 0);
                        p2 = relative(p2, rotate(rotation, Direction.SOUTH), 7);
                        pieces.add(new WoodlandMansionPiece("roof_front", p2, rotation.getRotated(Rotation.CLOCKWISE_180)));
                    }

                    if (!MansionGrid.isHouse(grid, x, y - 1)) {
                        var p2 = relative(position, rotate(rotation, Direction.WEST), 1);
                        pieces.add(new WoodlandMansionPiece("roof_front", p2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                    }

                    if (!MansionGrid.isHouse(grid, x, y + 1)) {
                        var p2 = relative(position, rotate(rotation, Direction.EAST), 6);
                        p2 = relative(p2, rotate(rotation, Direction.SOUTH), 6);
                        pieces.add(new WoodlandMansionPiece("roof_front", p2, rotation.getRotated(Rotation.CLOCKWISE_90)));
                    }
                }
            }
        }

        if (aboveGrid != null) {
            for (var y = 0; y < grid.height; y++) {
                for (var x = 0; x < grid.width; x++) {
                    var origin = relative(roofOrigin, rotate(rotation, Direction.SOUTH), 8 + (y - this.startY) * 8);
                    origin = relative(origin, rotate(rotation, Direction.EAST), (x - this.startX) * 8);
                    var isAbove = MansionGrid.isHouse(aboveGrid, x, y);
                    if (MansionGrid.isHouse(grid, x, y) && isAbove) {
                        if (!MansionGrid.isHouse(grid, x + 1, y)) {
                            var p2 = relative(origin, rotate(rotation, Direction.EAST), 7);
                            pieces.add(new WoodlandMansionPiece("small_wall", p2, rotation));
                        }

                        if (!MansionGrid.isHouse(grid, x - 1, y)) {
                            var p2 = relative(origin, rotate(rotation, Direction.WEST), 1);
                            p2 = relative(p2, rotate(rotation, Direction.SOUTH), 6);
                            pieces.add(new WoodlandMansionPiece("small_wall", p2, rotation.getRotated(Rotation.CLOCKWISE_180)));
                        }

                        if (!MansionGrid.isHouse(grid, x, y - 1)) {
                            var p2 = relative(origin, rotate(rotation, Direction.WEST), 0);
                            p2 = relative(p2, rotate(rotation, Direction.NORTH), 1);
                            pieces.add(new WoodlandMansionPiece("small_wall", p2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                        }

                        if (!MansionGrid.isHouse(grid, x, y + 1)) {
                            var p2 = relative(origin, rotate(rotation, Direction.EAST), 6);
                            p2 = relative(p2, rotate(rotation, Direction.SOUTH), 7);
                            pieces.add(new WoodlandMansionPiece("small_wall", p2, rotation.getRotated(Rotation.CLOCKWISE_90)));
                        }

                        if (!MansionGrid.isHouse(grid, x + 1, y)) {
                            if (!MansionGrid.isHouse(grid, x, y - 1)) {
                                var p2 = relative(origin, rotate(rotation, Direction.EAST), 7);
                                p2 = relative(p2, rotate(rotation, Direction.NORTH), 2);
                                pieces.add(new WoodlandMansionPiece("small_wall_corner", p2, rotation));
                            }

                            if (!MansionGrid.isHouse(grid, x, y + 1)) {
                                var p2 = relative(origin, rotate(rotation, Direction.EAST), 8);
                                p2 = relative(p2, rotate(rotation, Direction.SOUTH), 7);
                                pieces.add(new WoodlandMansionPiece("small_wall_corner", p2, rotation.getRotated(Rotation.CLOCKWISE_90)));
                            }
                        }

                        if (!MansionGrid.isHouse(grid, x - 1, y)) {
                            if (!MansionGrid.isHouse(grid, x, y - 1)) {
                                var p2 = relative(origin, rotate(rotation, Direction.WEST), 2);
                                p2 = relative(p2, rotate(rotation, Direction.NORTH), 1);
                                pieces.add(new WoodlandMansionPiece("small_wall_corner", p2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                            }

                            if (!MansionGrid.isHouse(grid, x, y + 1)) {
                                var p2 = relative(origin, rotate(rotation, Direction.WEST), 1);
                                p2 = relative(p2, rotate(rotation, Direction.SOUTH), 8);
                                pieces.add(new WoodlandMansionPiece("small_wall_corner", p2, rotation.getRotated(Rotation.CLOCKWISE_180)));
                            }
                        }
                    }
                }
            }
        }

        for (var y = 0; y < grid.height; y++) {
            for (var x = 0; x < grid.width; x++) {
                var origin = relative(roofOrigin, rotate(rotation, Direction.SOUTH), 8 + (y - this.startY) * 8);
                origin = relative(origin, rotate(rotation, Direction.EAST), (x - this.startX) * 8);
                var isAbove = aboveGrid != null && MansionGrid.isHouse(aboveGrid, x, y);
                if (!MansionGrid.isHouse(grid, x, y) || isAbove) {
                    continue;
                }

                if (!MansionGrid.isHouse(grid, x + 1, y)) {
                    var p2 = relative(origin, rotate(rotation, Direction.EAST), 6);
                    if (!MansionGrid.isHouse(grid, x, y + 1)) {
                        var p3 = relative(p2, rotate(rotation, Direction.SOUTH), 6);
                        pieces.add(new WoodlandMansionPiece("roof_corner", p3, rotation));
                    } else if (MansionGrid.isHouse(grid, x + 1, y + 1)) {
                        var p3 = relative(p2, rotate(rotation, Direction.SOUTH), 5);
                        pieces.add(new WoodlandMansionPiece("roof_inner_corner", p3, rotation));
                    }

                    if (!MansionGrid.isHouse(grid, x, y - 1)) {
                        pieces.add(new WoodlandMansionPiece("roof_corner", p2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                    } else if (MansionGrid.isHouse(grid, x + 1, y - 1)) {
                        var p3 = relative(origin, rotate(rotation, Direction.EAST), 9);
                        p3 = relative(p3, rotate(rotation, Direction.NORTH), 2);
                        pieces.add(new WoodlandMansionPiece("roof_inner_corner", p3, rotation.getRotated(Rotation.CLOCKWISE_90)));
                    }
                }

                if (!MansionGrid.isHouse(grid, x - 1, y)) {
                    var p2 = relative(origin, rotate(rotation, Direction.EAST), 0);
                    p2 = relative(p2, rotate(rotation, Direction.SOUTH), 0);
                    if (!MansionGrid.isHouse(grid, x, y + 1)) {
                        var p3 = relative(p2, rotate(rotation, Direction.SOUTH), 6);
                        pieces.add(new WoodlandMansionPiece("roof_corner", p3, rotation.getRotated(Rotation.CLOCKWISE_90)));
                    } else if (MansionGrid.isHouse(grid, x - 1, y + 1)) {
                        var p3 = relative(p2, rotate(rotation, Direction.SOUTH), 8);
                        p3 = relative(p3, rotate(rotation, Direction.WEST), 3);
                        pieces.add(new WoodlandMansionPiece("roof_inner_corner", p3, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
                    }

                    if (!MansionGrid.isHouse(grid, x, y - 1)) {
                        pieces.add(new WoodlandMansionPiece("roof_corner", p2, rotation.getRotated(Rotation.CLOCKWISE_180)));
                    } else if (MansionGrid.isHouse(grid, x - 1, y - 1)) {
                        var p3 = relative(p2, rotate(rotation, Direction.SOUTH), 1);
                        pieces.add(new WoodlandMansionPiece("roof_inner_corner", p3, rotation.getRotated(Rotation.CLOCKWISE_180)));
                    }
                }
            }
        }
    }

    private void entrance(List<WoodlandMansionPiece> pieces, PlacementData data) {
        var west = rotate(data.rotation, Direction.WEST);
        pieces.add(new WoodlandMansionPiece("entrance", relative(data.position, west, 9), data.rotation));
        data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), 16);
    }

    private void traverseWallPiece(List<WoodlandMansionPiece> pieces, PlacementData data) {
        pieces.add(new WoodlandMansionPiece(data.wallType, relative(data.position, rotate(data.rotation, Direction.EAST), 7), data.rotation));
        data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), 8);
    }

    private void traverseTurn(List<WoodlandMansionPiece> pieces, PlacementData data) {
        data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), -1);
        pieces.add(new WoodlandMansionPiece("wall_corner", data.position, data.rotation));
        data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), -7);
        data.position = relative(data.position, rotate(data.rotation, Direction.WEST), -6);
        data.rotation = data.rotation.getRotated(Rotation.CLOCKWISE_90);
    }

    private void traverseInnerTurn(List<WoodlandMansionPiece> pieces, PlacementData data) {
        data.position = relative(data.position, rotate(data.rotation, Direction.SOUTH), 6);
        data.position = relative(data.position, rotate(data.rotation, Direction.EAST), 8);
        data.rotation = data.rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
    }

    private void addRoom1x1(List<WoodlandMansionPiece> pieces, BlockVec roomPos, Rotation rotation, Direction doorDir,
            FloorRoomCollection rooms) {
        var pieceRot = Rotation.NONE;
        var roomType = rooms.get1x1(this.random);
        if (doorDir != Direction.EAST) {
            if (doorDir == Direction.NORTH) {
                pieceRot = pieceRot.getRotated(Rotation.COUNTERCLOCKWISE_90);
            } else if (doorDir == Direction.WEST) {
                pieceRot = pieceRot.getRotated(Rotation.CLOCKWISE_180);
            } else if (doorDir == Direction.SOUTH) {
                pieceRot = pieceRot.getRotated(Rotation.CLOCKWISE_90);
            } else {
                roomType = rooms.get1x1Secret(this.random);
            }
        }

        var orientation = StructureTemplate.getZeroPositionWithTransform(
                new BlockVec(1, 0, 0), Mirror.NONE, pieceRot, 7, 7);
        pieceRot = pieceRot.getRotated(rotation);
        orientation = rotateBlockVec(rotation, orientation);
        var pos = roomPos.add(orientation.blockX(), 0, orientation.blockZ());
        pieces.add(new WoodlandMansionPiece(roomType, pos, pieceRot));
    }

    private void addRoom1x2(List<WoodlandMansionPiece> pieces, BlockVec roomPos, Rotation rotation, Direction roomDir,
            Direction doorDir, FloorRoomCollection rooms, boolean isStairsRoom) {
        if (doorDir == Direction.EAST && roomDir == Direction.SOUTH) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 1);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos, rotation));
        } else if (doorDir == Direction.EAST && roomDir == Direction.NORTH) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 1);
            pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos, rotation, Mirror.LEFT_RIGHT));
        } else if (doorDir == Direction.WEST && roomDir == Direction.NORTH) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 7);
            pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.CLOCKWISE_180)));
        } else if (doorDir == Direction.WEST && roomDir == Direction.SOUTH) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 7);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos, rotation, Mirror.FRONT_BACK));
        } else if (doorDir == Direction.SOUTH && roomDir == Direction.EAST) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 1);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.CLOCKWISE_90), Mirror.LEFT_RIGHT));
        } else if (doorDir == Direction.SOUTH && roomDir == Direction.WEST) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 7);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.CLOCKWISE_90)));
        } else if (doorDir == Direction.NORTH && roomDir == Direction.WEST) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 7);
            pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.CLOCKWISE_90), Mirror.FRONT_BACK));
        } else if (doorDir == Direction.NORTH && roomDir == Direction.EAST) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 1);
            pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2SideEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
        } else if (doorDir == Direction.SOUTH && roomDir == Direction.NORTH) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 1);
            pos = relative(pos, rotate(rotation, Direction.NORTH), 8);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2FrontEntrance(this.random, isStairsRoom), pos, rotation));
        } else if (doorDir == Direction.NORTH && roomDir == Direction.SOUTH) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 7);
            pos = relative(pos, rotate(rotation, Direction.SOUTH), 14);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2FrontEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.CLOCKWISE_180)));
        } else if (doorDir == Direction.WEST && roomDir == Direction.EAST) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 15);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2FrontEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.CLOCKWISE_90)));
        } else if (doorDir == Direction.EAST && roomDir == Direction.WEST) {
            var pos = relative(roomPos, rotate(rotation, Direction.WEST), 7);
            pos = relative(pos, rotate(rotation, Direction.SOUTH), 6);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2FrontEntrance(this.random, isStairsRoom), pos,
                    rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)));
        } else if (doorDir == Direction.UP && roomDir == Direction.EAST) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 15);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2Secret(this.random), pos, rotation.getRotated(Rotation.CLOCKWISE_90)));
        } else if (doorDir == Direction.UP && roomDir == Direction.SOUTH) {
            var pos = relative(roomPos, rotate(rotation, Direction.EAST), 1);
            pos = relative(pos, rotate(rotation, Direction.NORTH), 0);
            pieces.add(new WoodlandMansionPiece(rooms.get1x2Secret(this.random), pos, rotation));
        }
    }

    private void addRoom2x2(List<WoodlandMansionPiece> pieces, BlockVec roomPos, Rotation rotation, Direction roomDir,
            Direction doorDir, FloorRoomCollection rooms) {
        var east = 0;
        var south = 0;
        var rot = rotation;
        var mirror = Mirror.NONE;
        if (doorDir == Direction.EAST && roomDir == Direction.SOUTH) {
            east = -7;
        } else if (doorDir == Direction.EAST && roomDir == Direction.NORTH) {
            east = -7;
            south = 6;
            mirror = Mirror.LEFT_RIGHT;
        } else if (doorDir == Direction.NORTH && roomDir == Direction.EAST) {
            east = 1;
            south = 14;
            rot = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
        } else if (doorDir == Direction.NORTH && roomDir == Direction.WEST) {
            east = 7;
            south = 14;
            rot = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
            mirror = Mirror.LEFT_RIGHT;
        } else if (doorDir == Direction.SOUTH && roomDir == Direction.WEST) {
            east = 7;
            south = -8;
            rot = rotation.getRotated(Rotation.CLOCKWISE_90);
        } else if (doorDir == Direction.SOUTH && roomDir == Direction.EAST) {
            east = 1;
            south = -8;
            rot = rotation.getRotated(Rotation.CLOCKWISE_90);
            mirror = Mirror.LEFT_RIGHT;
        } else if (doorDir == Direction.WEST && roomDir == Direction.NORTH) {
            east = 15;
            south = 6;
            rot = rotation.getRotated(Rotation.CLOCKWISE_180);
        } else if (doorDir == Direction.WEST && roomDir == Direction.SOUTH) {
            east = 15;
            mirror = Mirror.FRONT_BACK;
        }

        var pos = relative(roomPos, rotate(rotation, Direction.EAST), east);
        pos = relative(pos, rotate(rotation, Direction.SOUTH), south);
        pieces.add(new WoodlandMansionPiece(rooms.get2x2(this.random), pos, rot, mirror));
    }

    private void addRoom2x2Secret(List<WoodlandMansionPiece> pieces, BlockVec roomPos, Rotation rotation, FloorRoomCollection rooms) {
        var pos = relative(roomPos, rotate(rotation, Direction.EAST), 1);
        pieces.add(new WoodlandMansionPiece(rooms.get2x2Secret(this.random), pos, rotation, Mirror.NONE));
    }

    /** Vanilla {@code Rotation.rotate(Direction)} applied to the feature-package {@link Direction}. */
    private static Direction rotate(Rotation rotation, Direction direction) {
        if (direction.stepY() != 0) {
            return direction;
        }
        return switch (rotation) {
            case CLOCKWISE_90 -> direction.getClockWise();
            case CLOCKWISE_180 -> direction.opposite();
            case COUNTERCLOCKWISE_90 -> direction.getCounterClockWise();
            case NONE -> direction;
        };
    }

    private static BlockVec rotateBlockVec(Rotation rotation, BlockVec position) {
        return rotation.rotate(position, BlockVec.ZERO);
    }

    private static BlockVec relative(BlockVec position, Direction direction, int distance) {
        return position.add(direction.stepX() * distance, direction.stepY() * distance, direction.stepZ() * distance);
    }

    private static BlockVec above(BlockVec position, int distance) {
        return position.add(0, distance, 0);
    }

    private static final class PlacementData {
        Rotation rotation;
        BlockVec position;
        String wallType;
    }
}
