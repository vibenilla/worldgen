package rocks.minestom.worldgen.structure;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.assembly.JigsawAssembler;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.structure.pool.TemplatePool;

/**
 * A structure assembled from multiple pieces connected via jigsaw blocks.
 *
 * <p>Jigsaw structures are the most complex structure type, used for villages,
 * bastions, ancient cities, and other multi-piece structures. They work by:
 * <ol>
 *   <li>Starting with an initial piece from {@link #startPool}
 *   <li>Finding jigsaw blocks in placed pieces
 *   <li>Connecting new pieces from referenced pools at those jigsaw positions
 *   <li>Repeating until {@link #size} depth is reached or no more connections fit
 * </ol>
 *
 * <p>Key parameters:
 * <ul>
 *   <li>{@link #size} - Maximum depth of jigsaw expansion (not physical size)
 *   <li>{@link #startHeight} - Y offset for structure placement
 *   <li>{@link #projectToHeightmap} - Whether to place relative to terrain surface
 *   <li>{@link #maxDistanceFromCenter} - Bounds limit for piece placement
 * </ul>
 *
 * @see JigsawAssembler for the assembly algorithm
 * @see TemplatePool for the pools that provide pieces
 */
public record JigsawStructure(
        StructureBiomes biomes,
        Key startPool,
        int size,
        int startHeight,
        boolean projectToHeightmap,
        int maxDistanceFromCenter
) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        var assembler = new JigsawAssembler(context, this.size, this.maxDistanceFromCenter);
        assembler.assemble(this.startPool);
    }
}
