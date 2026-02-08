package rocks.minestom.worldgen.random;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RandomSupport {
    public static final long GOLDEN_RATIO_64 = -7046029254386353131L;
    public static final long SILVER_RATIO_64 = 7640891576956012809L;

    private RandomSupport() {
    }

    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public static Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        var seedLo = seed ^ SILVER_RATIO_64;
        var seedHi = seedLo + GOLDEN_RATIO_64;
        return new Seed128bit(seedLo, seedHi);
    }

    public static Seed128bit upgradeSeedTo128bit(long seed) {
        return upgradeSeedTo128bitUnmixed(seed).mixed();
    }

    public static Seed128bit seedFromHashOf(String value) {
        try {
            var digest = MessageDigest.getInstance("MD5");
            var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var seedLo = readLong(bytes, 0);
            var seedHi = readLong(bytes, 8);
            return new Seed128bit(seedLo, seedHi);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 MessageDigest is not available", exception);
        }
    }

    private static long readLong(byte[] bytes, int offset) {
        return ((long) bytes[offset] & 0xFFL) << 56
                | ((long) bytes[offset + 1] & 0xFFL) << 48
                | ((long) bytes[offset + 2] & 0xFFL) << 40
                | ((long) bytes[offset + 3] & 0xFFL) << 32
                | ((long) bytes[offset + 4] & 0xFFL) << 24
                | ((long) bytes[offset + 5] & 0xFFL) << 16
                | ((long) bytes[offset + 6] & 0xFFL) << 8
                | (long) bytes[offset + 7] & 0xFFL;
    }

    public record Seed128bit(long seedLo, long seedHi) {
        public Seed128bit xor(long xorLo, long xorHi) {
            return new Seed128bit(this.seedLo ^ xorLo, this.seedHi ^ xorHi);
        }

        public Seed128bit mixed() {
            return new Seed128bit(mixStafford13(this.seedLo), mixStafford13(this.seedHi));
        }
    }
}
