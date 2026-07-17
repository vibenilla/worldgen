package rocks.minestom.worldgen.structure.pool;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.JigsawBlockInfo;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.List;

/**
 * An element that can be selected from a {@link TemplatePool} during jigsaw
 * structure assembly, mirroring vanilla's {@code StructurePoolElement}.
 */
public sealed interface PoolElement permits EmptyPoolElement, FeaturePoolElement, SinglePoolElement, ListPoolElement {
    Projection projection();

    /**
     * The element's jigsaw blocks at the given placement, shuffled with the
     * assembly random then ordered by selection priority (vanilla
     * {@code getShuffledJigsawBlocks}). Draw counts must match vanilla
     * exactly: single elements Fisher-Yates shuffle their jigsaw list.
     */
    List<JigsawBlockInfo> getShuffledJigsawBlocks(StructureLoader loader, BlockVec position, Rotation rotation,
            RandomSource random);

    BoundingBox getBoundingBox(StructureLoader loader, BlockVec position, Rotation rotation);

    /** Vanilla {@code getGroundLevelDelta}, constant 1 for all element types. */
    default int groundLevelDelta() {
        return 1;
    }
}
