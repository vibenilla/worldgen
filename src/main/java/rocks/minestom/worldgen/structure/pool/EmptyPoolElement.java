package rocks.minestom.worldgen.structure.pool;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.JigsawBlockInfo;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.List;

/**
 * Vanilla {@code EmptyPoolElement}: a terminator that stops jigsaw expansion.
 * Identity-compared against {@link #INSTANCE} like vanilla.
 */
public final class EmptyPoolElement implements PoolElement {
    public static final EmptyPoolElement INSTANCE = new EmptyPoolElement();

    private EmptyPoolElement() {
    }

    @Override
    public Projection projection() {
        return Projection.TERRAIN_MATCHING;
    }

    @Override
    public List<JigsawBlockInfo> getShuffledJigsawBlocks(StructureLoader loader, BlockVec position, Rotation rotation,
            RandomSource random) {
        return List.of();
    }

    @Override
    public BoundingBox getBoundingBox(StructureLoader loader, BlockVec position, Rotation rotation) {
        throw new IllegalStateException("Invalid call to EmptyPoolElement.getBoundingBox, filter me!");
    }
}
