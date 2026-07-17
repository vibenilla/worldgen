package rocks.minestom.worldgen.structure.fortress;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The procedural nether fortress structure ({@code minecraft:fortress}).
 * Like the mineshaft, it is generated piece by piece rather than from
 * templates; placement is handled by {@link FortressPlacer} instead of the
 * generic template path.
 */
public record FortressStructure(StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by FortressPlacer; nothing to do through the template path.
    }
}
