package rocks.minestom.worldgen.structure.mansion;

import net.minestom.server.coordinate.BlockVec;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural invariants of the ported grid solver and piece placer, checked
 * across many seeds and rotations. This is a self-consistency and regression
 * test, not a vanilla piece-list diff: {@code rocks.minestom.worldgen.verify.MansionLocateCompare}
 * (a manual tool, not run by {@code ./gradlew test}) is the actual real-vanilla
 * cross-check available in this environment, and only verifies structure-set
 * placement (spacing/separation/salt/biome gate), not the internal layout.
 */
final class MansionPiecePlacerTest {
    private static final Set<String> KNOWN_TEMPLATES = Set.of(
            "1x1_a1", "1x1_a2", "1x1_a3", "1x1_a4", "1x1_a5",
            "1x1_as1", "1x1_as2", "1x1_as3", "1x1_as4",
            "1x1_b1", "1x1_b2", "1x1_b3", "1x1_b4", "1x1_b5",
            "1x2_a1", "1x2_a2", "1x2_a3", "1x2_a4", "1x2_a5", "1x2_a6", "1x2_a7", "1x2_a8", "1x2_a9",
            "1x2_b1", "1x2_b2", "1x2_b3", "1x2_b4", "1x2_b5",
            "1x2_c1", "1x2_c2", "1x2_c3", "1x2_c4", "1x2_c_stairs",
            "1x2_d1", "1x2_d2", "1x2_d3", "1x2_d4", "1x2_d5", "1x2_d_stairs",
            "1x2_s1", "1x2_s2", "1x2_se1",
            "2x2_a1", "2x2_a2", "2x2_a3", "2x2_a4",
            "2x2_b1", "2x2_b2", "2x2_b3", "2x2_b4", "2x2_b5",
            "2x2_s1",
            "carpet_south_1", "carpet_south_2", "carpet_west_1", "carpet_west_2",
            "carpet_north", "carpet_east",
            "corridor_floor", "entrance",
            "indoors_door_1", "indoors_door_2", "indoors_wall_1", "indoors_wall_2",
            "roof", "roof_front", "roof_corner", "roof_inner_corner",
            "small_wall", "small_wall_corner",
            "wall_corner", "wall_flat", "wall_window"
    );

    @Test
    void firstPieceIsAlwaysTheEntrance() {
        for (var seed = 0L; seed < 30; seed++) {
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(seed, 0, 0);
            var rotation = Rotation.getRandom(random);
            var pieces = MansionPiecePlacer.generateMansion(new BlockVec(0, 90, 0), rotation, random);

            assertFalse(pieces.isEmpty(), "seed " + seed + " produced no pieces");
            assertEquals("entrance", pieces.getFirst().templateName(), "seed " + seed);
        }
    }

    @Test
    void generationIsDeterministic() {
        for (var seed = 0L; seed < 30; seed++) {
            var randomA = new WorldgenRandom(new LegacyRandomSource(0L));
            randomA.setLargeFeatureSeed(seed, 3, -5);
            var rotationA = Rotation.getRandom(randomA);
            var piecesA = MansionPiecePlacer.generateMansion(new BlockVec(48, 90, -80), rotationA, randomA);

            var randomB = new WorldgenRandom(new LegacyRandomSource(0L));
            randomB.setLargeFeatureSeed(seed, 3, -5);
            var rotationB = Rotation.getRandom(randomB);
            var piecesB = MansionPiecePlacer.generateMansion(new BlockVec(48, 90, -80), rotationB, randomB);

            assertEquals(piecesA.size(), piecesB.size(), "seed " + seed);
            for (var index = 0; index < piecesA.size(); index++) {
                var a = piecesA.get(index);
                var b = piecesB.get(index);
                assertEquals(a.templateName(), b.templateName(), "seed " + seed + " piece " + index);
                assertEquals(a.position(), b.position(), "seed " + seed + " piece " + index);
                assertEquals(a.rotation(), b.rotation(), "seed " + seed + " piece " + index);
                assertEquals(a.mirror(), b.mirror(), "seed " + seed + " piece " + index);
            }
        }
    }

    @Test
    void allTemplateNamesAreKnown() {
        for (var seed = 0L; seed < 30; seed++) {
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(seed, -7, 12);
            var rotation = Rotation.getRandom(random);
            var pieces = MansionPiecePlacer.generateMansion(new BlockVec(0, 90, 0), rotation, random);

            for (var piece : pieces) {
                assertTrue(KNOWN_TEMPLATES.contains(piece.templateName()),
                        "seed " + seed + " produced unknown template " + piece.templateName());
            }
        }
    }

    @Test
    void pieceCountIsInVanillaRange() {
        for (var seed = 0L; seed < 30; seed++) {
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(seed, 20, 20);
            var rotation = Rotation.getRandom(random);
            var pieces = MansionPiecePlacer.generateMansion(new BlockVec(0, 90, 0), rotation, random);

            // A wide sanity bound - the grid solver can range from a compact
            // to a sprawling 11x11 layout across three floors, the roof and
            // both sets of outer walls (individual room/carpet/wall/roof
            // pieces easily number in the hundreds).
            assertTrue(pieces.size() >= 100 && pieces.size() <= 1000,
                    "seed " + seed + " produced an implausible piece count: " + pieces.size());
        }
    }

    @Test
    void differentSeedsProduceDifferentLayouts() {
        List<List<WoodlandMansionPiece>> layouts = new java.util.ArrayList<>();
        for (var seed = 0L; seed < 10; seed++) {
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(seed, 0, 0);
            var rotation = Rotation.getRandom(random);
            layouts.add(MansionPiecePlacer.generateMansion(new BlockVec(0, 90, 0), rotation, random));
        }

        var distinctSizes = new HashSet<Integer>();
        for (var layout : layouts) {
            distinctSizes.add(layout.size());
        }
        assertTrue(distinctSizes.size() > 1, "all 10 seeds produced identically sized layouts");
    }
}
