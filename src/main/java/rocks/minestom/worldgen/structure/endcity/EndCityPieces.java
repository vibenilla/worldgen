package rocks.minestom.worldgen.structure.endcity;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code EndCityPieces}: the recursive template assembly
 * that builds an end city from a house tower, connecting towers, bridges,
 * a fat tower branch and (with a chance that shrinks with recursion depth)
 * the end ship.
 *
 * <p>Each piece is placed relative to its parent by rotating a local-space
 * offset by the parent's rotation and adding it to the parent's position -
 * vanilla {@code StructureTemplate.calculateConnectedPosition} with the
 * default (zero) rotation pivot and no mirroring, which reduces to exactly
 * this. Collisions between sibling branches are rejected the same way
 * vanilla does: a whole generator's output is tagged with one shared random
 * {@code genDepth}, then discarded if it collides with any earlier piece
 * from a different lineage.
 */
public final class EndCityPieces {
    private static final int MAX_GEN_DEPTH = 8;

    private static final List<Bridge> TOWER_BRIDGES = List.of(
            new Bridge(Rotation.NONE, new BlockVec(1, -1, 0)),
            new Bridge(Rotation.CLOCKWISE_90, new BlockVec(6, -1, 1)),
            new Bridge(Rotation.COUNTERCLOCKWISE_90, new BlockVec(0, -1, 5)),
            new Bridge(Rotation.CLOCKWISE_180, new BlockVec(5, -1, 6))
    );

    private static final List<Bridge> FAT_TOWER_BRIDGES = List.of(
            new Bridge(Rotation.NONE, new BlockVec(4, -1, 0)),
            new Bridge(Rotation.CLOCKWISE_90, new BlockVec(12, -1, 4)),
            new Bridge(Rotation.COUNTERCLOCKWISE_90, new BlockVec(0, -1, 8)),
            new Bridge(Rotation.CLOCKWISE_180, new BlockVec(8, -1, 12))
    );

    private EndCityPieces() {
    }

