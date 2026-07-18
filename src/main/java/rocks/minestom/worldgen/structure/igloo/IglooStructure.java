package rocks.minestom.worldgen.structure.igloo;

import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;

/**
 * The igloo structure ({@code minecraft:igloo}). Unlike the generic
 * {@link rocks.minestom.worldgen.structure.SimpleStructure} template path,
 * which would place only the visible {@code igloo/top} template, vanilla
 * igloos roll a 50% chance of a basement: a {@code igloo/bottom} laboratory
 * plus a vertical {@code igloo/middle} ladder shaft, each piece resolving its
 * own surface height, so placement is handled by {@link IglooPlacer} instead.
 */
public record IglooStructure(StructureBiomes biomes) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Handled by IglooPlacer; nothing to do through the template path.
    }
}
