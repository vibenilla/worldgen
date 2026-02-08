package rocks.minestom.worldgen;

import java.util.function.IntPredicate;

public final class VMath {
    private VMath() {
    }

    public static int floor(double value) {
        var truncated = (int) value;
        return value < (double) truncated ? truncated - 1 : truncated;
    }

    public static long lfloor(double value) {
        var truncated = (long) value;
        return value < (double) truncated ? truncated - 1 : truncated;
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clampedLerp(double delta, double start, double end) {
        if (delta < 0.0D) return start;
        if (delta > 1.0D) return end;
        return lerp(delta, start, end);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    public static double lerp2(double deltaX, double deltaY, double value00, double value10, double value01, double value11) {
        return lerp(deltaY, lerp(deltaX, value00, value10), lerp(deltaX, value01, value11));
    }

    public static double lerp3(
            double deltaX,
            double deltaY,
            double deltaZ,
            double value000,
            double value100,
            double value010,
            double value110,
            double value001,
            double value101,
            double value011,
            double value111
    ) {
        return lerp(deltaZ, lerp2(deltaX, deltaY, value000, value100, value010, value110), lerp2(deltaX, deltaY, value001, value101, value011, value111));
    }

    public static double smoothstep(double value) {
        return value * value * value * (value * (value * 6.0 - 15.0) + 10.0);
    }

    public static double smoothstepDerivative(double value) {
        return 30.0 * value * value * (value - 1.0) * (value - 1.0);
    }

    public static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        var clamped = clamp(value, fromMin, fromMax);
        return lerp((clamped - fromMin) / (fromMax - fromMin), toMin, toMax);
    }

    public static long getSeed(int x, int y, int z) {
        var seed = (long) (x * 3129871L) ^ (long) z * 116129781L ^ (long) y;
        seed = seed * seed * 42317861L + seed * 11L;
        return seed >> 16;
    }

    public static int binarySearch(int startInclusive, int endExclusive, IntPredicate predicate) {
        var currentStart = startInclusive;
        var currentEnd = endExclusive;

        while (currentStart < currentEnd) {
            var middle = currentStart + (currentEnd - currentStart) / 2;
            if (predicate.test(middle)) {
                currentEnd = middle;
            } else {
                currentStart = middle + 1;
            }
        }

        return currentStart;
    }
}