    /**
     * Vanilla {@code EndCityPieces.startHouseTower}: builds the initial house
     * (base floor, two upper floors and a roof), then recurses into
     * {@link #towerGenerator} for everything above it.
     */
    public static List<Piece> startHouseTower(StructureLoader loader, BlockVec origin, Rotation rotation,
            RandomSource random) {
        var pieces = new ArrayList<Piece>();
        var state = new BridgeState();
        var last = addHelper(pieces, new Piece(loader, "base_floor", origin, rotation, true));
        last = addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 0, -1), "second_floor_1", rotation, false));
        last = addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 4, -1), "third_floor_1", rotation, false));
        last = addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 8, -1), "third_roof", rotation, true));
        recursiveChildren(loader, EndCityPieces::towerGenerator, 1, last, null, pieces, random, state);
        return pieces;
    }

    /**
     * Vanilla {@code HOUSE_TOWER_GENERATOR}: a smaller house built on top of a
     * tower bridge, with a chance of another one or two floors before
     * recursing back into {@link #towerGenerator}.
     */
    private static boolean houseTowerGenerator(StructureLoader loader, int genDepth, Piece parent, BlockVec offset,
            List<Piece> pieces, RandomSource random, BridgeState state) {
        if (genDepth > MAX_GEN_DEPTH) {
            return false;
        }

        var rotation = parent.rotation;
        var last = addHelper(pieces, addPiece(loader, parent, offset, "base_floor", rotation, true));
        var numFloors = random.nextInt(3);
        if (numFloors == 0) {
            addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 4, -1), "base_roof", rotation, true));
        } else if (numFloors == 1) {
            last = addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 0, -1), "second_floor_2", rotation, false));
            addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 8, -1), "second_roof", rotation, false));
            recursiveChildren(loader, EndCityPieces::towerGenerator, genDepth + 1, last, null, pieces, random, state);
        } else if (numFloors == 2) {
            last = addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 0, -1), "second_floor_2", rotation, false));
            last = addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 4, -1), "third_floor_2", rotation, false));
            addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 8, -1), "third_roof", rotation, true));
            recursiveChildren(loader, EndCityPieces::towerGenerator, genDepth + 1, last, null, pieces, random, state);
        }

        return true;
    }

    /**
     * Vanilla {@code TOWER_GENERATOR}: a tower of one to three segments,
     * branching into bridges (and their own house towers) from at most one
     * segment, or into a fat tower when no bridge was chosen.
     */
    private static boolean towerGenerator(StructureLoader loader, int genDepth, Piece parent, BlockVec offset,
            List<Piece> pieces, RandomSource random, BridgeState state) {
        var rotation = parent.rotation;
        var last = addHelper(pieces, addPiece(loader, parent,
                new BlockVec(3 + random.nextInt(2), -3, 3 + random.nextInt(2)), "tower_base", rotation, true));
        last = addHelper(pieces, addPiece(loader, last, new BlockVec(0, 7, 0), "tower_piece", rotation, true));
        var bridgePiece = random.nextInt(3) == 0 ? last : null;
        var towerHeight = 1 + random.nextInt(3);

        for (var i = 0; i < towerHeight; i++) {
            last = addHelper(pieces, addPiece(loader, last, new BlockVec(0, 4, 0), "tower_piece", rotation, true));
            if (i < towerHeight - 1 && random.nextBoolean()) {
                bridgePiece = last;
            }
        }

        if (bridgePiece != null) {
            for (var bridge : TOWER_BRIDGES) {
                if (random.nextBoolean()) {
                    var bridgeStart = addHelper(pieces, addPiece(loader, bridgePiece, bridge.offset(), "bridge_end",
                            rotation.getRotated(bridge.rotation()), true));
                    recursiveChildren(loader, EndCityPieces::towerBridgeGenerator, genDepth + 1, bridgeStart, null,
                            pieces, random, state);
                }
            }

            addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 4, -1), "tower_top", rotation, true));
        } else {
            if (genDepth != 7) {
                return recursiveChildren(loader, EndCityPieces::fatTowerGenerator, genDepth + 1, last, null, pieces,
                        random, state);
            }

            addHelper(pieces, addPiece(loader, last, new BlockVec(-1, 4, -1), "tower_top", rotation, true));
        }

        return true;
    }

    /**
     * Vanilla {@code TOWER_BRIDGE_GENERATOR}: a chain of straight bridge
     * segments and stairs, ending in either the end ship (once per start, with
     * a chance that shrinks as the recursion gets deeper) or another house
     * tower.
     */
    private static boolean towerBridgeGenerator(StructureLoader loader, int genDepth, Piece parent, BlockVec offset,
            List<Piece> pieces, RandomSource random, BridgeState state) {
        var rotation = parent.rotation;
        var bridgeLength = random.nextInt(4) + 1;
        var last = addHelper(pieces, addPiece(loader, parent, new BlockVec(0, 0, -4), "bridge_piece", rotation, true));
        last.genDepth = -1;
        var nextY = 0;

        for (var i = 0; i < bridgeLength; i++) {
            if (random.nextBoolean()) {
                last = addHelper(pieces,
                        addPiece(loader, last, new BlockVec(0, nextY, -4), "bridge_piece", rotation, true));
                nextY = 0;
            } else {
                if (random.nextBoolean()) {
                    last = addHelper(pieces, addPiece(loader, last, new BlockVec(0, nextY, -4),
                            "bridge_steep_stairs", rotation, true));
                } else {
                    last = addHelper(pieces, addPiece(loader, last, new BlockVec(0, nextY, -8),
                            "bridge_gentle_stairs", rotation, true));
                }
                nextY = 4;
            }
        }

        if (!state.shipCreated && random.nextInt(10 - genDepth) == 0) {
            addHelper(pieces, addPiece(loader, last,
                    new BlockVec(-8 + random.nextInt(8), nextY, -70 + random.nextInt(10)), "ship", rotation, true));
            state.shipCreated = true;
        } else if (!recursiveChildren(loader, EndCityPieces::houseTowerGenerator, genDepth + 1, last,
                new BlockVec(-3, nextY + 1, -11), pieces, random, state)) {
            return false;
        }

        last = addHelper(pieces, addPiece(loader, last, new BlockVec(4, nextY, 0), "bridge_end",
                rotation.getRotated(Rotation.CLOCKWISE_180), true));
        last.genDepth = -1;
        return true;
    }

    /**
     * Vanilla {@code FAT_TOWER_GENERATOR}: a wide tower of one to three
     * segments, each (after the first) branching bridges the same way
     * {@link #towerGenerator} does.
     */
    private static boolean fatTowerGenerator(StructureLoader loader, int genDepth, Piece parent, BlockVec offset,
            List<Piece> pieces, RandomSource random, BridgeState state) {
        var rotation = parent.rotation;
        var last = addHelper(pieces, addPiece(loader, parent, new BlockVec(-3, 4, -3), "fat_tower_base", rotation, true));
        last = addHelper(pieces, addPiece(loader, last, new BlockVec(0, 4, 0), "fat_tower_middle", rotation, true));

        for (var i = 0; i < 2 && random.nextInt(3) != 0; i++) {
            last = addHelper(pieces, addPiece(loader, last, new BlockVec(0, 8, 0), "fat_tower_middle", rotation, true));

            for (var bridge : FAT_TOWER_BRIDGES) {
                if (random.nextBoolean()) {
                    var bridgeStart = addHelper(pieces, addPiece(loader, last, bridge.offset(), "bridge_end",
                            rotation.getRotated(bridge.rotation()), true));
                    recursiveChildren(loader, EndCityPieces::towerBridgeGenerator, genDepth + 1, bridgeStart, null,
                            pieces, random, state);
                }
            }
        }

        addHelper(pieces, addPiece(loader, last, new BlockVec(-2, 8, -2), "fat_tower_top", rotation, true));
        return true;
    }

    /**
     * Vanilla {@code addPiece}: the child's rotation may differ from the
     * parent's (bridge branches turn), but the offset that positions it is
     * always expressed in - and rotated by - the parent's own rotation.
     */
    private static Piece addPiece(StructureLoader loader, Piece parent, BlockVec offset, String templateName,
            Rotation rotation, boolean overwrite) {
        var delta = parent.rotation.rotate(offset, BlockVec.ZERO);
        var position = new BlockVec(
                parent.position.blockX() + delta.blockX(),
                parent.position.blockY() + delta.blockY(),
                parent.position.blockZ() + delta.blockZ());
        return new Piece(loader, templateName, position, rotation, overwrite);
    }

    private static Piece addHelper(List<Piece> pieces, Piece piece) {
        pieces.add(piece);
        return piece;
    }

    /**
     * Vanilla {@code recursiveChildren}: builds a generator's pieces into a
     * fresh list, tags them all with one shared random depth marker, and
     * keeps them only if none collides with an earlier piece from a
     * different lineage (same-lineage collisions - a piece touching its own
     * parent batch - are expected and allowed).
     */
    private static boolean recursiveChildren(StructureLoader loader, Generator generator, int genDepth, Piece parent,
            BlockVec offset, List<Piece> pieces, RandomSource random, BridgeState state) {
        if (genDepth > MAX_GEN_DEPTH) {
            return false;
        }

        var childPieces = new ArrayList<Piece>();
        if (generator.generate(loader, genDepth, parent, offset, childPieces, random, state)) {
            var collision = false;
            var childTag = random.nextInt();

            for (var child : childPieces) {
                child.genDepth = childTag;
                var collisionPiece = findCollisionPiece(pieces, child.boundingBox);
                if (collisionPiece != null && collisionPiece.genDepth != parent.genDepth) {
                    collision = true;
                    break;
                }
            }

            if (!collision) {
                pieces.addAll(childPieces);
                return true;
            }
        }

        return false;
    }

    private static Piece findCollisionPiece(List<Piece> pieces, BoundingBox box) {
        for (var piece : pieces) {
            if (piece.boundingBox.intersects(box)) {
                return piece;
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Generator {
        boolean generate(StructureLoader loader, int genDepth, Piece parent, BlockVec offset, List<Piece> pieces,
                RandomSource random, BridgeState state);
    }

    /** Vanilla {@code TOWER_BRIDGE_GENERATOR.shipCreated}: at most one end ship per start. */
    private static final class BridgeState {
        boolean shipCreated;
    }

    private record Bridge(Rotation rotation, BlockVec offset) {
    }

    /**
     * Vanilla {@code EndCityPieces.EndCityPiece}: a single placed template,
     * with a mutable {@code genDepth} lineage tag used only for collision
     * resolution during assembly.
     */
    public static final class Piece {
        public final Key templateKey;
        public final BlockVec position;
        public final Rotation rotation;
        public final boolean overwrite;
        public final BoundingBox boundingBox;
        int genDepth;

        Piece(StructureLoader loader, String templateName, BlockVec position, Rotation rotation, boolean overwrite) {
            this.templateKey = Key.key("minecraft", "end_city/" + templateName);
            this.position = position;
            this.rotation = rotation;
            this.overwrite = overwrite;
            this.boundingBox = loader.getTemplate(this.templateKey).getBoundingBox(position, rotation);
        }
    }
}
