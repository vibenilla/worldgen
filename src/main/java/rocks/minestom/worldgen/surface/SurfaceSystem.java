package rocks.minestom.worldgen.surface;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.RandomState;
import rocks.minestom.worldgen.noise.NormalNoise;
import rocks.minestom.worldgen.random.PositionalRandomFactory;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.Arrays;

/**
 * Provides surface context inputs such as terracotta bands, surface depth noise, and randomness.
 * This supplies the shared signals that surface rules rely on to vary materials across
 * mesas, plains, and other biomes without re-sampling noise everywhere.
 */
public final class SurfaceSystem {
    private static final Block WHITE_TERRACOTTA = Block.WHITE_TERRACOTTA;
    private static final Block ORANGE_TERRACOTTA = Block.ORANGE_TERRACOTTA;
    private static final Block TERRACOTTA = Block.TERRACOTTA;
    private static final Block YELLOW_TERRACOTTA = Block.YELLOW_TERRACOTTA;
    private static final Block BROWN_TERRACOTTA = Block.BROWN_TERRACOTTA;
    private static final Block RED_TERRACOTTA = Block.RED_TERRACOTTA;
    private static final Block LIGHT_GRAY_TERRACOTTA = Block.LIGHT_GRAY_TERRACOTTA;

    private final Block defaultBlock;
    private final int seaLevel;
    private final Block[] clayBands;
    private final NormalNoise clayBandsOffsetNoise;
    private final PositionalRandomFactory noiseRandom;
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;

    public SurfaceSystem(RandomState randomState, Block defaultBlock, int seaLevel, PositionalRandomFactory positionalRandomFactory) {
        this.defaultBlock = defaultBlock;
        this.seaLevel = seaLevel;
        this.noiseRandom = positionalRandomFactory;
        this.clayBandsOffsetNoise = randomState.getOrCreateNoise(Key.key("minecraft:clay_bands_offset"));
        this.clayBands = generateBands(positionalRandomFactory.fromHashOf(Key.key("minecraft:clay_bands").asString()));
        this.surfaceNoise = randomState.getOrCreateNoise(Key.key("minecraft:surface"));
        this.surfaceSecondaryNoise = randomState.getOrCreateNoise(Key.key("minecraft:surface_secondary"));
    }

    public Block defaultBlock() {
        return this.defaultBlock;
    }

    public int seaLevel() {
        return this.seaLevel;
    }

    public int getSurfaceDepth(int blockX, int blockZ) {
        var noiseValue = this.surfaceNoise.getValue((double) blockX, 0.0D, (double) blockZ);
        return (int) (noiseValue * 2.75D + 3.0D + this.noiseRandom.at(blockX, 0, blockZ).nextDouble() * 0.25D);
    }

    public double getSurfaceSecondary(int blockX, int blockZ) {
        return this.surfaceSecondaryNoise.getValue((double) blockX, 0.0D, (double) blockZ);
    }

    public Block getBand(int blockX, int blockY, int blockZ) {
        var offset = (int) Math.round(this.clayBandsOffsetNoise.getValue((double) blockX, 0.0D, (double) blockZ) * 4.0D);
        var index = (blockY + offset + this.clayBands.length) % this.clayBands.length;
        return this.clayBands[index];
    }

    private static Block[] generateBands(RandomSource randomSource) {
        var bands = new Block[192];
        Arrays.fill(bands, TERRACOTTA);

        for (var index = 0; index < bands.length; index += randomSource.nextInt(5) + 1) {
            if (index < bands.length) {
                bands[index] = ORANGE_TERRACOTTA;
            }
        }

        makeBands(randomSource, bands, 1, YELLOW_TERRACOTTA);
        makeBands(randomSource, bands, 2, BROWN_TERRACOTTA);
        makeBands(randomSource, bands, 1, RED_TERRACOTTA);

        var whiteTerracottaBands = nextIntBetweenInclusive(randomSource, 9, 15);
        var whiteTerracottaBandCount = 0;

        for (var index = 0; whiteTerracottaBandCount < whiteTerracottaBands && index < bands.length; index += randomSource.nextInt(16) + 4) {
            bands[index] = WHITE_TERRACOTTA;

            if (index - 1 > 0 && randomSource.nextBoolean()) {
                bands[index - 1] = LIGHT_GRAY_TERRACOTTA;
            }

            if (index + 1 < bands.length && randomSource.nextBoolean()) {
                bands[index + 1] = LIGHT_GRAY_TERRACOTTA;
            }

            whiteTerracottaBandCount++;
        }

        return bands;
    }

    private static void makeBands(RandomSource randomSource, Block[] bands, int spread, Block block) {
        var bandCount = nextIntBetweenInclusive(randomSource, 6, 15);

        for (var index = 0; index < bandCount; index++) {
            var length = spread + randomSource.nextInt(3);
            var start = randomSource.nextInt(bands.length);

            for (var offset = 0; start + offset < bands.length && offset < length; offset++) {
                bands[start + offset] = block;
            }
        }
    }

    private static int nextIntBetweenInclusive(RandomSource randomSource, int minInclusive, int maxInclusive) {
        return minInclusive + randomSource.nextInt(maxInclusive - minInclusive + 1);
    }
}
