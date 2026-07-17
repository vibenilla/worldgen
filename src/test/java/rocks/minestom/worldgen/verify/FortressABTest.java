package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.fortress.NetherFortressPieces;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A/B compares the nether fortress piece port against real vanilla 26.2 code
 * in process: both sides build the piece list for the same start chunk from
 * identical {@code setLargeFeatureSeed} random sequences, and every piece's
 * type, generation depth, orientation and bounding box must match in order.
 *
 * <p>This exercises the full procedural layout algorithm (weighted piece
 * selection, the 112-block distance cutoff, the pending-children queue order,
 * and the final {@code moveInsideHeights} recentering) but not block-level
 * placement - see {@code FortressBlockConsistencyTest} for a white-box check
 * of the port's own {@code postProcess} determinism, since driving vanilla's
 * {@code postProcess} would require a full {@code WorldGenLevel}.
 */
final class FortressABTest {
    private static final int SEEDS = 6;
    private static final int STARTS_PER_SEED = 10;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void pieceListMatchesVanilla() {
        var totalCompared = 0;
        for (var seedIndex = 0; seedIndex < SEEDS; seedIndex++) {
            var seed = 1000003L * (seedIndex + 1) + 17;
            for (var startIndex = 0; startIndex < STARTS_PER_SEED; startIndex++) {
                var chunkX = startIndex * 37 - 200 + seedIndex;
                var chunkZ = startIndex * -53 + 150 - seedIndex * 3;

                var vanillaPieces = vanillaPieces(seed, chunkX, chunkZ);
                var ourPieces = NetherFortressPieces.generatePieces(ourRandom(seed, chunkX, chunkZ), chunkX, chunkZ);
                var context = "seed=" + seed + " chunk=" + chunkX + "," + chunkZ;

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

        assertTrue(totalCompared >= 50, "expected at least 50 fortress pieces compared, got " + totalCompared);
    }

    /**
     * Runs vanilla's real {@code NetherFortressStructure.generatePieces}
     * algorithm directly (its public building blocks: {@code StartPiece},
     * {@code StructurePiecesBuilder}, and the piece queue loop) rather than
     * through the full structure pipeline, since piece geometry never reads
     * the level - only {@code context.random()} and {@code context.chunkPos()}.
     */
    private static List<net.minecraft.world.level.levelgen.structure.StructurePiece> vanillaPieces(long seed, int chunkX, int chunkZ) {
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);

        var start = new net.minecraft.world.level.levelgen.structure.structures.NetherFortressPieces.StartPiece(
                random, (chunkX << 4) + 2, (chunkZ << 4) + 2);
        var builder = new net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder();
        builder.addPiece(start);
        start.addChildren(start, builder, random);

        var pendingChildren = start.pendingChildren;
        while (!pendingChildren.isEmpty()) {
            var index = random.nextInt(pendingChildren.size());
            var piece = pendingChildren.remove(index);
            piece.addChildren(start, builder, random);
        }

        builder.moveInsideHeights(random, 48, 70);
        return builder.build().pieces();
    }

    private static WorldgenRandom ourRandom(long seed, int chunkX, int chunkZ) {
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        return random;
    }
}
