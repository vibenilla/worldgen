package rocks.minestom.worldgen.density;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.noise.BlendedNoise;
import rocks.minestom.worldgen.noise.NormalNoise;
import rocks.minestom.worldgen.noise.SimplexNoise;

import java.util.List;

/**
 * Collection of density function nodes used to compose terrain, cave, and climate fields.
 * These nodes are the vocabulary for shaping the world: combining noise, gradients,
 * clamps, splines, and caches to build a coherent density field from many influences.
 */
public final class DensityFunctions {
    private DensityFunctions() {
    }

    public record Constant(double value) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.value;
        }
    }

    public record Add(DensityFunction argument1, DensityFunction argument2) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.argument1.compute(context) + this.argument2.compute(context);
        }
    }

    public record Mul(DensityFunction argument1, DensityFunction argument2) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.argument1.compute(context) * this.argument2.compute(context);
        }
    }

    public record Min(DensityFunction argument1, DensityFunction argument2) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return Math.min(this.argument1.compute(context), this.argument2.compute(context));
        }
    }

    public record Max(DensityFunction argument1, DensityFunction argument2) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return Math.max(this.argument1.compute(context), this.argument2.compute(context));
        }
    }

    public record Clamp(DensityFunction input, double min, double max) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return VMath.clamp(this.input.compute(context), this.min, this.max);
        }
    }

    public record Mapped(Type type, DensityFunction input) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.type.transform(this.input.compute(context));
        }

        public enum Type {
            ABS {
                @Override
                public double transform(double value) {
                    return Math.abs(value);
                }
            },
            SQUARE {
                @Override
                public double transform(double value) {
                    return value * value;
                }
            },
            CUBE {
                @Override
                public double transform(double value) {
                    return value * value * value;
                }
            },
            HALF_NEGATIVE {
                @Override
                public double transform(double value) {
                    return value > 0.0 ? value : value * 0.5;
                }
            },
            QUARTER_NEGATIVE {
                @Override
                public double transform(double value) {
                    return value > 0.0 ? value : value * 0.25;
                }
            },
            SQUEEZE {
                @Override
                public double transform(double value) {
                    var clamped = VMath.clamp(value, -1.0, 1.0);
                    return clamped / 2.0 - clamped * clamped * clamped / 24.0;
                }
            };

            public abstract double transform(double value);
        }
    }

    public record YClampedGradient(int fromY, int toY, double fromValue, double toValue) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return VMath.clampedMap((double) context.blockY(), (double) this.fromY, (double) this.toY, this.fromValue, this.toValue);
        }
    }

    public record RangeChoice(
            DensityFunction input,
            double minInclusive,
            double maxExclusive,
            DensityFunction whenInRange,
            DensityFunction whenOutOfRange
    ) implements DensityFunction {
        @Override
        public double compute(Context context) {
            var value = this.input.compute(context);
            if (value >= this.minInclusive && value < this.maxExclusive) {
                return this.whenInRange.compute(context);
            }
            return this.whenOutOfRange.compute(context);
        }
    }

    public record Noise(NormalNoise noise, double xzScale, double yScale) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.noise.getValue((double) context.blockX() * this.xzScale, (double) context.blockY() * this.yScale, (double) context.blockZ() * this.xzScale);
        }
    }

    public record ShiftedNoise(
            DensityFunction shiftX,
            DensityFunction shiftY,
            DensityFunction shiftZ,
            double xzScale,
            double yScale,
            NormalNoise noise
    ) implements DensityFunction {
        @Override
        public double compute(Context context) {
            var x = (double) context.blockX() * this.xzScale + this.shiftX.compute(context);
            var y = (double) context.blockY() * this.yScale + this.shiftY.compute(context);
            var z = (double) context.blockZ() * this.xzScale + this.shiftZ.compute(context);
            return this.noise.getValue(x, y, z);
        }
    }

    public record ShiftA(NormalNoise offsetNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.offsetNoise.getValue((double) context.blockX() * 0.25, 0.0, (double) context.blockZ() * 0.25) * 4.0;
        }
    }

    public record ShiftB(NormalNoise offsetNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.offsetNoise.getValue((double) context.blockZ() * 0.25, (double) context.blockX() * 0.25, 0.0) * 4.0;
        }
    }

    public record BlendAlpha() implements DensityFunction {
        @Override
        public double compute(Context context) {
            return 1.0;
        }
    }

    public record BlendOffset() implements DensityFunction {
        @Override
        public double compute(Context context) {
            return 0.0;
        }
    }

    public record BlendDensity(DensityFunction argument) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.argument.compute(context);
        }
    }

    public record Interpolated(DensityFunction argument) implements DensityFunction {
        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return chunkContext.interpolatedValue(this);
            }
            return this.argument.compute(context);
        }
    }

    public record CacheOnce(DensityFunction argument) implements DensityFunction {
        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return chunkContext.cacheOnceValue(this);
            }
            return this.argument.compute(context);
        }
    }

    public record Cache2D(DensityFunction argument) implements DensityFunction {
        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return chunkContext.cache2DValue(this);
            }
            return this.argument.compute(context);
        }
    }

    public record FlatCache(DensityFunction argument) implements DensityFunction {
        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return chunkContext.flatCacheValue(this);
            }
            return this.argument.compute(context);
        }
    }

    public record CacheAllInCell(DensityFunction argument) implements DensityFunction {
        @Override
        public double compute(Context context) {
            if (context instanceof ChunkContext chunkContext) {
                return chunkContext.cacheAllInCellValue(this);
            }
            return this.argument.compute(context);
        }
    }

    public record OldBlendedNoise(BlendedNoise blendedNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.blendedNoise.compute(context.blockX(), context.blockY(), context.blockZ());
        }
    }

    public record EndIslands(SimplexNoise islandNoise) implements DensityFunction {
        @Override
        public double compute(Context context) {
            var height = getHeightValue(this.islandNoise, context.blockX() / 8, context.blockZ() / 8);
            return ((double) height - 8.0D) / 128.0D;
        }

        private static float getHeightValue(SimplexNoise simplexNoise, int blockX, int blockZ) {
            var halfX = blockX / 2;
            var halfZ = blockZ / 2;
            var offsetX = blockX % 2;
            var offsetZ = blockZ % 2;
            var height = 100.0F - (float) Math.sqrt((float) (blockX * blockX + blockZ * blockZ)) * 8.0F;
            height = (float) VMath.clamp((double) height, -100.0D, 80.0D);

            for (var localX = -12; localX <= 12; localX++) {
                for (var localZ = -12; localZ <= 12; localZ++) {
                    var centerX = (long) (halfX + localX);
                    var centerZ = (long) (halfZ + localZ);
                    if (centerX * centerX + centerZ * centerZ > 4096L && simplexNoise.getValue((double) centerX, (double) centerZ) < -0.9D) {
                        var scale = (Math.abs((float) centerX) * 3439.0F + Math.abs((float) centerZ) * 147.0F) % 13.0F + 9.0F;
                        var deltaX = (float) (offsetX - localX * 2);
                        var deltaZ = (float) (offsetZ - localZ * 2);
                        var candidate = 100.0F - (float) Math.sqrt(deltaX * deltaX + deltaZ * deltaZ) * scale;
                        candidate = (float) VMath.clamp((double) candidate, -100.0D, 80.0D);
                        height = Math.max(height, candidate);
                    }
                }
            }

            return height;
        }
    }

    public record WeirdScaledSampler(DensityFunction input, NormalNoise noise, RarityValueMapper rarityValueMapper)
            implements DensityFunction {
        @Override
        public double compute(Context context) {
            var value = this.input.compute(context);
            var rarity = this.rarityValueMapper.map(value);
            return rarity * Math.abs(this.noise.getValue((double) context.blockX() / rarity, (double) context.blockY() / rarity, (double) context.blockZ() / rarity));
        }

        public enum RarityValueMapper {
            TYPE_1 {
                @Override
                public double map(double value) {
                    if (value < -0.5) {
                        return 0.75;
                    }
                    if (value < 0.0) {
                        return 1.0;
                    }
                    return value < 0.5 ? 1.5 : 2.0;
                }
            },
            TYPE_2 {
                @Override
                public double map(double value) {
                    if (value < -0.75) {
                        return 0.5;
                    }
                    if (value < -0.5) {
                        return 0.75;
                    }
                    if (value < 0.5) {
                        return 1.0;
                    }
                    return value < 0.75 ? 2.0 : 3.0;
                }
            };

            public abstract double map(double value);
        }
    }

    public sealed interface SplineNode permits SplineConstant, SplineMultipoint {
        float compute(DensityFunction.Context context);
    }

    public record SplineConstant(float value) implements SplineNode {
        @Override
        public float compute(DensityFunction.Context context) {
            return this.value;
        }
    }

    public record SplineMultipoint(
            DensityFunction coordinate,
            float[] locations,
            List<SplineNode> values,
            float[] derivatives
    ) implements SplineNode {
        @Override
        public float compute(DensityFunction.Context context) {
            var coordinateValue = (float) this.coordinate.compute(context);
            var intervalStart = findIntervalStart(this.locations, coordinateValue);
            var lastIndex = this.locations.length - 1;
            if (intervalStart < 0) {
                return linearExtend(coordinateValue, this.locations, this.values.get(0).compute(context), this.derivatives, 0);
            }
            if (intervalStart == lastIndex) {
                return linearExtend(coordinateValue, this.locations, this.values.get(lastIndex).compute(context), this.derivatives, lastIndex);
            }

            var startLocation = this.locations[intervalStart];
            var endLocation = this.locations[intervalStart + 1];
            var delta = (coordinateValue - startLocation) / (endLocation - startLocation);
            var startValue = this.values.get(intervalStart).compute(context);
            var endValue = this.values.get(intervalStart + 1).compute(context);
            var startDerivative = this.derivatives[intervalStart];
            var endDerivative = this.derivatives[intervalStart + 1];
            var p = startDerivative * (endLocation - startLocation) - (endValue - startValue);
            var q = -endDerivative * (endLocation - startLocation) + (endValue - startValue);
            return (float) VMath.lerp((double) delta, (double) startValue, (double) endValue) + delta * (1.0F - delta) * (float) VMath.lerp((double) delta, (double) p, (double) q);
        }

        private static float linearExtend(float coordinate, float[] locations, float value, float[] derivatives, int index) {
            var derivative = derivatives[index];
            if (derivative == 0.0F) {
                return value;
            }
            return value + derivative * (coordinate - locations[index]);
        }

        private static int findIntervalStart(float[] locations, float coordinate) {
            return VMath.binarySearch(0, locations.length, index -> coordinate < locations[index]) - 1;
        }
    }

    public record Spline(SplineNode spline) implements DensityFunction {
        @Override
        public double compute(Context context) {
            return this.spline.compute(context);
        }
    }
}
