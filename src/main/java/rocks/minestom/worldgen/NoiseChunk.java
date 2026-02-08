package rocks.minestom.worldgen;

import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.density.DensityFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A context object for noise-based chunk generation that pre-computes density
 * values
 * at cell corners and uses tri-linear interpolation between them. This is the
 * core
 * optimization that makes chunk generation fast - instead of computing density
 * at
 * every block, we only compute at cell corners and interpolate the rest.
 */
public final class NoiseChunk implements DensityFunction.Context {
    private final int cellWidth;
    private final int cellHeight;
    private final int cellCountXZ;
    private final int cellCountY;
    private final int cellNoiseMinY;
    private final int firstCellX;
    private final int firstCellZ;
    private final DensityFunction finalDensity;
    private final List<NoiseInterpolator> interpolators = new ArrayList<>();
    private final Map<DensityFunction, DensityFunction> wrapped = new HashMap<>();

    private int cellStartBlockX;
    private int cellStartBlockY;
    private int cellStartBlockZ;
    private int inCellX;
    private int inCellY;
    private int inCellZ;
    private boolean interpolating;
    private long interpolationCounter;

    public NoiseChunk(
            int chunkStartX,
            int chunkStartZ,
            int cellWidth,
            int cellHeight,
            int minY,
            int height,
            DensityFunction finalDensity) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.cellCountXZ = 16 / cellWidth;
        this.cellCountY = height / cellHeight;
        this.cellNoiseMinY = Math.floorDiv(minY, cellHeight);
        this.firstCellX = Math.floorDiv(chunkStartX, cellWidth);
        this.firstCellZ = Math.floorDiv(chunkStartZ, cellWidth);
        this.finalDensity = this.wrap(finalDensity);
    }

    @Override
    public int blockX() {
        return this.cellStartBlockX + this.inCellX;
    }

    @Override
    public int blockY() {
        return this.cellStartBlockY + this.inCellY;
    }

    @Override
    public int blockZ() {
        return this.cellStartBlockZ + this.inCellZ;
    }

    public int cellWidth() {
        return this.cellWidth;
    }

    public int cellHeight() {
        return this.cellHeight;
    }

    public int cellCountXZ() {
        return this.cellCountXZ;
    }

    public int cellCountY() {
        return this.cellCountY;
    }

    public int minCellY() {
        return this.cellNoiseMinY;
    }

    /**
     * Wraps density functions to use our interpolation and caching system.
     * The Interpolated marker is the key - it tells us to sample corners and
     * interpolate.
     * Note: Cannot use computeIfAbsent here because wrapNew recursively calls wrap,
     * which would modify the map during computation and cause
     * ConcurrentModificationException.
     */
    private DensityFunction wrap(DensityFunction function) {
        var existing = this.wrapped.get(function);
        if (existing != null) {
            return existing;
        }
        var result = this.wrapNew(function);
        this.wrapped.put(function, result);
        return result;
    }

    private DensityFunction wrapNew(DensityFunction function) {
        if (function instanceof DensityFunctions.Interpolated(DensityFunction argument4)) {
            return new NoiseInterpolator(argument4);
        }

        if (function instanceof DensityFunctions.CacheOnce(DensityFunction argument3)) {
            return new CacheOnce(this.wrap(argument3));
        }

        if (function instanceof DensityFunctions.Cache2D(DensityFunction argument2)) {
            return new Cache2D(this.wrap(argument2));
        }

        if (function instanceof DensityFunctions.FlatCache(DensityFunction argument1)) {
            return new FlatCache(this.wrap(argument1));
        }

        if (function instanceof DensityFunctions.CacheAllInCell(DensityFunction argument)) {
            return new CacheAllInCell(this.wrap(argument));
        }

        // Recursively wrap child functions
        return this.wrapChildren(function);
    }

    private DensityFunction wrapChildren(DensityFunction function) {
        if (function instanceof DensityFunctions.Add(DensityFunction argument7, DensityFunction argument8)) {
            return new DensityFunctions.Add(this.wrap(argument7), this.wrap(argument8));
        }
        if (function instanceof DensityFunctions.Mul(DensityFunction argument5, DensityFunction argument6)) {
            return new DensityFunctions.Mul(this.wrap(argument5), this.wrap(argument6));
        }
        if (function instanceof DensityFunctions.Min(DensityFunction argument3, DensityFunction argument4)) {
            return new DensityFunctions.Min(this.wrap(argument3), this.wrap(argument4));
        }
        if (function instanceof DensityFunctions.Max(DensityFunction argument1, DensityFunction argument2)) {
            return new DensityFunctions.Max(this.wrap(argument1), this.wrap(argument2));
        }
        if (function instanceof DensityFunctions.Clamp(DensityFunction input3, double min, double max)) {
            return new DensityFunctions.Clamp(this.wrap(input3), min, max);
        }
        if (function instanceof DensityFunctions.Mapped(DensityFunctions.Mapped.Type type, DensityFunction input2)) {
            return new DensityFunctions.Mapped(type, this.wrap(input2));
        }
        if (function instanceof DensityFunctions.RangeChoice(
                DensityFunction input1, double minInclusive, double maxExclusive, DensityFunction whenInRange,
                DensityFunction whenOutOfRange
        )) {
            return new DensityFunctions.RangeChoice(
                    this.wrap(input1),
                    minInclusive,
                    maxExclusive,
                    this.wrap(whenInRange),
                    this.wrap(whenOutOfRange));
        }
        if (function instanceof DensityFunctions.ShiftedNoise(
                DensityFunction shiftX, DensityFunction shiftY, DensityFunction shiftZ, double xzScale, double yScale,
                rocks.minestom.worldgen.noise.NormalNoise noise1
        )) {
            return new DensityFunctions.ShiftedNoise(
                    this.wrap(shiftX),
                    this.wrap(shiftY),
                    this.wrap(shiftZ),
                    xzScale,
                    yScale,
                    noise1);
        }
        if (function instanceof DensityFunctions.BlendDensity(DensityFunction argument)) {
            return new DensityFunctions.BlendDensity(this.wrap(argument));
        }
        if (function instanceof DensityFunctions.Spline(DensityFunctions.SplineNode spline1)) {
            return new DensityFunctions.Spline(this.wrapSplineNode(spline1));
        }
        if (function instanceof DensityFunctions.WeirdScaledSampler(
                DensityFunction input, rocks.minestom.worldgen.noise.NormalNoise noise,
                DensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper
        )) {
            return new DensityFunctions.WeirdScaledSampler(
                    this.wrap(input),
                    noise,
                    rarityValueMapper);
        }

        // Leaf nodes that don't need wrapping
        return function;
    }

    private DensityFunctions.SplineNode wrapSplineNode(DensityFunctions.SplineNode node) {
        if (node instanceof DensityFunctions.SplineConstant) {
            return node;
        }
        if (node instanceof DensityFunctions.SplineMultipoint(
                DensityFunction coordinate, float[] locations, List<DensityFunctions.SplineNode> values,
                float[] derivatives
        )) {
            var wrappedValues = new ArrayList<DensityFunctions.SplineNode>(values.size());
            for (var value : values) {
                wrappedValues.add(this.wrapSplineNode(value));
            }
            return new DensityFunctions.SplineMultipoint(
                    this.wrap(coordinate),
                    locations,
                    wrappedValues,
                    derivatives);
        }
        return node;
    }

    /**
     * Initialize for the first cell X slice. Pre-computes density at all corners
     * for the first X slice.
     */
    public void initializeForFirstCellX() {
        if (this.interpolating) {
            throw new IllegalStateException("Starting interpolation twice");
        }
        this.interpolating = true;
        this.interpolationCounter = 0L;
        this.fillSlice(true, this.firstCellX);
    }

    /**
     * Advance to the next cell X slice. Swaps the slices and fills the new one.
     */
    public void advanceCellX(int cellOffsetX) {
        this.fillSlice(false, this.firstCellX + cellOffsetX + 1);
        this.cellStartBlockX = (this.firstCellX + cellOffsetX) * this.cellWidth;
    }

    /**
     * Fill a slice (either slice0 or slice1) with density values at cell corners.
     */
    private void fillSlice(boolean useSlice0, int cellX) {
        this.cellStartBlockX = cellX * this.cellWidth;
        this.inCellX = 0;

        for (var cellZ = 0; cellZ <= this.cellCountXZ; cellZ++) {
            var actualCellZ = this.firstCellZ + cellZ;
            this.cellStartBlockZ = actualCellZ * this.cellWidth;
            this.inCellZ = 0;

            for (var interpolator : this.interpolators) {
                var slice = useSlice0 ? interpolator.slice0 : interpolator.slice1;

                for (var cellY = 0; cellY <= this.cellCountY; cellY++) {
                    this.cellStartBlockY = (this.cellNoiseMinY + cellY) * this.cellHeight;
                    this.inCellY = 0;
                    this.interpolationCounter++;
                    slice[cellZ][cellY] = interpolator.noiseFiller.compute(this);
                }
            }
        }
    }

    /**
     * Select the cell at Y index cellY and Z index cellZ for processing.
     * Loads corner values from the pre-computed slices.
     */
    public void selectCellYZ(int cellY, int cellZ) {
        for (var interpolator : this.interpolators) {
            interpolator.selectCellYZ(cellY, cellZ);
        }
        this.cellStartBlockY = (cellY + this.cellNoiseMinY) * this.cellHeight;
        this.cellStartBlockZ = (this.firstCellZ + cellZ) * this.cellWidth;
    }

    /**
     * Update interpolation for the given Y position within the cell.
     */
    public void updateForY(int blockY, double deltaY) {
        this.inCellY = blockY - this.cellStartBlockY;
        for (var interpolator : this.interpolators) {
            interpolator.updateForY(deltaY);
        }
    }

    /**
     * Update interpolation for the given X position within the cell.
     */
    public void updateForX(int blockX, double deltaX) {
        this.inCellX = blockX - this.cellStartBlockX;
        for (var interpolator : this.interpolators) {
            interpolator.updateForX(deltaX);
        }
    }

    /**
     * Update interpolation for the given Z position within the cell.
     */
    public void updateForZ(int blockZ, double deltaZ) {
        this.inCellZ = blockZ - this.cellStartBlockZ;
        this.interpolationCounter++;
        for (var interpolator : this.interpolators) {
            interpolator.updateForZ(deltaZ);
        }
    }

    /**
     * Get the interpolated density value at the current position.
     */
    public double getInterpolatedDensity() {
        return this.finalDensity.compute(this);
    }

    /**
     * Swap slice0 and slice1 after advancing X.
     */
    public void swapSlices() {
        for (var interpolator : this.interpolators) {
            interpolator.swapSlices();
        }
    }

    /**
     * Stop interpolation mode.
     */
    public void stopInterpolation() {
        if (!this.interpolating) {
            throw new IllegalStateException("Not interpolating");
        }
        this.interpolating = false;
    }

    /**
     * Interpolator that samples density at 8 cell corners and interpolates between
     * them.
     * This is the core optimization.
     */
    private final class NoiseInterpolator implements DensityFunction {
        private final DensityFunction noiseFiller;
        double[][] slice0;
        double[][] slice1;

        // Corner values for current cell
        private double noise000, noise001, noise010, noise011;
        private double noise100, noise101, noise110, noise111;

        // Intermediate interpolation values
        private double valueXZ00, valueXZ01, valueXZ10, valueXZ11;
        private double valueZ0, valueZ1;
        private double value;

        NoiseInterpolator(DensityFunction noiseFiller) {
            this.noiseFiller = NoiseChunk.this.wrap(noiseFiller);
            this.slice0 = this.allocateSlice();
            this.slice1 = this.allocateSlice();
            NoiseChunk.this.interpolators.add(this);
        }

        private double[][] allocateSlice() {
            var zSize = NoiseChunk.this.cellCountXZ + 1;
            var ySize = NoiseChunk.this.cellCountY + 1;
            var slice = new double[zSize][ySize];
            return slice;
        }

        void selectCellYZ(int cellY, int cellZ) {
            this.noise000 = this.slice0[cellZ][cellY];
            this.noise001 = this.slice0[cellZ + 1][cellY];
            this.noise100 = this.slice1[cellZ][cellY];
            this.noise101 = this.slice1[cellZ + 1][cellY];
            this.noise010 = this.slice0[cellZ][cellY + 1];
            this.noise011 = this.slice0[cellZ + 1][cellY + 1];
            this.noise110 = this.slice1[cellZ][cellY + 1];
            this.noise111 = this.slice1[cellZ + 1][cellY + 1];
        }

        void updateForY(double deltaY) {
            this.valueXZ00 = VMath.lerp(deltaY, this.noise000, this.noise010);
            this.valueXZ10 = VMath.lerp(deltaY, this.noise100, this.noise110);
            this.valueXZ01 = VMath.lerp(deltaY, this.noise001, this.noise011);
            this.valueXZ11 = VMath.lerp(deltaY, this.noise101, this.noise111);
        }

        void updateForX(double deltaX) {
            this.valueZ0 = VMath.lerp(deltaX, this.valueXZ00, this.valueXZ10);
            this.valueZ1 = VMath.lerp(deltaX, this.valueXZ01, this.valueXZ11);
        }

        void updateForZ(double deltaZ) {
            this.value = VMath.lerp(deltaZ, this.valueZ0, this.valueZ1);
        }

        void swapSlices() {
            var temp = this.slice0;
            this.slice0 = this.slice1;
            this.slice1 = temp;
        }

        @Override
        public double compute(Context context) {
            if (context != NoiseChunk.this) {
                return this.noiseFiller.compute(context);
            }
            if (!NoiseChunk.this.interpolating) {
                throw new IllegalStateException("Trying to sample interpolator outside the interpolation loop");
            }
            return this.value;
        }
    }

    /**
     * Cache that stores the last computed value and reuses it if the position
     * hasn't changed.
     */
    private final class CacheOnce implements DensityFunction {
        private final DensityFunction function;
        private long lastCounter = -1L;
        private double lastValue;

        CacheOnce(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(Context context) {
            if (context != NoiseChunk.this) {
                return this.function.compute(context);
            }
            if (this.lastCounter == NoiseChunk.this.interpolationCounter) {
                return this.lastValue;
            }
            this.lastCounter = NoiseChunk.this.interpolationCounter;
            this.lastValue = this.function.compute(context);
            return this.lastValue;
        }
    }

    /**
     * Cache that only cares about X,Z position (ignores Y).
     */
    private final class Cache2D implements DensityFunction {
        private final DensityFunction function;
        private long lastPos2D = Long.MIN_VALUE;
        private double lastValue;

        Cache2D(DensityFunction function) {
            this.function = function;
        }

        @Override
        public double compute(Context context) {
            var x = context.blockX();
            var z = context.blockZ();
            var key = packXZ(x, z);
            if (this.lastPos2D == key) {
                return this.lastValue;
            }
            this.lastPos2D = key;
            this.lastValue = this.function.compute(context);
            return this.lastValue;
        }

        private static long packXZ(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }
    }

    /**
     * Pre-computes values at quart positions for the whole chunk once.
     */
    private final class FlatCache implements DensityFunction {
        private final DensityFunction function;
        private final double[] values;
        private final int sizeXZ;
        private final int firstNoiseX;
        private final int firstNoiseZ;

        FlatCache(DensityFunction function) {
            this.function = function;
            this.sizeXZ = 5; // 16 blocks / 4 quarts + 1
            this.values = new double[this.sizeXZ * this.sizeXZ];
            this.firstNoiseX = NoiseChunk.this.firstCellX * NoiseChunk.this.cellWidth / 4;
            this.firstNoiseZ = NoiseChunk.this.firstCellZ * NoiseChunk.this.cellWidth / 4;

            // Pre-compute values at quart positions
            for (var quartX = 0; quartX < this.sizeXZ; quartX++) {
                var blockX = (this.firstNoiseX + quartX) * 4;
                for (var quartZ = 0; quartZ < this.sizeXZ; quartZ++) {
                    var blockZ = (this.firstNoiseZ + quartZ) * 4;
                    this.values[quartX + quartZ * this.sizeXZ] = function
                            .compute(new SinglePointContext(blockX, 0, blockZ));
                }
            }
        }

        @Override
        public double compute(Context context) {
            var quartX = context.blockX() / 4 - this.firstNoiseX;
            var quartZ = context.blockZ() / 4 - this.firstNoiseZ;
            if (quartX >= 0 && quartZ >= 0 && quartX < this.sizeXZ && quartZ < this.sizeXZ) {
                return this.values[quartX + quartZ * this.sizeXZ];
            }
            return this.function.compute(context);
        }
    }

    /**
     * Pre-computes values for all positions in a cell.
     */
    private final class CacheAllInCell implements DensityFunction {
        private final DensityFunction function;
        private final double[] values;
        private int lastCellY = Integer.MIN_VALUE;
        private int lastCellZ = Integer.MIN_VALUE;

        CacheAllInCell(DensityFunction function) {
            this.function = function;
            this.values = new double[NoiseChunk.this.cellWidth * NoiseChunk.this.cellWidth
                    * NoiseChunk.this.cellHeight];
        }

        @Override
        public double compute(Context context) {
            if (context != NoiseChunk.this) {
                return this.function.compute(context);
            }

            var cellY = (NoiseChunk.this.cellStartBlockY / NoiseChunk.this.cellHeight) - NoiseChunk.this.cellNoiseMinY;
            var cellZ = (NoiseChunk.this.cellStartBlockZ / NoiseChunk.this.cellWidth) - NoiseChunk.this.firstCellZ;

            if (cellY != this.lastCellY || cellZ != this.lastCellZ) {
                this.lastCellY = cellY;
                this.lastCellZ = cellZ;
                this.fillCell();
            }

            var index = this.getIndex(NoiseChunk.this.inCellX, NoiseChunk.this.inCellY, NoiseChunk.this.inCellZ);
            return this.values[index];
        }

        private void fillCell() {
            var savedInCellX = NoiseChunk.this.inCellX;
            var savedInCellY = NoiseChunk.this.inCellY;
            var savedInCellZ = NoiseChunk.this.inCellZ;

            for (var y = 0; y < NoiseChunk.this.cellHeight; y++) {
                NoiseChunk.this.inCellY = y;
                for (var x = 0; x < NoiseChunk.this.cellWidth; x++) {
                    NoiseChunk.this.inCellX = x;
                    for (var z = 0; z < NoiseChunk.this.cellWidth; z++) {
                        NoiseChunk.this.inCellZ = z;
                        this.values[this.getIndex(x, y, z)] = this.function.compute(NoiseChunk.this);
                    }
                }
            }

            NoiseChunk.this.inCellX = savedInCellX;
            NoiseChunk.this.inCellY = savedInCellY;
            NoiseChunk.this.inCellZ = savedInCellZ;
        }

        private int getIndex(int x, int y, int z) {
            return ((NoiseChunk.this.cellHeight - 1 - y) * NoiseChunk.this.cellWidth + x) * NoiseChunk.this.cellWidth
                    + z;
        }
    }

    /**
     * Simple context for a single point, used for pre-computation.
     */
    private record SinglePointContext(int x, int y, int z) implements DensityFunction.Context {
        @Override
        public int blockX() {
            return this.x;
        }

        @Override
        public int blockY() {
            return this.y;
        }

        @Override
        public int blockZ() {
            return this.z;
        }
    }
}
