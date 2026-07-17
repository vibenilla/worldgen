package rocks.minestom.worldgen.structure.pool;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.StructureRng;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla {@code StructureTemplatePool}: elements expanded by weight into a
 * flat template list; picks and shuffles operate on that expanded list.
 */
public final class TemplatePool {
    private final List<PoolElement> templates;
    private final Key fallback;
    private int maxSize = Integer.MIN_VALUE;

    public TemplatePool(List<PoolElementEntry> elements, Key fallback) {
        this.templates = new ArrayList<>();
        for (var entry : elements) {
            for (var index = 0; index < entry.weight(); index++) {
                this.templates.add(entry.element());
            }
        }
        this.fallback = fallback;
    }

    public Key fallback() {
        return this.fallback;
    }

    public int size() {
        return this.templates.size();
    }

    /** Vanilla {@code getRandomTemplate}: one nextInt draw on non-empty pools. */
    public PoolElement getRandomTemplate(RandomSource random) {
        return this.templates.isEmpty()
                ? EmptyPoolElement.INSTANCE
                : this.templates.get(random.nextInt(this.templates.size()));
    }

    /** Vanilla {@code getShuffledTemplates}: Fisher-Yates copy of the expanded list. */
    public List<PoolElement> getShuffledTemplates(RandomSource random) {
        var copy = new ArrayList<>(this.templates);
        StructureRng.shuffle(copy, random);
        return copy;
    }

    /**
     * Vanilla {@code getMaxSize}: tallest bounding box (Y span) among the
     * expanded templates at no rotation, used by the expansion hack.
     */
    public int getMaxSize(StructureLoader loader) {
        if (this.maxSize == Integer.MIN_VALUE) {
            var max = 0;
            for (var template : this.templates) {
                if (template == EmptyPoolElement.INSTANCE) {
                    continue;
                }
                var bounds = template.getBoundingBox(loader, BlockVec.ZERO, Rotation.NONE);
                max = Math.max(max, bounds.getYSpan());
            }
            this.maxSize = max;
        }

        return this.maxSize;
    }

    public record PoolElementEntry(PoolElement element, int weight) {
    }
}
