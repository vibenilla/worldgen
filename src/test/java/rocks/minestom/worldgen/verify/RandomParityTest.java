package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares this library's random sources against the unobfuscated vanilla
 * server classes on the test classpath.
 */
final class RandomParityTest {

    @Test
    void xoroshiroSequence() {
        var vanilla = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(123456789L);
        var ours = new XoroshiroRandomSource(123456789L);
        for (var i = 0; i < 1000; i++) {
            assertEquals(vanilla.nextLong(), ours.nextLong(), "nextLong #" + i);
        }
        assertEquals(vanilla.nextInt(37), ours.nextInt(37));
        assertEquals(vanilla.nextDouble(), ours.nextDouble());
        assertEquals(vanilla.nextFloat(), ours.nextFloat());
        assertEquals(vanilla.nextBoolean(), ours.nextBoolean());
    }

    @Test
    void legacySequence() {
        var vanilla = new net.minecraft.world.level.levelgen.LegacyRandomSource(987654321L);
        var ours = new LegacyRandomSource(987654321L);
        for (var i = 0; i < 1000; i++) {
            assertEquals(vanilla.nextLong(), ours.nextLong(), "nextLong #" + i);
        }
        assertEquals(vanilla.nextInt(37), ours.nextInt(37));
        assertEquals(vanilla.nextDouble(), ours.nextDouble());
    }

    @Test
    void worldgenRandomDecorationSeeds() {
        var vanilla = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.XoroshiroRandomSource(0L));
        var ours = new WorldgenRandom(new XoroshiroRandomSource(0L));

        for (var chunkX = -3; chunkX <= 3; chunkX += 3) {
            for (var chunkZ = -3; chunkZ <= 3; chunkZ += 3) {
                var vanillaSeed = vanilla.setDecorationSeed(123456789L, chunkX * 16, chunkZ * 16);
                var ourSeed = ours.setDecorationSeed(123456789L, chunkX * 16, chunkZ * 16);
                assertEquals(vanillaSeed, ourSeed, "decoration seed at " + chunkX + "," + chunkZ);

                for (var step = 0; step < 11; step++) {
                    for (var index = 0; index < 5; index++) {
                        vanilla.setFeatureSeed(vanillaSeed, index, step);
                        ours.setFeatureSeed(ourSeed, index, step);
                        for (var i = 0; i < 20; i++) {
                            assertEquals(vanilla.nextInt(64), ours.nextInt(64),
                                    "feature random step=" + step + " index=" + index + " draw=" + i);
                        }
                        assertEquals(vanilla.nextFloat(), ours.nextFloat());
                        assertEquals(vanilla.nextDouble(), ours.nextDouble());
                    }
                }
            }
        }
    }

    @Test
    void positionalForks() {
        var vanilla = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(42L).forkPositional();
        var ours = new XoroshiroRandomSource(42L).forkPositional();

        var vanillaAt = vanilla.at(10, -20, 30);
        var oursAt = ours.at(10, -20, 30);
        for (var i = 0; i < 100; i++) {
            assertEquals(vanillaAt.nextLong(), oursAt.nextLong());
        }

        var vanillaHash = vanilla.fromHashOf(net.minecraft.resources.Identifier.parse("minecraft:aquifer"));
        var oursHash = ours.fromHashOf("minecraft:aquifer");
        for (var i = 0; i < 100; i++) {
            assertEquals(vanillaHash.nextLong(), oursHash.nextLong(), "fromHashOf draw " + i);
        }
    }
}
