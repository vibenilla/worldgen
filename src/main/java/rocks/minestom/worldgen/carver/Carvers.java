package rocks.minestom.worldgen.carver;

import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.biome.CachedBiomeSource;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.surface.BiomeResolver;
import rocks.minestom.worldgen.surface.SurfaceRules;
import rocks.minestom.worldgen.terrain.Aquifer;
import rocks.minestom.worldgen.terrain.TerrainData;

/**
 * Vanilla CARVERS stage (runs after the surface stage): every source chunk in
 * a 17x17 neighborhood seeds each of its biome's carvers with
 * {@code setLargeFeatureSeed(seed + index, sourceX, sourceZ)} and carves the
 * portion of its tunnels that intersects the target chunk. The biome is read
 * at the source chunk's (0, 0) corner quart at y = 0, exactly like vanilla.
 */
public final class Carvers {
    private static final int CHUNK_RANGE = 8;

    private final NoiseGeneratorSettingsRuntime settings;
    private final CachedBiomeSource biomeSource;
    private final BiomeResolver biomeResolver;
    private final BiomeZoomer biomeZoomer;
    private final CarverLoader carverLoader;

    public Carvers(NoiseGeneratorSettingsRuntime settings, CachedBiomeSource biomeSource,
            BiomeResolver biomeResolver, BiomeZoomer biomeZoomer, CarverLoader carverLoader) {
        this.settings = settings;
        this.biomeSource = biomeSource;
        this.biomeResolver = biomeResolver;
        this.biomeZoomer = biomeZoomer;
        this.carverLoader = carverLoader;
    }

    /**
     * Carves the chunk's terrain buffer in place and refreshes the height
     * arrays afterwards. The aquifer must be the one the noise fill used so
     * flooded caves resolve the same fluids as vanilla's shared NoiseChunk
     * aquifer.
     */
    public void applyCarvers(TerrainData data, int chunkX, int chunkZ, Aquifer aquifer) {
        var minY = this.settings.minY();
        var height = this.settings.height();
        var context = new CarvingContext(data, chunkX, chunkZ, minY, height, aquifer, this.topMaterial());
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        var seed = this.settings.randomState().seed();

        for (var offsetX = -CHUNK_RANGE; offsetX <= CHUNK_RANGE; offsetX++) {
            for (var offsetZ = -CHUNK_RANGE; offsetZ <= CHUNK_RANGE; offsetZ++) {
                var sourceChunkX = chunkX + offsetX;
                var sourceChunkZ = chunkZ + offsetZ;
                var biome = this.biomeSource.biome(sourceChunkX << 2, 0, sourceChunkZ << 2);
                var carverIds = this.carverLoader.biomeCarvers(biome);

                for (var index = 0; index < carverIds.size(); index++) {
                    var carver = this.carverLoader.configuredCarver(carverIds.get(index));
                    if (carver == null) {
                        continue;
                    }

                    random.setLargeFeatureSeed(seed + index, sourceChunkX, sourceChunkZ);
                    if (carver.isStartChunk(random)) {
                        carver.carve(context, random, sourceChunkX, sourceChunkZ);
                    }
                }
            }
        }

        recomputeHeights(data, minY, height);
    }

    /**
     * Surface-rule evaluation for a single exposed floor block, mirroring
     * vanilla {@code SurfaceSystem.topMaterial} (stone depths of 1, water at
     * the block above when the carved state is a fluid).
     */
    private CarvingContext.TopMaterial topMaterial() {
        var surfaceRule = this.settings.surfaceRule();
        if (surfaceRule == null) {
            return null;
        }

        var surfaceContext = new SurfaceRules.Context(
                this.settings.surfaceSystem(),
                this.settings.randomState(),
                this.biomeResolver,
                this.biomeZoomer,
                this.settings.preliminarySurfaceLevel(),
                this.settings.minY(),
                this.settings.maxYInclusive());
        return (blockX, blockY, blockZ, underFluid) -> {
            surfaceContext.updateXZ(blockX, blockZ, false);
            surfaceContext.updateY(blockY, 1, 1, underFluid ? blockY + 1 : Integer.MIN_VALUE);
            return surfaceRule.tryApply(surfaceContext);
        };
    }

    /**
     * Re-derives the per-column height arrays (first solid / first fluid from
     * the top) after carving, since features and heightmap consumers read them.
     */
    private static void recomputeHeights(TerrainData data, int minY, int height) {
        var surfaceHeights = data.surfaceHeights();
        var waterHeights = data.waterHeights();
        var stoneMask = data.stoneMask();

        for (var surfaceIndex = 0; surfaceIndex < surfaceHeights.length; surfaceIndex++) {
            var maskIndex = surfaceIndex * height;
            var surfaceHeight = Integer.MIN_VALUE;
            var waterHeight = Integer.MIN_VALUE;
            for (var yIndex = height - 1; yIndex >= 0; yIndex--) {
                var state = stoneMask[maskIndex + yIndex];
                if (surfaceHeight == Integer.MIN_VALUE
                        && (state == TerrainData.SOLID || state == TerrainData.SOLID_OTHER)) {
                    surfaceHeight = minY + yIndex;
                }
                if (waterHeight == Integer.MIN_VALUE && state == TerrainData.FLUID) {
                    waterHeight = minY + yIndex + 1;
                }
                if (surfaceHeight != Integer.MIN_VALUE && waterHeight != Integer.MIN_VALUE) {
                    break;
                }
            }
            surfaceHeights[surfaceIndex] = surfaceHeight;
            waterHeights[surfaceIndex] = waterHeight;
        }
    }
}
