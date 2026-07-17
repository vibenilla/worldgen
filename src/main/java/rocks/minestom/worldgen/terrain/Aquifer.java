package rocks.minestom.worldgen.terrain;

import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.NoiseChunk;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.random.PositionalRandomFactory;

import java.util.Arrays;

/**
 * Port of vanilla's {@code Aquifer}. Decides, for every block whose final density is
 * non-positive, whether it becomes air, water, lava or stays solid (barrier). Aquifer
 * cells are placed on a jittered grid and each cell rolls a local fluid level and type,
 * with pressure barriers computed between neighboring cells.
 */
public interface Aquifer {
    /**
     * Returns the block to place at the current position, or {@code null} if the block
     * should be the default solid block (possibly overridden by ore veins).
     */
    @Nullable
    Block computeSubstance(DensityFunction.Context context, double density);

    boolean shouldScheduleFluidUpdate();

    static Aquifer create(
            NoiseChunk noiseChunk,
            int minBlockX,
            int minBlockZ,
            DensityFunction barrierNoise,
            DensityFunction fluidLevelFloodednessNoise,
            DensityFunction fluidLevelSpreadNoise,
            DensityFunction lavaNoise,
            DensityFunction erosion,
            DensityFunction depth,
            PositionalRandomFactory positionalRandomFactory,
            int minBlockY,
            int yBlockSize,
            FluidPicker globalFluidPicker) {
        return new NoiseBasedAquifer(
                noiseChunk,
                minBlockX,
                minBlockZ,
                barrierNoise,
                fluidLevelFloodednessNoise,
                fluidLevelSpreadNoise,
                lavaNoise,
                erosion,
                depth,
                positionalRandomFactory,
                minBlockY,
                yBlockSize,
                globalFluidPicker);
    }

    static Aquifer createDisabled(FluidPicker fluidPicker) {
        return new Aquifer() {
            @Override
            public Block computeSubstance(DensityFunction.Context context, double density) {
                if (density > 0.0) {
                    return null;
                }
                return fluidPicker.computeFluid(context.blockX(), context.blockY(), context.blockZ()).at(context.blockY());
            }

            @Override
            public boolean shouldScheduleFluidUpdate() {
                return false;
            }
        };
    }

    /**
     * Port of vanilla's {@code NoiseBasedChunkGenerator.createFluidPicker}: lava below
     * {@code min(-54, seaLevel)}, the default fluid up to sea level above that.
     */
    static FluidPicker createGlobalFluidPicker(int seaLevel, Block defaultFluid) {
        var lavaStatus = new FluidStatus(-54, Block.LAVA);
        var seaStatus = new FluidStatus(seaLevel, defaultFluid);
        var lavaThreshold = Math.min(-54, seaLevel);
        return (x, y, z) -> y < lavaThreshold ? lavaStatus : seaStatus;
    }

    interface FluidPicker {
        FluidStatus computeFluid(int blockX, int blockY, int blockZ);
    }

    record FluidStatus(int fluidLevel, Block fluidType) {
        public Block at(int blockY) {
            return blockY < this.fluidLevel ? this.fluidType : Block.AIR;
        }
    }

    final class NoiseBasedAquifer implements Aquifer {
        private static final int X_RANGE = 10;
        private static final int Y_RANGE = 9;
        private static final int Z_RANGE = 10;
        private static final int X_SPACING = 16;
        private static final int Y_SPACING = 12;
        private static final int Z_SPACING = 16;
        private static final int X_SPACING_SHIFT = 4;
        private static final int Z_SPACING_SHIFT = 4;
        private static final double FLOWING_UPDATE_SIMILARITY = similarity(10 * 10, 12 * 12);
        private static final int SAMPLE_OFFSET_X = -5;
        private static final int SAMPLE_OFFSET_Y = 1;
        private static final int SAMPLE_OFFSET_Z = -5;
        private static final int MIN_CELL_SAMPLE_X = 0;
        private static final int MIN_CELL_SAMPLE_Y = -1;
        private static final int MIN_CELL_SAMPLE_Z = 0;
        private static final int MAX_CELL_SAMPLE_X = 1;
        private static final int MAX_CELL_SAMPLE_Y = 1;
        private static final int MAX_CELL_SAMPLE_Z = 1;
        // DimensionType.WAY_BELOW_MIN_Y = MIN_Y << 4 with MIN_Y = -2032
        private static final int WAY_BELOW_MIN_Y = -2032 << 4;
        private static final int[][] SURFACE_SAMPLING_OFFSETS_IN_CHUNKS = new int[][]{
                {0, 0}, {-2, -1}, {-1, -1}, {0, -1}, {1, -1}, {-3, 0}, {-2, 0}, {-1, 0}, {1, 0}, {-2, 1}, {-1, 1}, {0, 1}, {1, 1}
        };

