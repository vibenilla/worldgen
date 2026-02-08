package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.instance.block.Block;

import java.util.List;

public record StructureProcessorList(List<StructureProcessor> processors) {
    public static final StructureProcessorList EMPTY = new StructureProcessorList(List.of());

    public Block apply(Block block, StructureProcessorContext context) {
        var result = block;
        for (var processor : this.processors) {
            result = processor.process(result, context);
        }
        return result;
    }
}
