package rocks.minestom.worldgen.structure.oceanruin;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code OceanRuinPieces}: the template selection arrays and
 * {@code addPieces}/{@code addClusterRuins} draw order. Every piece is
 * returned with its build-time position ({@code y = 90}); the per-piece floor
 * height is resolved afterward by {@link OceanRuinPlacer}, mirroring vanilla's
 * {@code OceanRuinPiece.postProcess}.
 */
final class OceanRuinPieces {
    private static final Key[] WARM_RUINS = keys(
            "underwater_ruin/warm_1", "underwater_ruin/warm_2", "underwater_ruin/warm_3", "underwater_ruin/warm_4",
            "underwater_ruin/warm_5", "underwater_ruin/warm_6", "underwater_ruin/warm_7", "underwater_ruin/warm_8");
    private static final Key[] RUINS_BRICK = keys(
            "underwater_ruin/brick_1", "underwater_ruin/brick_2", "underwater_ruin/brick_3", "underwater_ruin/brick_4",
            "underwater_ruin/brick_5", "underwater_ruin/brick_6", "underwater_ruin/brick_7", "underwater_ruin/brick_8");
    private static final Key[] RUINS_CRACKED = keys(
            "underwater_ruin/cracked_1", "underwater_ruin/cracked_2", "underwater_ruin/cracked_3", "underwater_ruin/cracked_4",
            "underwater_ruin/cracked_5", "underwater_ruin/cracked_6", "underwater_ruin/cracked_7", "underwater_ruin/cracked_8");
    private static final Key[] RUINS_MOSSY = keys(
            "underwater_ruin/mossy_1", "underwater_ruin/mossy_2", "underwater_ruin/mossy_3", "underwater_ruin/mossy_4",
            "underwater_ruin/mossy_5", "underwater_ruin/mossy_6", "underwater_ruin/mossy_7", "underwater_ruin/mossy_8");
    private static final Key[] BIG_RUINS_BRICK = keys(
            "underwater_ruin/big_brick_1", "underwater_ruin/big_brick_2", "underwater_ruin/big_brick_3", "underwater_ruin/big_brick_8");
    private static final Key[] BIG_RUINS_MOSSY = keys(
            "underwater_ruin/big_mossy_1", "underwater_ruin/big_mossy_2", "underwater_ruin/big_mossy_3", "underwater_ruin/big_mossy_8");
    private static final Key[] BIG_RUINS_CRACKED = keys(
            "underwater_ruin/big_cracked_1", "underwater_ruin/big_cracked_2", "underwater_ruin/big_cracked_3", "underwater_ruin/big_cracked_8");
    private static final Key[] BIG_WARM_RUINS = keys(
            "underwater_ruin/big_warm_4", "underwater_ruin/big_warm_5", "underwater_ruin/big_warm_6", "underwater_ruin/big_warm_7");

    private OceanRuinPieces() {
    }

    private static Key[] keys(String... names) {
        var result = new Key[names.length];
        for (var index = 0; index < names.length; index++) {
            result[index] = Key.key("minecraft", names[index]);
        }
        return result;
    }

    record Piece(Key template, BlockVec position, Rotation rotation, float integrity, boolean isLarge) {
    }

    static List<Piece> addPieces(BlockVec position, Rotation rotation, RandomSource random, OceanRuinStructure structure) {
        var pieces = new ArrayList<Piece>();
        var isLarge = random.nextFloat() <= structure.largeProbability();
        var baseIntegrity = isLarge ? 0.9F : 0.8F;
        addPiece(pieces, position, rotation, random, structure, isLarge, baseIntegrity);
        if (isLarge && random.nextFloat() <= structure.clusterProbability()) {
            addClusterRuins(pieces, random, rotation, position, structure);
        }
        return pieces;
    }

    private static void addClusterRuins(List<Piece> pieces, RandomSource random, Rotation rotation, BlockVec p,
            OceanRuinStructure structure) {
        var parentPos = new BlockVec(p.blockX(), 90, p.blockZ());
        var parentCorner = parentPos.add(rotation.rotate(new BlockVec(15, 0, 15), BlockVec.ZERO));
        var parentBB = BoundingBox.fromCorners(parentPos, parentCorner);
        var parentBottomLeft = new BlockVec(
                Math.min(parentPos.blockX(), parentCorner.blockX()), parentPos.blockY(),
                Math.min(parentPos.blockZ(), parentCorner.blockZ()));
        var allPositions = allPositions(random, parentBottomLeft);
        var ruins = nextInt(random, 4, 8);

        for (var index = 0; index < ruins; index++) {
            if (!allPositions.isEmpty()) {
                var pickedIndex = random.nextInt(allPositions.size());
                var pos = allPositions.remove(pickedIndex);
                var nextRotation = Rotation.getRandom(random);
                var nextCorner = pos.add(nextRotation.rotate(new BlockVec(5, 0, 6), BlockVec.ZERO));
                var nextBB = BoundingBox.fromCorners(pos, nextCorner);
                if (!nextBB.intersects(parentBB)) {
                    addPiece(pieces, pos, nextRotation, random, structure, false, 0.8F);
                }
            }
        }
    }

    private static List<BlockVec> allPositions(RandomSource random, BlockVec origin) {
        var positions = new ArrayList<BlockVec>();
        positions.add(origin.add(-16 + nextInt(random, 1, 8), 0, 16 + nextInt(random, 1, 7)));
        positions.add(origin.add(-16 + nextInt(random, 1, 8), 0, nextInt(random, 1, 7)));
        positions.add(origin.add(-16 + nextInt(random, 1, 8), 0, -16 + nextInt(random, 4, 8)));
        positions.add(origin.add(nextInt(random, 1, 7), 0, 16 + nextInt(random, 1, 7)));
        positions.add(origin.add(nextInt(random, 1, 7), 0, -16 + nextInt(random, 4, 6)));
        positions.add(origin.add(16 + nextInt(random, 1, 7), 0, 16 + nextInt(random, 3, 8)));
        positions.add(origin.add(16 + nextInt(random, 1, 7), 0, nextInt(random, 1, 7)));
        positions.add(origin.add(16 + nextInt(random, 1, 7), 0, -16 + nextInt(random, 4, 8)));
        return positions;
    }

    private static void addPiece(List<Piece> pieces, BlockVec position, Rotation rotation, RandomSource random,
            OceanRuinStructure structure, boolean isLarge, float baseIntegrity) {
        switch (structure.biomeTemp()) {
            case COLD -> {
                var bricks = isLarge ? BIG_RUINS_BRICK : RUINS_BRICK;
                var cracked = isLarge ? BIG_RUINS_CRACKED : RUINS_CRACKED;
                var mossy = isLarge ? BIG_RUINS_MOSSY : RUINS_MOSSY;
                var index = random.nextInt(bricks.length);
                pieces.add(new Piece(bricks[index], position, rotation, baseIntegrity, isLarge));
                pieces.add(new Piece(cracked[index], position, rotation, 0.7F, isLarge));
                pieces.add(new Piece(mossy[index], position, rotation, 0.5F, isLarge));
            }
            case WARM -> {
                var ruins = isLarge ? BIG_WARM_RUINS : WARM_RUINS;
                var template = ruins[random.nextInt(ruins.length)];
                pieces.add(new Piece(template, position, rotation, baseIntegrity, isLarge));
            }
        }
    }

    /** Vanilla {@code Mth.nextInt}: an inclusive range draw. */
    private static int nextInt(RandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }
}