        private final NoiseChunk noiseChunk;
        private final DensityFunction barrierNoise;
        private final DensityFunction fluidLevelFloodednessNoise;
        private final DensityFunction fluidLevelSpreadNoise;
        private final DensityFunction lavaNoise;
        private final PositionalRandomFactory positionalRandomFactory;
        private final FluidStatus[] aquiferCache;
        private final long[] aquiferLocationCache;
        private final FluidPicker globalFluidPicker;
        private final DensityFunction erosion;
        private final DensityFunction depth;
        private boolean shouldScheduleFluidUpdate;
        private final int skipSamplingAboveY;
        private final int minGridX;
        private final int minGridY;
        private final int minGridZ;
        private final int gridSizeX;
        private final int gridSizeZ;

        private NoiseBasedAquifer(
                NoiseChunk noiseChunk,
                int minBlockX,
                int minBlockZ,
                DensityFunction barrierNoise,
                DensityFunction fluidLevelFloodednessNoise,
                DensityFunction fluidLevelSpreadNoise,
                DensityFunction lavaNoise,
                DensityFunction erosion,
                DensityFunction depth,
                PositionalRandomFactory positionalRandomFactory,
                int minBlockY,
                int yBlockSize,
                FluidPicker globalFluidPicker) {
            this.noiseChunk = noiseChunk;
            this.barrierNoise = barrierNoise;
            this.fluidLevelFloodednessNoise = fluidLevelFloodednessNoise;
            this.fluidLevelSpreadNoise = fluidLevelSpreadNoise;
            this.lavaNoise = lavaNoise;
            this.erosion = erosion;
            this.depth = depth;
            this.positionalRandomFactory = positionalRandomFactory;
            this.minGridX = gridX(minBlockX + SAMPLE_OFFSET_X) + MIN_CELL_SAMPLE_X;
            this.globalFluidPicker = globalFluidPicker;
            var maxGridX = gridX(minBlockX + 15 + SAMPLE_OFFSET_X) + MAX_CELL_SAMPLE_X;
            this.gridSizeX = maxGridX - this.minGridX + 1;
            this.minGridY = gridY(minBlockY + SAMPLE_OFFSET_Y) + MIN_CELL_SAMPLE_Y;
            var maxGridY = gridY(minBlockY + yBlockSize + SAMPLE_OFFSET_Y) + MAX_CELL_SAMPLE_Y;
            var gridSizeY = maxGridY - this.minGridY + 1;
            this.minGridZ = gridZ(minBlockZ + SAMPLE_OFFSET_Z) + MIN_CELL_SAMPLE_Z;
            var maxGridZ = gridZ(minBlockZ + 15 + SAMPLE_OFFSET_Z) + MAX_CELL_SAMPLE_Z;
            this.gridSizeZ = maxGridZ - this.minGridZ + 1;
            var totalGridSize = this.gridSizeX * gridSizeY * this.gridSizeZ;
            this.aquiferCache = new FluidStatus[totalGridSize];
            this.aquiferLocationCache = new long[totalGridSize];
            Arrays.fill(this.aquiferLocationCache, Long.MAX_VALUE);
            var maxAdjustedSurfaceLevel = this.adjustSurfaceLevel(
                    noiseChunk.maxPreliminarySurfaceLevel(
                            fromGridX(this.minGridX, 0),
                            fromGridZ(this.minGridZ, 0),
                            fromGridX(maxGridX, X_RANGE - 1),
                            fromGridZ(maxGridZ, Z_RANGE - 1)));
            var skipSamplingAboveGridY = gridY(maxAdjustedSurfaceLevel + Y_SPACING) - MIN_CELL_SAMPLE_Y;
            this.skipSamplingAboveY = fromGridY(skipSamplingAboveGridY, Y_SPACING - 1) - 1;
        }

        private int getIndex(int gridX, int gridY, int gridZ) {
            var x = gridX - this.minGridX;
            var y = gridY - this.minGridY;
            var z = gridZ - this.minGridZ;
            return (y * this.gridSizeZ + z) * this.gridSizeX + x;
        }

