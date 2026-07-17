package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.structure.stronghold.StrongholdPieces;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A/B compares the stronghold piece port against real vanilla 26.2 code in
 * process: both sides run the same {@code generatePieces} retry loop
 * (reseeding {@code seed + attempt} on every retry until a portal room
 * appears) for the same start chunk and sea level, and every piece's type,
 * generation depth, orientation and bounding box must match in order.
 *
 * <p>This exercises the full procedural layout algorithm - weighted piece
 * selection with {@code maxPlaceCount}/depth gating, the imposed five-way
 * crossing after the source stairwell, the pending-children queue order, the
 * filler-corridor fallback, and the {@code moveBelowSeaLevel} retry loop
 * itself - but not block-level placement, since driving vanilla's
 * {@code postProcess} would require a full {@code WorldGenLevel}. Block
 * placement is instead verified by careful transcription against the
 * vanilla source (see {@code StrongholdPieces} class Javadoc) and by the
 * generator's own determinism.
 */
final class StrongholdABTest {
    private static final int SEEDS = 10;
    private static final int STARTS_PER_SEED = 10;
    private static final int SEA_LEVEL = 63;
    private static final int MIN_Y = -64;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void pieceListMatchesVanilla() {
        var totalCompared = 0;
        var totalStarts = 0;
        var multiAttemptStarts = 0;

        for (var seedIndex = 0; seedIndex < SEEDS; seedIndex++) {
            var seed = 2000003L * (seedIndex + 1) + 29;
            for (var startIndex = 0; startIndex < STARTS_PER_SEED; startIndex++) {
                var chunkX = startIndex * 41 - 150 + seedIndex;
                var chunkZ = startIndex * -59 + 100 - seedIndex * 5;

                var attempts = new int[1];
                var vanillaPieces = vanillaPieces(seed, chunkX, chunkZ, attempts);
                var ourPieces = StrongholdPieces.generatePieces(seed, chunkX, chunkZ, SEA_LEVEL, MIN_Y);
                var context = "seed=" + seed + " chunk=" + chunkX + "," + chunkZ;

                totalStarts++;
                if (attempts[0] > 1) {
                    multiAttemptStarts++;
                }

                assertEquals(vanillaPieces.size(), ourPieces.size(), context + " piece count");

                for (var index = 0; index < vanillaPieces.size(); index++) {
                    var vanilla = vanillaPieces.get(index);
                    var ours = ourPieces.get(index);
                    var pieceContext = context + " piece#" + index;

                    assertEquals(vanilla.getClass().getSimpleName(), ours.getClass().getSimpleName(), pieceContext + " type");
                    assertEquals(vanilla.getGenDepth(), ours.genDepth(), pieceContext + " genDepth");

                    var vanillaOrientation = vanilla.getOrientation();
                    var ourOrientation = ours.orientation();
                    assertEquals(
                            vanillaOrientation == null ? null : vanillaOrientation.name(),
                            ourOrientation == null ? null : ourOrientation.name(),
                            pieceContext + " orientation");

                    var vanillaBox = vanilla.getBoundingBox();
                    var ourBox = ours.boundingBox();
                    assertEquals(vanillaBox.minX(), ourBox.minX(), pieceContext + " minX");
                    assertEquals(vanillaBox.minY(), ourBox.minY(), pieceContext + " minY");
                    assertEquals(vanillaBox.minZ(), ourBox.minZ(), pieceContext + " minZ");
                    assertEquals(vanillaBox.maxX(), ourBox.maxX(), pieceContext + " maxX");
                    assertEquals(vanillaBox.maxY(), ourBox.maxY(), pieceContext + " maxY");
                    assertEquals(vanillaBox.maxZ(), ourBox.maxZ(), pieceContext + " maxZ");
                    totalCompared++;
                }
            }
        }

        assertTrue(totalStarts >= 30, "expected at least 30 stronghold starts compared, got " + totalStarts);
        assertTrue(totalCompared >= 200, "expected at least 200 stronghold pieces compared, got " + totalCompared);
        assertTrue(multiAttemptStarts > 0, "expected at least one start to need multiple generatePieces attempts");
    }

    /**
     * Runs vanilla's real {@code StrongholdStructure.generatePieces}
     * algorithm directly (its public building blocks: {@code
     * StrongholdPieces.StartPiece}, {@code StructurePiecesBuilder}, and the
     * piece queue and retry loops) rather than through the full structure
     * pipeline, since piece geometry never reads the level - only {@code
     * random}, {@code chunkPos}, sea level and min Y. Records the number of
     * {@code generatePieces} attempts taken in {@code attemptsOut[0]}.
     */
    private static List<net.minecraft.world.level.levelgen.structure.StructurePiece> vanillaPieces(
            long seed, int chunkX, int chunkZ, int[] attemptsOut) {
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
        var builder = new net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder();

        var tries = 0;
        net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces.StartPiece startRoom;
        do {
            builder.clear();
            random.setLargeFeatureSeed(seed + (long) (tries++), chunkX, chunkZ);
            net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces.resetPieces();
            startRoom = new net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces.StartPiece(
                    random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
            builder.addPiece(startRoom);
            startRoom.addChildren(startRoom, builder, random);

            var pendingChildren = startRoom.pendingChildren;
            while (!pendingChildren.isEmpty()) {
                var pos = random.nextInt(pendingChildren.size());
                var piece = pendingChildren.remove(pos);
                piece.addChildren(startRoom, builder, random);
            }

            builder.moveBelowSeaLevel(SEA_LEVEL, MIN_Y, random, 10);
        } while (builder.isEmpty() || startRoom.portalRoomPiece == null);

        attemptsOut[0] = tries;
        return builder.build().pieces();
    }
}
