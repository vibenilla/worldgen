package rocks.minestom.worldgen.structure.placement;

import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;

/**
 * Port of vanilla {@code StructurePlacement.FrequencyReductionMethod}: gates a
 * placement chunk behind a per-chunk random roll against the set's frequency.
 * All reducers use a legacy random source regardless of the world's random
 * type, matching vanilla.
 */
public enum FrequencyReduction {
    DEFAULT {
        @Override
        public boolean shouldGenerate(long seed, int salt, int chunkX, int chunkZ, float frequency) {
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            // setLargeFeatureWithSalt(seed, salt, chunkX, chunkZ)
            random.setSeed((long) salt * 341873128712L + (long) chunkX * 132897987541L + seed + (long) chunkZ);
            return random.nextFloat() < frequency;
        }
    },
    LEGACY_TYPE_1 {
        @Override
        public boolean shouldGenerate(long seed, int salt, int chunkX, int chunkZ, float frequency) {
            var regionX = chunkX >> 4;
            var regionZ = chunkZ >> 4;
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setSeed(regionX ^ regionZ << 4 ^ seed);
            random.nextInt();
            return random.nextInt((int) (1.0F / frequency)) == 0;
        }
    },
    LEGACY_TYPE_2 {
        @Override
        public boolean shouldGenerate(long seed, int salt, int chunkX, int chunkZ, float frequency) {
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            // setLargeFeatureWithSalt(seed, chunkX, chunkZ, HIGHLY_ARBITRARY_RANDOM_SALT)
            random.setSeed((long) chunkX * 341873128712L + (long) chunkZ * 132897987541L + seed + 10387320L);
            return random.nextFloat() < frequency;
        }
    },
    LEGACY_TYPE_3 {
        @Override
        public boolean shouldGenerate(long seed, int salt, int chunkX, int chunkZ, float frequency) {
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(seed, chunkX, chunkZ);
            return random.nextDouble() < frequency;
        }
    };

    public abstract boolean shouldGenerate(long seed, int salt, int chunkX, int chunkZ, float frequency);

    public static FrequencyReduction fromName(String name) {
        return switch (name) {
            case "legacy_type_1" -> LEGACY_TYPE_1;
            case "legacy_type_2" -> LEGACY_TYPE_2;
            case "legacy_type_3" -> LEGACY_TYPE_3;
            default -> DEFAULT;
        };
    }
}
