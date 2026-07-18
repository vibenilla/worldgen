package rocks.minestom.worldgen.structure.ruinedportal;

import net.kyori.adventure.key.Key;

/**
 * Port of vanilla {@code RuinedPortalStructure}'s hardcoded template pools: ten
 * normal portals and three giant portals, both under {@code ruined_portal/}.
 * These lists are shared by every biome variant and are not read from JSON.
 */
final class RuinedPortalPieces {
    static final Key[] STRUCTURE_LOCATION_PORTALS = keys(
            "ruined_portal/portal_1",
            "ruined_portal/portal_2",
            "ruined_portal/portal_3",
            "ruined_portal/portal_4",
            "ruined_portal/portal_5",
            "ruined_portal/portal_6",
            "ruined_portal/portal_7",
            "ruined_portal/portal_8",
            "ruined_portal/portal_9",
            "ruined_portal/portal_10");
    static final Key[] STRUCTURE_LOCATION_GIANT_PORTALS = keys(
            "ruined_portal/giant_portal_1",
            "ruined_portal/giant_portal_2",
            "ruined_portal/giant_portal_3");

    private RuinedPortalPieces() {
    }

    private static Key[] keys(String... names) {
        var result = new Key[names.length];
        for (var index = 0; index < names.length; index++) {
            result[index] = Key.key("minecraft", names[index]);
        }
        return result;
    }
}
