package rocks.minestom.worldgen.structure.pool;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.StructureRng;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.*;

import java.util.Comparator;
import java.util.List;

/**
 * Vanilla {@code SinglePoolElement} / {@code LegacySinglePoolElement}: a pool
 * element referencing a single NBT template.
 *
 * <p>The {@code legacy} flag only changes placement: legacy elements skip air
 * blocks (village-era templates), single elements place air (trial chambers
 * carve their rooms).
 *
 * @param location               template key pointing to an NBT file
 * @param processors             processors to apply during placement
 * @param projection             placement projection mode
 * @param overrideLiquidSettings per-element liquid settings override, or null
 * @param legacy                 legacy air handling
 */
public record SinglePoolElement(
        Key location,
        StructureProcessorList processors,
        Projection projection,
        @Nullable LiquidSettings overrideLiquidSettings,
        boolean legacy
) implements PoolElement {
    private static final Comparator<JigsawBlockInfo> HIGHEST_SELECTION_PRIORITY_FIRST =
            Comparator.comparingInt(JigsawBlockInfo::selectionPriority).reversed();

    @Override
    public List<JigsawBlockInfo> getShuffledJigsawBlocks(StructureLoader loader, BlockVec position, Rotation rotation,
            RandomSource random) {
        var template = loader.getTemplate(this.location);
        var jigsaws = template.getJigsaws(position, rotation);
        StructureRng.shuffle(jigsaws, random);
        jigsaws.sort(HIGHEST_SELECTION_PRIORITY_FIRST);
        return jigsaws;
    }

    @Override
    public BoundingBox getBoundingBox(StructureLoader loader, BlockVec position, Rotation rotation) {
        return loader.getTemplate(this.location).getBoundingBox(position, rotation);
    }
}
