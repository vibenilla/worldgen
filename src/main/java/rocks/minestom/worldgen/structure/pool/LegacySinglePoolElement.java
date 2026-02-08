package rocks.minestom.worldgen.structure.pool;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;

/**
 * A pool element referencing a single NBT template.
 *
 * <p>This is the most common pool element type. It specifies:
 * <ul>
 *   <li>{@code location} - The template path (e.g., "village/houses/small_house_1")
 *   <li>{@code processors} - Block transformations to apply
 *   <li>{@code projection} - "rigid" for fixed Y, or "terrain_matching" to follow terrain
 * </ul>
 *
 * @param location   template key pointing to an NBT file
 * @param processors processors to apply during placement
 * @param projection placement projection mode
 */
public record LegacySinglePoolElement(Key location, StructureProcessorList processors, String projection) implements PoolElement {
}
