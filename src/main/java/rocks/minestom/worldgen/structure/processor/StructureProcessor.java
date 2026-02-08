package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.instance.block.Block;

/**
 * Transforms blocks during structure template placement.
 *
 * <p>Processors modify blocks as they're placed, enabling effects like:
 * <ul>
 *   <li>Randomized block replacement (e.g., cracked stone bricks)
 *   <li>Biome-dependent materials (e.g., different wood types)
 *   <li>Decay and weathering effects
 *   <li>Block removal based on conditions
 * </ul>
 *
 * <p>Returning {@code null} from {@link #process} prevents the block from being placed.
 *
 * @see StructureProcessorList for chaining multiple processors
 * @see RuleStructureProcessor for rule-based block replacement
 */
public interface StructureProcessor {

    /**
     * Processes a block before placement.
     *
     * @param block   the original block from the template
     * @param context provides randomness and tag information
     * @return the block to place, or null to skip placement
     */
    Block process(Block block, StructureProcessorContext context);
}
