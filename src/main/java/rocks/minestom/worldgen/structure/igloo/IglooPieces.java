package rocks.minestom.worldgen.structure.igloo;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of vanilla {@code IglooPieces}: the three template locations with their
 * per-template pivots and offsets, plus the {@code addPieces} draw order. The
 * basement roll and ladder shaft are built downward in 3-block increments; the
 * per-piece surface height is resolved afterward by {@link IglooPlacer},
 * mirroring vanilla's {@code IglooPiece.postProcess}.
 */
final class IglooPieces {
    static final Key STRUCTURE_LOCATION_TOP = Key.key("minecraft", "igloo/top");
    static final Key STRUCTURE_LOCATION_MIDDLE = Key.key("minecraft", "igloo/middle");
    static final Key STRUCTURE_LOCATION_BOTTOM = Key.key("minecraft", "igloo/bottom");

    static final Map<Key, BlockVec> PIVOTS = Map.of(
            STRUCTURE_LOCATION_TOP, new BlockVec(3, 5, 5),
            STRUCTURE_LOCATION_MIDDLE, new BlockVec(1, 3, 1),
            STRUCTURE_LOCATION_BOTTOM, new BlockVec(3, 6, 7));
    static final Map<Key, BlockVec> OFFSETS = Map.of(
            STRUCTURE_LOCATION_TOP, BlockVec.ZERO,
            STRUCTURE_LOCATION_MIDDLE, new BlockVec(2, -3, 4),
            STRUCTURE_LOCATION_BOTTOM, new BlockVec(0, -3, -2));

    private IglooPieces() {
    }

    record Piece(Key template, BlockVec position, Rotation rotation) {
    }

    static List<Piece> addPieces(BlockVec position, Rotation rotation, RandomSource random) {
        var pieces = new ArrayList<Piece>();
        if (random.nextDouble() < 0.5) {
            var depth = random.nextInt(8) + 4;
            pieces.add(makePiece(STRUCTURE_LOCATION_BOTTOM, position, rotation, depth * 3));
            for (var index = 0; index < depth - 1; index++) {
                pieces.add(makePiece(STRUCTURE_LOCATION_MIDDLE, position, rotation, index * 3));
            }
        }
        pieces.add(makePiece(STRUCTURE_LOCATION_TOP, position, rotation, 0));
        return pieces;
    }

    private static Piece makePiece(Key template, BlockVec position, Rotation rotation, int depth) {
        var offset = OFFSETS.get(template);
        var templatePosition = position.add(offset.blockX(), offset.blockY() - depth, offset.blockZ());
        return new Piece(template, templatePosition, rotation);
    }
}
