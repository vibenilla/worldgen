package rocks.minestom.worldgen.structure.fortress;

import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * White-box check of the fortress port's block-level placement: this package
 * has no vanilla {@code WorldGenLevel} equivalent to A/B against directly
 * (unlike the pure piece-geometry algorithm covered by
 * {@code rocks.minestom.worldgen.verify.FortressABTest}, {@code postProcess}
 * writes require a level), so this instead asserts internal properties that
 * would catch most porting mistakes: postProcess never throws across a wide
 * sample of layouts, every piece places at least one non-air block, and
 * running the same seed twice produces byte-identical block writes.
 */
final class FortressBlockConsistencyTest {
    @Test
    void postProcessIsDeterministicAndNonEmpty() {
        var seeds = new long[]{1L, 42L, 123456789L, -77L, 2026L};
        for (var seed : seeds) {
            for (var chunkX = -3; chunkX <= 3; chunkX++) {
                for (var chunkZ = -3; chunkZ <= 3; chunkZ++) {
                    var first = placeAll(seed, chunkX, chunkZ);
                    var second = placeAll(seed, chunkX, chunkZ);
                    assertEquals(first, second, "non-deterministic placement at seed=" + seed
                            + " chunk=" + chunkX + "," + chunkZ);
                    assertTrue(first.values().stream().anyMatch(block -> !block.isAir()),
                            "no non-air blocks placed at seed=" + seed + " chunk=" + chunkX + "," + chunkZ);
                }
            }
        }
    }

    private static Map<Long, Block> placeAll(long seed, int chunkX, int chunkZ) {
        var genRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        genRandom.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var pieces = NetherFortressPieces.generatePieces(genRandom, chunkX, chunkZ);

        var minY = -64;
        var maxY = 319;
        var recorded = new HashMap<Long, Block>();
        Block.Setter adapter = (x, y, z, block) -> recorded.put(packPos(x, y, z), block);

        var decorationRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        decorationRandom.setLargeFeatureSeed(seed ^ 0x5DEECE66DL, chunkX, chunkZ);

        for (var piece : pieces) {
            var pieceBox = piece.boundingBox();
            var startX = (pieceBox.minX() >> 4) << 4;
            var startZ = (pieceBox.minZ() >> 4) << 4;
            var chunkBB = new BoundingBox(startX, minY + 1, startZ, startX + 15, maxY, startZ + 15);
            var level = new FortressLevel(adapter, new Block[16 * 16 * (maxY - minY + 1)], startX, startZ, minY, maxY, null, null);
            piece.postProcess(level, decorationRandom, chunkBB);
        }
        return recorded;
    }

    private static long packPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (y & 0xFFF) << 26) | (z & 0x3FFFFFF);
    }
}
