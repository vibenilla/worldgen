package rocks.minestom.worldgen.density;

import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.density.DensityFunctions.Interpolated;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class ChunkContext implements DensityFunction.Context {
    private final int cellWidth;
    private final int cellHeight;
    private final Map<Interpolated, LongObjectCache<double[]>> interpolationCache;
    private final Map<DensityFunctions.CacheOnce, CacheOnceState> cacheOnce;
    private final Map<DensityFunctions.Cache2D, Cache2DState> cache2D;
    private final Map<DensityFunctions.FlatCache, Cache2DState> flatCache;
    private final Map<DensityFunctions.CacheAllInCell, LongObjectCache<Double>> cacheAllInCell;

    private int blockX;
    private int blockY;
    private int blockZ;

    public ChunkContext(int cellWidth, int cellHeight) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.interpolationCache = new IdentityHashMap<>();
        this.cacheOnce = new IdentityHashMap<>();
        this.cache2D = new IdentityHashMap<>();
        this.flatCache = new IdentityHashMap<>();
        this.cacheAllInCell = new IdentityHashMap<>();
    }

    public int cellWidth() {
        return this.cellWidth;
    }

    public int cellHeight() {
        return this.cellHeight;
    }

    public void setBlock(int blockX, int blockY, int blockZ) {
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
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

    public double interpolatedValue(Interpolated interpolated) {
        var x = this.blockX;
        var y = this.blockY;
        var z = this.blockZ;

        var cellX = Math.floorDiv(x, this.cellWidth);
        var cellY = Math.floorDiv(y, this.cellHeight);
        var cellZ = Math.floorDiv(z, this.cellWidth);

        var x0 = cellX * this.cellWidth;
        var y0 = cellY * this.cellHeight;
        var z0 = cellZ * this.cellWidth;
        var x1 = x0 + this.cellWidth;
        var y1 = y0 + this.cellHeight;
        var z1 = z0 + this.cellWidth;

        var deltaX = (double) (x - x0) / (double) this.cellWidth;
        var deltaY = (double) (y - y0) / (double) this.cellHeight;
        var deltaZ = (double) (z - z0) / (double) this.cellWidth;

        var perFunction = this.interpolationCache.computeIfAbsent(interpolated, key -> new LongObjectCache<>());
        var cellKey = pack(cellX, cellY, cellZ);
        var corners = perFunction.getOrCompute(cellKey, () -> this.sampleCorners(interpolated, x0, y0, z0, x1, y1, z1));

        return VMath.lerp3(deltaX, deltaY, deltaZ, corners[0], corners[1], corners[2], corners[3], corners[4], corners[5], corners[6], corners[7]);
    }

    public double cacheOnceValue(DensityFunctions.CacheOnce function) {
        var state = this.cacheOnce.computeIfAbsent(function, key -> new CacheOnceState());
        if (state.hasValue && state.blockX == this.blockX && state.blockY == this.blockY && state.blockZ == this.blockZ) {
            return state.value;
        }

        var value = function.argument().compute(this);
        state.blockX = this.blockX;
        state.blockY = this.blockY;
        state.blockZ = this.blockZ;
        state.value = value;
        state.hasValue = true;
        return value;
    }

    public double cache2DValue(DensityFunctions.Cache2D function) {
        var state = this.cache2D.computeIfAbsent(function, key -> new Cache2DState());
        if (state.hasValue && state.blockX == this.blockX && state.blockZ == this.blockZ) {
            return state.value;
        }

        var value = function.argument().compute(this);
        state.blockX = this.blockX;
        state.blockZ = this.blockZ;
        state.value = value;
        state.hasValue = true;
        return value;
    }

    public double flatCacheValue(DensityFunctions.FlatCache function) {
        var state = this.flatCache.computeIfAbsent(function, key -> new Cache2DState());
        if (state.hasValue && state.blockX == this.blockX && state.blockZ == this.blockZ) {
            return state.value;
        }

        var value = function.argument().compute(this);
        state.blockX = this.blockX;
        state.blockZ = this.blockZ;
        state.value = value;
        state.hasValue = true;
        return value;
    }

    public double cacheAllInCellValue(DensityFunctions.CacheAllInCell function) {
        var perFunction = this.cacheAllInCell.computeIfAbsent(function, key -> new LongObjectCache<>());
        var cellX = Math.floorDiv(this.blockX, this.cellWidth);
        var cellY = Math.floorDiv(this.blockY, this.cellHeight);
        var cellZ = Math.floorDiv(this.blockZ, this.cellWidth);
        var cellKey = pack(cellX, cellY, cellZ);
        return perFunction.getOrCompute(cellKey, () -> function.argument().compute(this));
    }

    private double[] sampleCorners(Interpolated interpolated, int x0, int y0, int z0, int x1, int y1, int z1) {
        var values = new double[8];
        values[0] = this.sample(interpolated.argument(), x0, y0, z0);
        values[1] = this.sample(interpolated.argument(), x1, y0, z0);
        values[2] = this.sample(interpolated.argument(), x0, y1, z0);
        values[3] = this.sample(interpolated.argument(), x1, y1, z0);
        values[4] = this.sample(interpolated.argument(), x0, y0, z1);
        values[5] = this.sample(interpolated.argument(), x1, y0, z1);
        values[6] = this.sample(interpolated.argument(), x0, y1, z1);
        values[7] = this.sample(interpolated.argument(), x1, y1, z1);
        return values;
    }

    private double sample(DensityFunction function, int x, int y, int z) {
        var previousX = this.blockX;
        var previousY = this.blockY;
        var previousZ = this.blockZ;
        this.setBlock(x, y, z);
        var value = function.compute(this);
        this.setBlock(previousX, previousY, previousZ);
        return value;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x3FFFFF) << 20) | (long) (z & 0xFFFFF);
    }

    private static final class CacheOnceState {
        private boolean hasValue;
        private int blockX;
        private int blockY;
        private int blockZ;
        private double value;
    }

    private static final class Cache2DState {
        private boolean hasValue;
        private int blockX;
        private int blockZ;
        private double value;
    }

    private static final class LongObjectCache<T> {
        private static final float LOAD_FACTOR = 0.7F;

        private long[] keys;
        private Object[] values;
        private boolean[] occupied;
        private int size;
        private int threshold;

        private LongObjectCache() {
            this.keys = new long[16];
            this.values = new Object[16];
            this.occupied = new boolean[16];
            this.size = 0;
            this.threshold = (int) ((float) this.keys.length * LOAD_FACTOR);
        }

        @SuppressWarnings("unchecked")
        private T get(long key) {
            var index = this.findIndex(key);
            if (index < 0) {
                return null;
            }
            return (T) this.values[index];
        }

        private void put(long key, T value) {
            if (this.size >= this.threshold) {
                this.rehash(this.keys.length * 2);
            }

            var mask = this.keys.length - 1;
            var index = (int) mix64(key) & mask;
            while (this.occupied[index]) {
                if (this.keys[index] == key) {
                    this.values[index] = value;
                    return;
                }
                index = (index + 1) & mask;
            }

            this.occupied[index] = true;
            this.keys[index] = key;
            this.values[index] = value;
            this.size++;
        }

        private int findIndex(long key) {
            var mask = this.keys.length - 1;
            var index = (int) mix64(key) & mask;
            while (this.occupied[index]) {
                if (this.keys[index] == key) {
                    return index;
                }
                index = (index + 1) & mask;
            }
            return -1;
        }

        private void rehash(int newCapacity) {
            var oldKeys = this.keys;
            var oldValues = this.values;
            var oldOccupied = this.occupied;

            this.keys = new long[newCapacity];
            this.values = new Object[newCapacity];
            this.occupied = new boolean[newCapacity];
            this.size = 0;
            this.threshold = (int) ((float) newCapacity * LOAD_FACTOR);

            for (var index = 0; index < oldKeys.length; index++) {
                if (!oldOccupied[index]) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                var value = (T) oldValues[index];
                this.put(oldKeys[index], value);
            }
        }

        private static long mix64(long value) {
            value = (value ^ (value >>> 33)) * 0xff51afd7ed558ccdL;
            value = (value ^ (value >>> 33)) * 0xc4ceb9fe1a85ec53L;
            return value ^ (value >>> 33);
        }

        private T getOrCompute(long key, Supplier<T> supplier) {
            var cached = this.get(key);
            if (cached != null) {
                return cached;
            }
            var value = supplier.get();
            this.put(key, value);
            return value;
        }
    }
}
