package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

/**
 * Everything a vanilla {@code StructureProcessor} can observe while a piece is
 * being placed.
 *
 * @param level          world reads at placement time (terrain + earlier
 *                       structure writes), like vanilla's {@code LevelReader}
 * @param blockTags      block tag lookups for tag-based rule tests
 * @param worldSeed      the world seed (capped processors fork from it)
 * @param piecePosition  the piece origin ({@code targetPosition} in vanilla)
 * @param referencePos   the structure reference position pos-rule tests
 *                       measure distances from
 * @param heightSampler  first-free-height lookup for the gravity processor,
 *                       or null when unavailable
 */
public record StructureProcessorContext(
        Block.Getter level,
        BlockTagManager blockTags,
        long worldSeed,
        BlockVec piecePosition,
        BlockVec referencePos,
        @Nullable HeightSampler heightSampler
) {
    /** First free Y (one above the surface) at a world column. */
    public interface HeightSampler {
        int firstFreeHeight(int x, int z);
    }
}
