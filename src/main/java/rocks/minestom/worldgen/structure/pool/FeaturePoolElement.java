package rocks.minestom.worldgen.structure.pool;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.utils.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.JigsawBlockInfo;
import rocks.minestom.worldgen.structure.template.JointType;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.List;

/**
 * Vanilla {@code FeaturePoolElement}: a placed feature standing in for a
 * template piece. It exposes a single synthetic downward-facing jigsaw named
 * {@code minecraft:bottom} pointing at the empty pool, and a one-block
 * bounding box. Its jigsaw list is not shuffled (no random draws).
 */
public record FeaturePoolElement(Key feature, Projection projection) implements PoolElement {
    private static final Key EMPTY_POOL = Key.key("minecraft:empty");

    @Override
    public List<JigsawBlockInfo> getShuffledJigsawBlocks(StructureLoader loader, BlockVec position, Rotation rotation,
            RandomSource random) {
        return List.of(new JigsawBlockInfo(
                position,
                EMPTY_POOL,
                "minecraft:bottom",
                "minecraft:empty",
                JointType.ROLLABLE,
                Direction.DOWN,
                Direction.SOUTH,
                0,
                0,
                "minecraft:air"));
    }

    @Override
    public BoundingBox getBoundingBox(StructureLoader loader, BlockVec position, Rotation rotation) {
        return new BoundingBox(position);
    }
}
