package rocks.minestom.worldgen;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.noise.NormalNoise;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.PositionalRandomFactory;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the per-world random and noise state derived from the seed.
 * This provides shared, deterministic noise and positional randomness so every system
 * samples the same values for the same coordinates without re-creating generators.
 */
public final class RandomState {
    private final DataPack dataPack;
    private final long seed;
    private final boolean legacyRandomSource;
    private final PositionalRandomFactory positionalRandomFactory;
    private final Map<Key, NormalNoise.NoiseParameters> parametersCache;
    private final Map<Key, NormalNoise> noiseCache;
    private final Map<Key, PositionalRandomFactory> randomFactoryCache;

    public RandomState(DataPack dataPack, long seed, boolean legacyRandomSource) {
        this.dataPack = dataPack;
        this.seed = seed;
        this.legacyRandomSource = legacyRandomSource;
        this.positionalRandomFactory = legacyRandomSource ? new LegacyRandomSource(seed).forkPositional() : new XoroshiroRandomSource(seed).forkPositional();
        this.parametersCache = new ConcurrentHashMap<>();
        this.noiseCache = new ConcurrentHashMap<>();
        this.randomFactoryCache = new ConcurrentHashMap<>();
    }

    public boolean legacyRandomSource() {
        return this.legacyRandomSource;
    }

    public long seed() {
        return this.seed;
    }

    public NormalNoise getOrCreateNoise(Key id) {
        return this.noiseCache.computeIfAbsent(id, this::createNoise);
    }

    public PositionalRandomFactory getOrCreateRandomFactory(Key id) {
        return this.randomFactoryCache.computeIfAbsent(id, key -> this.positionalRandomFactory.fromHashOf(key.asString()).forkPositional());
    }

    public RandomSource terrainRandom() {
        if (this.legacyRandomSource) {
            return new LegacyRandomSource(this.seed);
        }
        return this.positionalRandomFactory.fromHashOf(Key.key("minecraft:terrain").asString());
    }

    private NormalNoise createNoise(Key id) {
        if (this.legacyRandomSource) {
            if (id.asString().equals("minecraft:temperature")) {
                return NormalNoise.createLegacyNetherBiome(new LegacyRandomSource(this.seed), new NormalNoise.NoiseParameters(-7, new double[]{1.0, 1.0}));
            }
            if (id.asString().equals("minecraft:vegetation")) {
                return NormalNoise.createLegacyNetherBiome(new LegacyRandomSource(this.seed + 1L), new NormalNoise.NoiseParameters(-7, new double[]{1.0, 1.0}));
            }
            if (id.asString().equals("minecraft:offset")) {
                return NormalNoise.create(this.positionalRandomFactory.fromHashOf(Key.key("minecraft:offset").asString()), new NormalNoise.NoiseParameters(0, new double[]{0.0}));
            }
        }

        var parameters = this.parametersCache.computeIfAbsent(id, this::readNoiseParameters);
        return NormalNoise.create(this.positionalRandomFactory.fromHashOf(id.asString()), parameters);
    }

    private NormalNoise.NoiseParameters readNoiseParameters(Key id) {
        var json = this.dataPack.readNoiseParameters(id);
        var parametersJson = NoiseParameters.CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var amplitudes = new double[parametersJson.amplitudes().size()];

        for (var index = 0; index < parametersJson.amplitudes().size(); index++) {
            amplitudes[index] = parametersJson.amplitudes().get(index);
        }

        return new NormalNoise.NoiseParameters(parametersJson.firstOctave(), amplitudes);
    }
}
