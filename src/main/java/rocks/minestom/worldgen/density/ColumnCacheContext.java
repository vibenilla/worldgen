package rocks.minestom.worldgen.density;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Density-function context for repeated samples down one column. Honors the
 * datapack's {@code flat_cache}/{@code cache_2d} markers the way vanilla's
 * chunk-cached climate sampler does: their subtrees are Y-invariant by
 * contract, so each caches once per column instead of once per sample.
 */
public final class ColumnCacheContext implements DensityFunction.Context {
    private final Map<DensityFunction, Double> columnCache = new IdentityHashMap<>();
    private int blockX;
    private int blockY;
    private int blockZ;

    /**
     * Moves to a new column, clearing the per-column cache.
     */
    public void moveColumn(int blockX, int blockZ) {
        this.blockX = blockX;
        this.blockZ = blockZ;
        this.columnCache.clear();
    }

    public void blockY(int blockY) {
        this.blockY = blockY;
    }

    /**
     * Value of a column-invariant node, computed once per column.
     */
    public double columnValue(DensityFunction marker, DensityFunction argument) {
        var cached = this.columnCache.get(marker);
        if (cached != null) {
            return cached;
        }

        var value = argument.compute(this);
        this.columnCache.put(marker, value);
        return value;
    }

    @Override
    public int blockX() {
        return this.blockX;
    }

    @Override
    public int blockY() {
        return this.blockY;
    }

    @Override
    public int blockZ() {
        return this.blockZ;
    }
}
