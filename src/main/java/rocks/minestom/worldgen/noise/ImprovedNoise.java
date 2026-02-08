package rocks.minestom.worldgen.noise;

import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.random.RandomSource;

public final class ImprovedNoise {
    private static final float SHIFT_UP_EPSILON = 1.0E-7F;

    private final byte[] permutations;
    public final double xo;
    public final double yo;
    public final double zo;

    public ImprovedNoise(RandomSource randomSource) {
        this.xo = randomSource.nextDouble() * 256.0;
        this.yo = randomSource.nextDouble() * 256.0;
        this.zo = randomSource.nextDouble() * 256.0;
        this.permutations = new byte[256];

        for (var index = 0; index < 256; index++) {
            this.permutations[index] = (byte) index;
        }

        for (var index = 0; index < 256; index++) {
            var swapIndex = randomSource.nextInt(256 - index);
            var value = this.permutations[index];
            this.permutations[index] = this.permutations[index + swapIndex];
            this.permutations[index + swapIndex] = value;
        }
    }

    public double noise(double x, double y, double z, double yScale, double yMax) {
        var offsetX = x + this.xo;
        var offsetY = y + this.yo;
        var offsetZ = z + this.zo;
        var floorX = VMath.floor(offsetX);
        var floorY = VMath.floor(offsetY);
        var floorZ = VMath.floor(offsetZ);
        var localX = offsetX - (double) floorX;
        var localY = offsetY - (double) floorY;
        var localZ = offsetZ - (double) floorZ;

        var yFloorOffset = 0.0;

        if (yScale != 0.0) {
            var yToFloor = 0.0;

            if (yMax >= 0.0 && yMax < localY) {
                yToFloor = yMax;
            } else {
                yToFloor = localY;
            }

            yFloorOffset = (double) VMath.floor(yToFloor / yScale + (double) SHIFT_UP_EPSILON) * yScale;
        }

        return this.sampleAndLerp(floorX, floorY, floorZ, localX, localY - yFloorOffset, localZ, localY);
    }

    private static double gradDot(int hash, double x, double y, double z) {
        return SimplexNoise.dot(SimplexNoise.GRADIENT[hash & 15], x, y, z);
    }

    private int p(int value) {
        return this.permutations[value & 0xFF] & 0xFF;
    }

    private double sampleAndLerp(int x, int y, int z, double localX, double localY, double localZ, double smoothY) {
        var permX0 = this.p(x);
        var permX1 = this.p(x + 1);
        var permX0Y0 = this.p(permX0 + y);
        var permX0Y1 = this.p(permX0 + y + 1);
        var permX1Y0 = this.p(permX1 + y);
        var permX1Y1 = this.p(permX1 + y + 1);

        var value000 = gradDot(this.p(permX0Y0 + z), localX, localY, localZ);
        var value100 = gradDot(this.p(permX1Y0 + z), localX - 1.0, localY, localZ);
        var value010 = gradDot(this.p(permX0Y1 + z), localX, localY - 1.0, localZ);
        var value110 = gradDot(this.p(permX1Y1 + z), localX - 1.0, localY - 1.0, localZ);

        var value001 = gradDot(this.p(permX0Y0 + z + 1), localX, localY, localZ - 1.0);
        var value101 = gradDot(this.p(permX1Y0 + z + 1), localX - 1.0, localY, localZ - 1.0);
        var value011 = gradDot(this.p(permX0Y1 + z + 1), localX, localY - 1.0, localZ - 1.0);
        var value111 = gradDot(this.p(permX1Y1 + z + 1), localX - 1.0, localY - 1.0, localZ - 1.0);

        var smoothX = VMath.smoothstep(localX);
        var smoothZ = VMath.smoothstep(smoothY);
        var smoothW = VMath.smoothstep(localZ);
        return VMath.lerp3(smoothX, smoothZ, smoothW, value000, value100, value010, value110, value001, value101, value011, value111);
    }
}