        @Override
        public Block computeSubstance(DensityFunction.Context context, double density) {
            if (density > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }
            var posX = context.blockX();
            var posY = context.blockY();
            var posZ = context.blockZ();
            var globalFluid = this.globalFluidPicker.computeFluid(posX, posY, posZ);
            if (posY > this.skipSamplingAboveY) {
                this.shouldScheduleFluidUpdate = false;
                return globalFluid.at(posY);
            }
            if (globalFluid.at(posY).compare(Block.LAVA)) {
                this.shouldScheduleFluidUpdate = false;
                return Block.LAVA;
            }

            var xAnchor = gridX(posX + SAMPLE_OFFSET_X);
            var yAnchor = gridY(posY + SAMPLE_OFFSET_Y);
            var zAnchor = gridZ(posZ + SAMPLE_OFFSET_Z);
            var distanceSqr1 = Integer.MAX_VALUE;
            var distanceSqr2 = Integer.MAX_VALUE;
            var distanceSqr3 = Integer.MAX_VALUE;
            var distanceSqr4 = Integer.MAX_VALUE;
            var closestIndex1 = 0;
            var closestIndex2 = 0;
            var closestIndex3 = 0;
            var closestIndex4 = 0;

            for (var x1 = MIN_CELL_SAMPLE_X; x1 <= MAX_CELL_SAMPLE_X; x1++) {
                for (var y1 = MIN_CELL_SAMPLE_Y; y1 <= MAX_CELL_SAMPLE_Y; y1++) {
                    for (var z1 = MIN_CELL_SAMPLE_Z; z1 <= MAX_CELL_SAMPLE_Z; z1++) {
                        var spacedGridX = xAnchor + x1;
                        var spacedGridY = yAnchor + y1;
                        var spacedGridZ = zAnchor + z1;
                        var index = this.getIndex(spacedGridX, spacedGridY, spacedGridZ);
                        var existingLocation = this.aquiferLocationCache[index];
                        long location;
                        if (existingLocation != Long.MAX_VALUE) {
                            location = existingLocation;
                        } else {
                            var random = this.positionalRandomFactory.at(spacedGridX, spacedGridY, spacedGridZ);
                            location = packBlockPos(
                                    fromGridX(spacedGridX, random.nextInt(X_RANGE)),
                                    fromGridY(spacedGridY, random.nextInt(Y_RANGE)),
                                    fromGridZ(spacedGridZ, random.nextInt(Z_RANGE)));
                            this.aquiferLocationCache[index] = location;
                        }

                        var dx = unpackBlockPosX(location) - posX;
                        var dy = unpackBlockPosY(location) - posY;
                        var dz = unpackBlockPosZ(location) - posZ;
                        var newDistance = dx * dx + dy * dy + dz * dz;
                        if (distanceSqr1 >= newDistance) {
                            closestIndex4 = closestIndex3;
                            closestIndex3 = closestIndex2;
                            closestIndex2 = closestIndex1;
                            closestIndex1 = index;
                            distanceSqr4 = distanceSqr3;
                            distanceSqr3 = distanceSqr2;
                            distanceSqr2 = distanceSqr1;
                            distanceSqr1 = newDistance;
                        } else if (distanceSqr2 >= newDistance) {
                            closestIndex4 = closestIndex3;
                            closestIndex3 = closestIndex2;
                            closestIndex2 = index;
                            distanceSqr4 = distanceSqr3;
                            distanceSqr3 = distanceSqr2;
                            distanceSqr2 = newDistance;
                        } else if (distanceSqr3 >= newDistance) {
                            closestIndex4 = closestIndex3;
                            closestIndex3 = index;
                            distanceSqr4 = distanceSqr3;
                            distanceSqr3 = newDistance;
                        } else if (distanceSqr4 >= newDistance) {
                            closestIndex4 = index;
                            distanceSqr4 = newDistance;
                        }
                    }
                }
            }

            var closestStatus1 = this.getAquiferStatus(closestIndex1);
            var similarity12 = similarity(distanceSqr1, distanceSqr2);
            var fluidState = closestStatus1.at(posY);
            if (similarity12 <= 0.0) {
                if (similarity12 >= FLOWING_UPDATE_SIMILARITY) {
                    var closestStatus2 = this.getAquiferStatus(closestIndex2);
                    this.shouldScheduleFluidUpdate = !closestStatus1.equals(closestStatus2);
                } else {
                    this.shouldScheduleFluidUpdate = false;
                }
                return fluidState;
            }
            if (fluidState.compare(Block.WATER) && this.globalFluidPicker.computeFluid(posX, posY - 1, posZ).at(posY - 1).compare(Block.LAVA)) {
                this.shouldScheduleFluidUpdate = true;
                return fluidState;
            }

            var barrierNoiseValue = new MutableDouble();
            var closestStatus2 = this.getAquiferStatus(closestIndex2);
            var barrier12 = similarity12 * this.calculatePressure(context, barrierNoiseValue, closestStatus1, closestStatus2);
            if (density + barrier12 > 0.0) {
                this.shouldScheduleFluidUpdate = false;
                return null;
            }

            var closestStatus3 = this.getAquiferStatus(closestIndex3);
            var similarity13 = similarity(distanceSqr1, distanceSqr3);
            if (similarity13 > 0.0) {
                var barrier13 = similarity12 * similarity13 * this.calculatePressure(context, barrierNoiseValue, closestStatus1, closestStatus3);
                if (density + barrier13 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }

            var similarity23 = similarity(distanceSqr2, distanceSqr3);
            if (similarity23 > 0.0) {
                var barrier23 = similarity12 * similarity23 * this.calculatePressure(context, barrierNoiseValue, closestStatus2, closestStatus3);
                if (density + barrier23 > 0.0) {
                    this.shouldScheduleFluidUpdate = false;
                    return null;
                }
            }

            var mayFlow12 = !closestStatus1.equals(closestStatus2);
            var mayFlow23 = similarity23 >= FLOWING_UPDATE_SIMILARITY && !closestStatus2.equals(closestStatus3);
            var mayFlow13 = similarity13 >= FLOWING_UPDATE_SIMILARITY && !closestStatus1.equals(closestStatus3);
            if (!mayFlow12 && !mayFlow23 && !mayFlow13) {
                this.shouldScheduleFluidUpdate = similarity13 >= FLOWING_UPDATE_SIMILARITY
                        && similarity(distanceSqr1, distanceSqr4) >= FLOWING_UPDATE_SIMILARITY
                        && !closestStatus1.equals(this.getAquiferStatus(closestIndex4));
            } else {
                this.shouldScheduleFluidUpdate = true;
            }

            return fluidState;
        }

        @Override
        public boolean shouldScheduleFluidUpdate() {
            return this.shouldScheduleFluidUpdate;
        }

        private static double similarity(int distanceSqr1, int distanceSqr2) {
            return 1.0 - (distanceSqr2 - distanceSqr1) / 25.0;
        }

        private double calculatePressure(
                DensityFunction.Context context,
                MutableDouble barrierNoiseValue,
                FluidStatus statusClosest1,
                FluidStatus statusClosest2) {
            var posY = context.blockY();
            var type1 = statusClosest1.at(posY);
            var type2 = statusClosest2.at(posY);
            if ((type1.compare(Block.LAVA) && type2.compare(Block.WATER)) || (type1.compare(Block.WATER) && type2.compare(Block.LAVA))) {
                return 2.0;
            }

            var fluidYDiff = Math.abs(statusClosest1.fluidLevel() - statusClosest2.fluidLevel());
            if (fluidYDiff == 0) {
                return 0.0;
            }

            var averageFluidY = 0.5 * (statusClosest1.fluidLevel() + statusClosest2.fluidLevel());
            var howFarAboveAverageFluidPoint = posY + 0.5 - averageFluidY;
            var baseValue = fluidYDiff / 2.0;
            var distanceFromBarrierEdgeTowardsMiddle = baseValue - Math.abs(howFarAboveAverageFluidPoint);
            double gradient;
            if (howFarAboveAverageFluidPoint > 0.0) {
                var centerPoint = 0.0 + distanceFromBarrierEdgeTowardsMiddle;
                if (centerPoint > 0.0) {
                    gradient = centerPoint / 1.5;
                } else {
                    gradient = centerPoint / 2.5;
                }
            } else {
                var centerPoint = 3.0 + distanceFromBarrierEdgeTowardsMiddle;
                if (centerPoint > 0.0) {
                    gradient = centerPoint / 3.0;
                } else {
                    gradient = centerPoint / 10.0;
                }
            }

            double noiseValue;
            if (!(gradient < -2.0) && !(gradient > 2.0)) {
                var currentNoiseValue = barrierNoiseValue.value;
                if (Double.isNaN(currentNoiseValue)) {
                    var barrierNoise = this.barrierNoise.compute(context);
                    barrierNoiseValue.value = barrierNoise;
                    noiseValue = barrierNoise;
                } else {
                    noiseValue = currentNoiseValue;
                }
            } else {
                noiseValue = 0.0;
            }

            return 2.0 * (noiseValue + gradient);
        }

        private static int gridX(int blockCoord) {
            return blockCoord >> X_SPACING_SHIFT;
        }

        private static int fromGridX(int gridCoord, int blockOffset) {
            return (gridCoord << X_SPACING_SHIFT) + blockOffset;
        }

        private static int gridY(int blockCoord) {
            return Math.floorDiv(blockCoord, Y_SPACING);
        }

        private static int fromGridY(int gridCoord, int blockOffset) {
            return gridCoord * Y_SPACING + blockOffset;
        }

        private static int gridZ(int blockCoord) {
            return blockCoord >> Z_SPACING_SHIFT;
        }

        private static int fromGridZ(int gridCoord, int blockOffset) {
            return (gridCoord << Z_SPACING_SHIFT) + blockOffset;
        }

        private FluidStatus getAquiferStatus(int index) {
            var oldStatus = this.aquiferCache[index];
            if (oldStatus != null) {
                return oldStatus;
            }
            var location = this.aquiferLocationCache[index];
            var status = this.computeFluid(unpackBlockPosX(location), unpackBlockPosY(location), unpackBlockPosZ(location));
            this.aquiferCache[index] = status;
            return status;
        }

        private FluidStatus computeFluid(int x, int y, int z) {
            var globalFluid = this.globalFluidPicker.computeFluid(x, y, z);
            var lowestPreliminarySurface = Integer.MAX_VALUE;
            var topOfAquiferCell = y + Y_SPACING;
            var bottomOfAquiferCell = y - Y_SPACING;
            var surfaceAtCenterIsUnderGlobalFluidLevel = false;

            for (var offset : SURFACE_SAMPLING_OFFSETS_IN_CHUNKS) {
                var sampleX = x + (offset[0] << 4);
                var sampleZ = z + (offset[1] << 4);
                var preliminarySurfaceLevel = this.noiseChunk.preliminarySurfaceLevel(sampleX, sampleZ);
                var adjustedSurfaceLevel = this.adjustSurfaceLevel(preliminarySurfaceLevel);
                var start = offset[0] == 0 && offset[1] == 0;
                if (start && bottomOfAquiferCell > adjustedSurfaceLevel) {
                    return globalFluid;
                }

                var topOfAquiferCellPokesAboveSurface = topOfAquiferCell > adjustedSurfaceLevel;
                if (topOfAquiferCellPokesAboveSurface || start) {
                    var globalFluidAtSurface = this.globalFluidPicker.computeFluid(sampleX, adjustedSurfaceLevel, sampleZ);
                    if (!globalFluidAtSurface.at(adjustedSurfaceLevel).isAir()) {
                        if (start) {
                            surfaceAtCenterIsUnderGlobalFluidLevel = true;
                        }
                        if (topOfAquiferCellPokesAboveSurface) {
                            return globalFluidAtSurface;
                        }
                    }
                }

                lowestPreliminarySurface = Math.min(lowestPreliminarySurface, preliminarySurfaceLevel);
            }

            var fluidSurfaceLevel = this.computeSurfaceLevel(x, y, z, globalFluid, lowestPreliminarySurface, surfaceAtCenterIsUnderGlobalFluidLevel);
            return new FluidStatus(fluidSurfaceLevel, this.computeFluidType(x, y, z, globalFluid, fluidSurfaceLevel));
        }

        private int adjustSurfaceLevel(int preliminarySurfaceLevel) {
            return preliminarySurfaceLevel + 8;
        }

        private int computeSurfaceLevel(
                int x,
                int y,
                int z,
                FluidStatus globalFluid,
                int lowestPreliminarySurface,
                boolean surfaceAtCenterIsUnderGlobalFluidLevel) {
            var context = new DensityFunction.SinglePointContext(x, y, z);
            double partiallyFloodedness;
            double fullyFloodedness;
            if (isDeepDarkRegion(this.erosion, this.depth, context)) {
                partiallyFloodedness = -1.0;
                fullyFloodedness = -1.0;
            } else {
                var distanceBelowSurface = lowestPreliminarySurface + 8 - y;
                var floodednessFactor = surfaceAtCenterIsUnderGlobalFluidLevel
                        ? clampedMap(distanceBelowSurface, 0.0, 64.0, 1.0, 0.0)
                        : 0.0;
                var floodednessNoiseValue = VMath.clamp(this.fluidLevelFloodednessNoise.compute(context), -1.0, 1.0);
                var fullyFloodedThreshold = map(floodednessFactor, 1.0, 0.0, -0.3, 0.8);
                var partiallyFloodedThreshold = map(floodednessFactor, 1.0, 0.0, -0.8, 0.4);
                partiallyFloodedness = floodednessNoiseValue - partiallyFloodedThreshold;
                fullyFloodedness = floodednessNoiseValue - fullyFloodedThreshold;
            }

            int fluidSurfaceLevel;
            if (fullyFloodedness > 0.0) {
                fluidSurfaceLevel = globalFluid.fluidLevel();
            } else if (partiallyFloodedness > 0.0) {
                fluidSurfaceLevel = this.computeRandomizedFluidSurfaceLevel(x, y, z, lowestPreliminarySurface);
            } else {
                fluidSurfaceLevel = WAY_BELOW_MIN_Y;
            }

            return fluidSurfaceLevel;
        }

        private int computeRandomizedFluidSurfaceLevel(int x, int y, int z, int lowestPreliminarySurface) {
            var fluidLevelCellX = Math.floorDiv(x, 16);
            var fluidLevelCellY = Math.floorDiv(y, 40);
            var fluidLevelCellZ = Math.floorDiv(z, 16);
            var fluidCellMiddleY = fluidLevelCellY * 40 + 20;
            var fluidLevelSpread = this.fluidLevelSpreadNoise
                    .compute(new DensityFunction.SinglePointContext(fluidLevelCellX, fluidLevelCellY, fluidLevelCellZ)) * 10.0;
            var fluidLevelSpreadQuantized = quantize(fluidLevelSpread, 3);
            var targetFluidSurfaceLevel = fluidCellMiddleY + fluidLevelSpreadQuantized;
            return Math.min(lowestPreliminarySurface, targetFluidSurfaceLevel);
        }

        private Block computeFluidType(int x, int y, int z, FluidStatus globalFluid, int fluidSurfaceLevel) {
            var fluidType = globalFluid.fluidType();
            if (fluidSurfaceLevel <= -10 && fluidSurfaceLevel != WAY_BELOW_MIN_Y && !globalFluid.fluidType().compare(Block.LAVA)) {
                var fluidTypeCellX = Math.floorDiv(x, 64);
                var fluidTypeCellY = Math.floorDiv(y, 40);
                var fluidTypeCellZ = Math.floorDiv(z, 64);
                var lavaNoiseValue = this.lavaNoise
                        .compute(new DensityFunction.SinglePointContext(fluidTypeCellX, fluidTypeCellY, fluidTypeCellZ));
                if (Math.abs(lavaNoiseValue) > 0.3) {
                    fluidType = Block.LAVA;
                }
            }

            return fluidType;
        }

        // Port of OverworldBiomeBuilder.isDeepDarkRegion
        private static boolean isDeepDarkRegion(DensityFunction erosion, DensityFunction depth, DensityFunction.Context context) {
            return erosion.compute(context) < -0.225F && depth.compute(context) > 0.9F;
        }

        // Exact port of Mth.clampedMap (clampedLerp over inverseLerp)
        private static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
            var delta = inverseLerp(value, fromMin, fromMax);
            if (delta < 0.0) {
                return toMin;
            }
            if (delta > 1.0) {
                return toMax;
            }
            return VMath.lerp(delta, toMin, toMax);
        }

        // Exact port of Mth.map
        private static double map(double value, double fromMin, double fromMax, double toMin, double toMax) {
            return VMath.lerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
        }

        private static double inverseLerp(double value, double min, double max) {
            return (value - min) / (max - min);
        }

        // Exact port of Mth.quantize
        private static int quantize(double value, int quantizeResolution) {
            return VMath.floor(value / quantizeResolution) * quantizeResolution;
        }

        // Vanilla BlockPos long packing (26/12/26 bits, X high, Y low, Z middle)
        private static long packBlockPos(int x, int y, int z) {
            return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
        }

        private static int unpackBlockPosX(long packed) {
            return (int) (packed >> 38);
        }

        private static int unpackBlockPosY(long packed) {
            return (int) (packed << 52 >> 52);
        }

        private static int unpackBlockPosZ(long packed) {
            return (int) (packed << 26 >> 38);
        }

        private static final class MutableDouble {
            private double value = Double.NaN;
        }
    }
}
