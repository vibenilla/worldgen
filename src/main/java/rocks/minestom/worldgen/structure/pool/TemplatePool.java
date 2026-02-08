package rocks.minestom.worldgen.structure.pool;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.assembly.JigsawAssembler;

import java.util.List;

/**
 * A weighted collection of {@link PoolElement}s used during jigsaw structure assembly.
 *
 * <p>Template pools are referenced by jigsaw blocks to determine what pieces can connect
 * at each connection point. For example, a village street jigsaw might reference a
 * "village/houses" pool containing various house templates with different weights.
 *
 * <p>The {@link #fallback} pool is used when the primary pool fails to provide a valid piece.
 *
 * @see JigsawAssembler for the assembly algorithm
 * @see PoolElement for the elements contained in pools
 */
public record TemplatePool(List<PoolElementEntry> elements, Key fallback) {
    public PoolElementEntry pick(RandomSource random) {
        if (this.elements.isEmpty()) {
            return null;
        }

        var totalWeight = 0;
        for (var entry : this.elements) {
            totalWeight += entry.weight();
        }

        if (totalWeight <= 0) {
            return null;
        }

        var selectedWeight = random.nextInt(totalWeight);
        var runningWeight = 0;
        for (var entry : this.elements) {
            runningWeight += entry.weight();
            if (selectedWeight < runningWeight) {
                return entry;
            }
        }

        return this.elements.getFirst();
    }

    public record PoolElementEntry(PoolElement element, int weight) {
    }
}
