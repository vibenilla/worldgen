package rocks.minestom.worldgen.structure.pool;

import rocks.minestom.worldgen.structure.assembly.JigsawAssembler;

/**
 * An element that can be selected from a {@link TemplatePool} during jigsaw assembly.
 *
 * <p>Pool elements are the building blocks of jigsaw structures. When assembling a structure,
 * the {@link JigsawAssembler} picks elements from pools and places them based on jigsaw
 * block connections.
 *
 * <p>Element types:
 * <ul>
 *   <li>{@link LegacySinglePoolElement} - A single NBT template with optional processors
 *   <li>{@link ListPoolElement} - Multiple elements placed together at the same location
 *   <li>{@link FeaturePoolElement} - A worldgen feature (not a template)
 *   <li>{@link EmptyPoolElement} - A terminator that stops jigsaw expansion
 * </ul>
 *
 * @see TemplatePool for weighted selection of elements
 */
public sealed interface PoolElement permits EmptyPoolElement, FeaturePoolElement, LegacySinglePoolElement, ListPoolElement {
}
