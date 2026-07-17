package rocks.minestom.worldgen.structure.processor;

import java.util.List;

/**
 * An ordered list of datapack processors attached to a pool element.
 */
public record StructureProcessorList(List<StructureProcessor> processors) {
    public static final StructureProcessorList EMPTY = new StructureProcessorList(List.of());
}
