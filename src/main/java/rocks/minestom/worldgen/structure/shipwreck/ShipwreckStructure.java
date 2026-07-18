package rocks.minestom.worldgen.structure.shipwreck;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The shipwreck structure ({@code minecraft:shipwreck} and
 * {@code minecraft:shipwreck_beached}, both type {@code minecraft:shipwreck},
 * distinguished by {@code is_beached}). Unlike the generic
 * {@link rocks.minestom.worldgen.structure.SimpleStructure} template path,
 * shipwrecks pick from a beached- or ocean-specific template pool, rotate
 * around a fixed pivot, and compute their height from either the world
 * surface (beached) or the mean ocean floor of their footprint, so placement
 * is handled by {@link ShipwreckPlacer} instead.
 */
public record ShipwreckStructure(StructureBiomes biomes, boolean isBeached) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by ShipwreckPlacer; nothing to do through the template path.
    }
}
