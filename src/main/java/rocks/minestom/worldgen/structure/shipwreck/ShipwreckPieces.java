package rocks.minestom.worldgen.structure.shipwreck;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.Rotation;

/**
 * Port of vanilla {@code ShipwreckPieces}: the beached and ocean template
 * pools plus the single random pick. The pivot (4, 0, 15) matches vanilla's
 * {@code ShipwreckPieces.PIVOT}.
 */
final class ShipwreckPieces {
    static final BlockVec PIVOT = new BlockVec(4, 0, 15);

    private static final Key[] STRUCTURE_LOCATION_BEACHED = keys(
            "shipwreck/with_mast",
            "shipwreck/sideways_full",
            "shipwreck/sideways_fronthalf",
            "shipwreck/sideways_backhalf",
            "shipwreck/rightsideup_full",
            "shipwreck/rightsideup_fronthalf",
            "shipwreck/rightsideup_backhalf",
            "shipwreck/with_mast_degraded",
            "shipwreck/rightsideup_full_degraded",
            "shipwreck/rightsideup_fronthalf_degraded",
            "shipwreck/rightsideup_backhalf_degraded");
    private static final Key[] STRUCTURE_LOCATION_OCEAN = keys(
            "shipwreck/with_mast",
            "shipwreck/upsidedown_full",
            "shipwreck/upsidedown_fronthalf",
            "shipwreck/upsidedown_backhalf",
            "shipwreck/sideways_full",
            "shipwreck/sideways_fronthalf",
            "shipwreck/sideways_backhalf",
            "shipwreck/rightsideup_full",
            "shipwreck/rightsideup_fronthalf",
            "shipwreck/rightsideup_backhalf",
            "shipwreck/with_mast_degraded",
            "shipwreck/upsidedown_full_degraded",
            "shipwreck/upsidedown_fronthalf_degraded",
            "shipwreck/upsidedown_backhalf_degraded",
            "shipwreck/sideways_full_degraded",
            "shipwreck/sideways_fronthalf_degraded",
            "shipwreck/sideways_backhalf_degraded",
            "shipwreck/rightsideup_full_degraded",
            "shipwreck/rightsideup_fronthalf_degraded",
            "shipwreck/rightsideup_backhalf_degraded");

    private ShipwreckPieces() {
    }

    private static Key[] keys(String... names) {
        var result = new Key[names.length];
        for (var index = 0; index < names.length; index++) {
            result[index] = Key.key("minecraft", names[index]);
        }
        return result;
    }

    record Piece(Key template, BlockVec position, Rotation rotation) {
    }

    static Piece addRandomPiece(BlockVec position, Rotation rotation, RandomSource random, boolean isBeached) {
        var pool = isBeached ? STRUCTURE_LOCATION_BEACHED : STRUCTURE_LOCATION_OCEAN;
        var template = pool[random.nextInt(pool.length)];
        return new Piece(template, position, rotation);
    }
}
