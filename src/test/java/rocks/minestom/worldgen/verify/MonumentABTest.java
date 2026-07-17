package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.monument.OceanMonumentPieces;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A/B compares the ocean monument room-graph port against real vanilla 26.2
 * code in process: both sides build the {@code MonumentBuilding} piece for
 * the same start chunk from identical {@code setLargeFeatureSeed} random
 * sequences (including the {@code Direction.Plane.HORIZONTAL} orientation
 * draw), and the top piece plus every child room's type, orientation and
 * bounding box must match in order.
 *
 * <p>This exercises the full room-graph algorithm (grid connections, the
 * core-room draw, the opening-closing loop with its {@code findSource}
 * scans, and every {@code MonumentRoomFitter}) but not block-level
 * placement - driving vanilla's {@code postProcess} would require a full
 * {@code WorldGenLevel}. See {@code FortressABTest} for the same scope
 * decision on the nether fortress port.
 *
 * <p>Vanilla's {@code childPieces} field is private, so it is read via
 * reflection; every other value compared here is public API.
 */
final class MonumentABTest {
    private static final int SEEDS = 6;
    private static final int STARTS_PER_SEED = 10;

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void roomGraphMatchesVanilla() throws Exception {
        var totalRoomsCompared = 0;
        for (var seedIndex = 0; seedIndex < SEEDS; seedIndex++) {
            var seed = 1000003L * (seedIndex + 1) + 17;
            for (var startIndex = 0; startIndex < STARTS_PER_SEED; startIndex++) {
                var chunkX = startIndex * 41 - 150 + seedIndex;
                var chunkZ = startIndex * -59 + 120 - seedIndex * 3;
                var context = "seed=" + seed + " chunk=" + chunkX + "," + chunkZ;

                var vanilla = vanillaBuilding(seed, chunkX, chunkZ);
                var ours = OceanMonumentPieces.generateBuilding(
                        ourRandom(seed, chunkX, chunkZ), (chunkX << 4) - 29, (chunkZ << 4) - 29,
                        ourDirection(seed, chunkX, chunkZ));

                assertBoxEquals(vanilla.getBoundingBox(), ours.boundingBox(), context + " top piece");
                assertEquals(vanilla.getOrientation().name(), ours.orientation().name(), context + " top orientation");

                var vanillaChildren = vanillaChildPieces(vanilla);
                var ourChildren = ours.childPieces();
                assertEquals(vanillaChildren.size(), ourChildren.size(), context + " child piece count");

                for (var index = 0; index < vanillaChildren.size(); index++) {
                    var vanillaPiece = vanillaChildren.get(index);
                    var ourPiece = ourChildren.get(index);
                    var pieceContext = context + " child#" + index;

                    assertEquals(vanillaPiece.getClass().getSimpleName(), ourPiece.getClass().getSimpleName(), pieceContext + " type");

                    var vanillaOrientation = vanillaPiece.getOrientation();
                    var ourOrientation = ourPiece.orientation();
                    assertEquals(
                            vanillaOrientation == null ? null : vanillaOrientation.name(),
                            ourOrientation == null ? null : ourOrientation.name(),
                            pieceContext + " orientation");

                    assertBoxEquals(vanillaPiece.getBoundingBox(), ourPiece.boundingBox(), pieceContext);
                    totalRoomsCompared++;
                }
            }
        }

        assertTrue(totalRoomsCompared >= 200, "expected at least 200 monument rooms compared, got " + totalRoomsCompared);
    }

    private static void assertBoxEquals(net.minecraft.world.level.levelgen.structure.BoundingBox vanillaBox,
            rocks.minestom.worldgen.structure.template.BoundingBox ourBox, String context) {
        assertEquals(vanillaBox.minX(), ourBox.minX(), context + " minX");
        assertEquals(vanillaBox.minY(), ourBox.minY(), context + " minY");
        assertEquals(vanillaBox.minZ(), ourBox.minZ(), context + " minZ");
        assertEquals(vanillaBox.maxX(), ourBox.maxX(), context + " maxX");
        assertEquals(vanillaBox.maxY(), ourBox.maxY(), context + " maxY");
        assertEquals(vanillaBox.maxZ(), ourBox.maxZ(), context + " maxZ");
    }

    /**
     * Vanilla {@code OceanMonumentStructure.createTopPiece}: draws the
     * orientation from the same random before constructing the building, so
     * both sides must draw it on their own random in the same call order.
     */
    private static net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding vanillaBuilding(
            long seed, int chunkX, int chunkZ) {
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var west = (chunkX << 4) - 29;
        var north = (chunkZ << 4) - 29;
        var direction = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(random);
        return new net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding(
                random, west, north, direction);
    }

    private static WorldgenRandom ourRandom(long seed, int chunkX, int chunkZ) {
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        // Consume the same single orientation draw ourDirection() will make
        // on its own independent random instance, keeping this random's
        // remaining sequence (used by generateBuilding) aligned with
        // vanilla's post-orientation-draw state.
        random.nextInt(4);
        return random;
    }

    private static rocks.minestom.worldgen.feature.Direction ourDirection(long seed, int chunkX, int chunkZ) {
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        return rocks.minestom.worldgen.feature.Direction.HORIZONTAL.get(
                random.nextInt(rocks.minestom.worldgen.feature.Direction.HORIZONTAL.size()));
    }

    @SuppressWarnings("unchecked")
    private static List<net.minecraft.world.level.levelgen.structure.StructurePiece> vanillaChildPieces(
            net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding building) throws Exception {
        Field field = building.getClass().getDeclaredField("childPieces");
        field.setAccessible(true);
        return new ArrayList<>((List<net.minecraft.world.level.levelgen.structure.StructurePiece>) field.get(building));
    }
}
