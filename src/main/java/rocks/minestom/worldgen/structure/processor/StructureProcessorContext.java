package rocks.minestom.worldgen.structure.processor;

import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

public record StructureProcessorContext(RandomSource random, BlockTagManager blockTags) {
}
