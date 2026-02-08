package rocks.minestom.worldgen.noise;

import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.random.RandomSource;

public final class SimplexNoise {
    public static final int[][] GRADIENT = new int[][]{
            {1, 1, 0},
            {-1, 1, 0},
            {1, -1, 0},
            {-1, -1, 0},
            {1, 0, 1},
            {-1, 0, 1},
            {1, 0, -1},
            {-1, 0, -1},
            {0, 1, 1},
            {0, -1, 1},
            {0, 1, -1},
            {0, -1, -1},
            {1, 1, 0},
            {0, -1, 1},
            {-1, 1, 0},
            {0, -1, -1}
    };
    private static final double SQRT_3 = Math.sqrt(3.0D);
    private static final double F2 = 0.5D * (SQRT_3 - 1.0D);
    private static final double G2 = (3.0D - SQRT_3) / 6.0D;

    private final int[] permutation = new int[512];
    public final double xo;
    public final double yo;
    public final double zo;

    public SimplexNoise(RandomSource randomSource) {
        this.xo = randomSource.nextDouble() * 256.0D;
        this.yo = randomSource.nextDouble() * 256.0D;
        this.zo = randomSource.nextDouble() * 256.0D;
        var index = 0;

        while (index < 256) {
            this.permutation[index] = index++;
        }

        for (var shuffleIndex = 0; shuffleIndex < 256; shuffleIndex++) {
            var swapIndex = randomSource.nextInt(256 - shuffleIndex);
            var value = this.permutation[shuffleIndex];
            this.permutation[shuffleIndex] = this.permutation[swapIndex + shuffleIndex];
            this.permutation[swapIndex + shuffleIndex] = value;
        }
    }

    private int p(int value) {
        return this.permutation[value & 0xFF];
    }

    public static double dot(int[] gradient, double x, double y, double z) {
        return (double) gradient[0] * x + (double) gradient[1] * y + (double) gradient[2] * z;
    }

    private double getCornerNoise3D(int gradientIndex, double x, double y, double z, double falloff) {
        var value = falloff - x * x - y * y - z * z;
        if (value < 0.0D) {
            return 0.0D;
        }
        value *= value;
        return value * value * dot(GRADIENT[gradientIndex], x, y, z);
    }

    public double getValue(double x, double y) {
        var skew = (x + y) * F2;
        var cellX = VMath.floor(x + skew);
        var cellY = VMath.floor(y + skew);
        var unskew = (double) (cellX + cellY) * G2;
        var originX = (double) cellX - unskew;
        var originY = (double) cellY - unskew;
        var localX = x - originX;
        var localY = y - originY;
        int offsetX;
        int offsetY;
        if (localX > localY) {
            offsetX = 1;
            offsetY = 0;
        } else {
            offsetX = 0;
            offsetY = 1;
        }

        var secondX = localX - (double) offsetX + G2;
        var secondY = localY - (double) offsetY + G2;
        var thirdX = localX - 1.0D + 2.0D * G2;
        var thirdY = localY - 1.0D + 2.0D * G2;
        var permX = cellX & 0xFF;
        var permY = cellY & 0xFF;
        var gradient0 = this.p(permX + this.p(permY)) % 12;
        var gradient1 = this.p(permX + offsetX + this.p(permY + offsetY)) % 12;
        var gradient2 = this.p(permX + 1 + this.p(permY + 1)) % 12;
        var noise0 = this.getCornerNoise3D(gradient0, localX, localY, 0.0D, 0.5D);
        var noise1 = this.getCornerNoise3D(gradient1, secondX, secondY, 0.0D, 0.5D);
        var noise2 = this.getCornerNoise3D(gradient2, thirdX, thirdY, 0.0D, 0.5D);
        return 70.0D * (noise0 + noise1 + noise2);
    }

    public double getValue(double x, double y, double z) {
        var skew = (x + y + z) * 0.3333333333333333D;
        var cellX = VMath.floor(x + skew);
        var cellY = VMath.floor(y + skew);
        var cellZ = VMath.floor(z + skew);
        var unskew = (double) (cellX + cellY + cellZ) * 0.16666666666666666D;
        var originX = (double) cellX - unskew;
        var originY = (double) cellY - unskew;
        var originZ = (double) cellZ - unskew;
        var localX = x - originX;
        var localY = y - originY;
        var localZ = z - originZ;
        int offsetX0;
        int offsetY0;
        int offsetZ0;
        int offsetX1;
        int offsetY1;
        int offsetZ1;
        if (localX >= localY) {
            if (localY >= localZ) {
                offsetX0 = 1;
                offsetY0 = 0;
                offsetZ0 = 0;
                offsetX1 = 1;
                offsetY1 = 1;
                offsetZ1 = 0;
            } else if (localX >= localZ) {
                offsetX0 = 1;
                offsetY0 = 0;
                offsetZ0 = 0;
                offsetX1 = 1;
                offsetY1 = 0;
                offsetZ1 = 1;
            } else {
                offsetX0 = 0;
                offsetY0 = 0;
                offsetZ0 = 1;
                offsetX1 = 1;
                offsetY1 = 0;
                offsetZ1 = 1;
            }
        } else if (localY < localZ) {
            offsetX0 = 0;
            offsetY0 = 0;
            offsetZ0 = 1;
            offsetX1 = 0;
            offsetY1 = 1;
            offsetZ1 = 1;
        } else if (localX < localZ) {
            offsetX0 = 0;
            offsetY0 = 1;
            offsetZ0 = 0;
            offsetX1 = 0;
            offsetY1 = 1;
            offsetZ1 = 1;
        } else {
            offsetX0 = 0;
            offsetY0 = 1;
            offsetZ0 = 0;
            offsetX1 = 1;
            offsetY1 = 1;
            offsetZ1 = 0;
        }

        var secondX = localX - (double) offsetX0 + 0.16666666666666666D;
        var secondY = localY - (double) offsetY0 + 0.16666666666666666D;
        var secondZ = localZ - (double) offsetZ0 + 0.16666666666666666D;
        var thirdX = localX - (double) offsetX1 + 0.3333333333333333D;
        var thirdY = localY - (double) offsetY1 + 0.3333333333333333D;
        var thirdZ = localZ - (double) offsetZ1 + 0.3333333333333333D;
        var fourthX = localX - 1.0D + 0.5D;
        var fourthY = localY - 1.0D + 0.5D;
        var fourthZ = localZ - 1.0D + 0.5D;
        var permX = cellX & 0xFF;
        var permY = cellY & 0xFF;
        var permZ = cellZ & 0xFF;
        var gradient0 = this.p(permX + this.p(permY + this.p(permZ))) % 12;
        var gradient1 = this.p(permX + offsetX0 + this.p(permY + offsetY0 + this.p(permZ + offsetZ0))) % 12;
        var gradient2 = this.p(permX + offsetX1 + this.p(permY + offsetY1 + this.p(permZ + offsetZ1))) % 12;
        var gradient3 = this.p(permX + 1 + this.p(permY + 1 + this.p(permZ + 1))) % 12;
        var noise0 = this.getCornerNoise3D(gradient0, localX, localY, localZ, 0.6D);
        var noise1 = this.getCornerNoise3D(gradient1, secondX, secondY, secondZ, 0.6D);
        var noise2 = this.getCornerNoise3D(gradient2, thirdX, thirdY, thirdZ, 0.6D);
        var noise3 = this.getCornerNoise3D(gradient3, fourthX, fourthY, fourthZ, 0.6D);
        return 32.0D * (noise0 + noise1 + noise2 + noise3);
    }
}
