package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;

public final class BiomeZoomer {
    private static final long LCG_MULTIPLIER = 6364136223846793005L;
    private static final long LCG_INCREMENT = 1442695040888963407L;

    private final BiomeSource source;
    private final long zoomSeed;

    public BiomeZoomer(BiomeSource source, long zoomSeed) {
        this.source = source;
        this.zoomSeed = zoomSeed;
    }

    public static long obfuscateSeed(long seed) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN).putLong(seed).array();
            var hash = digest.digest(bytes);
            return toLittleEndianLong(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to obfuscate world seed", exception);
        }
    }

    public Key biome(int blockX, int blockY, int blockZ) {
        var x = blockX - 2;
        var y = blockY - 2;
        var z = blockZ - 2;
        var quartX = x >> 2;
        var quartY = y >> 2;
        var quartZ = z >> 2;
        var localX = (double) (x & 3) / 4.0D;
        var localY = (double) (y & 3) / 4.0D;
        var localZ = (double) (z & 3) / 4.0D;

        var bestCorner = 0;
        var bestDistance = Double.POSITIVE_INFINITY;

        for (var corner = 0; corner < 8; corner++) {
            var xCorner = (corner & 4) == 0;
            var yCorner = (corner & 2) == 0;
            var zCorner = (corner & 1) == 0;
            var sampleQuartX = xCorner ? quartX : quartX + 1;
            var sampleQuartY = yCorner ? quartY : quartY + 1;
            var sampleQuartZ = zCorner ? quartZ : quartZ + 1;
            var dx = xCorner ? localX : localX - 1.0D;
            var dy = yCorner ? localY : localY - 1.0D;
            var dz = zCorner ? localZ : localZ - 1.0D;
            var distance = fiddledDistance(this.zoomSeed, sampleQuartX, sampleQuartY, sampleQuartZ, dx, dy, dz);
            if (distance < bestDistance) {
                bestCorner = corner;
                bestDistance = distance;
            }
        }

        var finalQuartX = (bestCorner & 4) == 0 ? quartX : quartX + 1;
        var finalQuartY = (bestCorner & 2) == 0 ? quartY : quartY + 1;
        var finalQuartZ = (bestCorner & 1) == 0 ? quartZ : quartZ + 1;
        return this.source.biome(finalQuartX, finalQuartY, finalQuartZ);
    }

    private static double fiddledDistance(long seed, int quartX, int quartY, int quartZ, double dx, double dy, double dz) {
        var state = lcgNext(seed, quartX);
        state = lcgNext(state, quartY);
        state = lcgNext(state, quartZ);
        state = lcgNext(state, quartX);
        state = lcgNext(state, quartY);
        state = lcgNext(state, quartZ);
        var offsetX = fiddle(state);
        state = lcgNext(state, seed);
        var offsetY = fiddle(state);
        state = lcgNext(state, seed);
        var offsetZ = fiddle(state);
        return square(dz + offsetZ) + square(dy + offsetY) + square(dx + offsetX);
    }

    private static long lcgNext(long state, long salt) {
        state *= state * LCG_MULTIPLIER + LCG_INCREMENT;
        return state + salt;
    }

    private static double fiddle(long value) {
        var fraction = (double) floorMod(value >> 24, 1024L) / 1024.0D;
        return (fraction - 0.5D) * 0.9D;
    }

    private static long floorMod(long value, long mod) {
        var result = value % mod;
        return result >= 0L ? result : result + mod;
    }

    private static double square(double value) {
        return value * value;
    }

    private static long toLittleEndianLong(byte[] hash) {
        if (hash.length < Long.BYTES) {
            throw new IllegalArgumentException("Hash too short: " + hash.length);
        }

        var result = 0L;
        for (var index = 0; index < Long.BYTES; index++) {
            result |= (hash[index] & 0xFFL) << (index * 8);
        }
        return result;
    }
}

