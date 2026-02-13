package rocks.minestom.worldgen;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.biome.Biome;
import rocks.minestom.worldgen.biome.BiomeSource;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.*;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.structure.placement.StructurePlacer;
import rocks.minestom.worldgen.surface.BiomeResolver;
import rocks.minestom.worldgen.surface.SurfaceRules;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.util.Arrays;

/**
 * Orchestrates chunk generation by filling biomes, evaluating density to place
 * base blocks and fluids, applying surface rules, and placing features.
 * This is the step that turns abstract noise and biome data into actual blocks
 * in a chunk.
 */
public final class WorldGenerator implements Generator {
    private final NoiseGeneratorSettingsRuntime settings;
    private final BiomeSource biomeSource;
    private final BiomeZoomer biomeZoomer;
    private final BiomeResolver biomeResolver;
    private final FeatureLoader featureLoader;
    private final StructurePlacer structurePlacer;
    private final boolean generateEndStructures;

    public WorldGenerator(NoiseGeneratorSettingsRuntime settings, BiomeSource biomeSource, long biomeZoomSeed,
            BiomeResolver biomeResolver, FeatureLoader featureLoader, StructurePlacer structurePlacer,
            boolean generateEndStructures) {
        this.settings = settings;
        this.biomeSource = biomeSource;
        this.biomeZoomer = new BiomeZoomer(biomeSource, biomeZoomSeed);
        this.biomeResolver = biomeResolver;
        this.featureLoader = featureLoader;
        this.structurePlacer = structurePlacer;
        this.generateEndStructures = generateEndStructures;
    }

    @Override
    public void generate(GenerationUnit unit) {
        var modifier = unit.modifier();
        fillBiomesFromNoise(unit, this.biomeSource, this.settings.minY(), this.settings.maxYInclusive());
        var startX = unit.absoluteStart().blockX();
        var startY = unit.absoluteStart().blockY();
        var startZ = unit.absoluteStart().blockZ();
        var sizeX = unit.size().blockX();
        var sizeZ = unit.size().blockZ();
        var minY = this.settings.minY();
        var maxY = this.settings.maxYInclusive();
        var height = maxY - minY + 1;
        var defaultBlock = this.settings.defaultBlock();

        var terrainGenerator = new TerrainGenerator(this.settings);
        var terrainData = terrainGenerator.generate(unit);
        var surfaceHeights = terrainData.surfaceHeights();
        var waterHeights = terrainData.waterHeights();
        var stoneMask = terrainData.stoneMask();

        var surfaceRule = this.settings.surfaceRule();
        var constantSurface = SurfaceRules.constantBlock(surfaceRule);
        var surfaceContext = new SurfaceRules.Context(
                this.settings.surfaceSystem(),
                this.settings.randomState(),
                this.biomeResolver,
                this.biomeZoomer,
                minY,
                maxY);
        var stoneDepthAbove = new int[height];
        var stoneDepthBelow = new int[height];

        var shouldApplySurface = constantSurface == null || !constantSurface.equals(defaultBlock);
        for (var localX = 0; localX < sizeX; localX++) {
            var blockX = startX + localX;

            for (var localZ = 0; localZ < sizeZ; localZ++) {
                var blockZ = startZ + localZ;
                var surfaceIndex = localX * sizeZ + localZ;
                var maskIndex = surfaceIndex * height;
                var preliminarySurfaceLevel = surfaceHeights[surfaceIndex];
                if (preliminarySurfaceLevel == Integer.MIN_VALUE) {
                    continue;
                }

                if (!shouldApplySurface) {
                    continue;
                }

                Arrays.fill(stoneDepthAbove, 0);
                Arrays.fill(stoneDepthBelow, 0);

                for (var yIndex = height - 1; yIndex >= 0; yIndex--) {
                    if (stoneMask[maskIndex + yIndex] == 0) {
                        continue;
                    }

                    var aboveIndex = yIndex + 1;
                    if (aboveIndex >= height || stoneMask[maskIndex + aboveIndex] == 0) {
                        stoneDepthAbove[yIndex] = 1;
                    } else {
                        stoneDepthAbove[yIndex] = stoneDepthAbove[aboveIndex] + 1;
                    }
                }

                for (var yIndex = 0; yIndex < height; yIndex++) {
                    if (stoneMask[maskIndex + yIndex] == 0) {
                        continue;
                    }

                    var belowIndex = yIndex - 1;
                    if (belowIndex < 0 || stoneMask[maskIndex + belowIndex] == 0) {
                        stoneDepthBelow[yIndex] = 1;
                    } else {
                        stoneDepthBelow[yIndex] = stoneDepthBelow[belowIndex] + 1;
                    }
                }

                var steep = isSteep(surfaceHeights, sizeZ, localX, localZ);
                surfaceContext.updateXZ(blockX, blockZ, preliminarySurfaceLevel, steep, waterHeights[surfaceIndex]);

                for (var yIndex = height - 1; yIndex >= 0; yIndex--) {
                    if (stoneMask[maskIndex + yIndex] == 0) {
                        continue;
                    }

                    var depthAbove = stoneDepthAbove[yIndex];
                    var depthBelow = stoneDepthBelow[yIndex];
                    if (!shouldApplySurfaceRule(yIndex, height, depthAbove, depthBelow)) {
                        continue;
                    }

                    var blockY = minY + yIndex;
                    surfaceContext.updateY(blockY, depthAbove, depthBelow);
                    var newBlock = surfaceRule.tryApply(surfaceContext);
                    if (newBlock != null && !newBlock.equals(defaultBlock)) {
                        modifier.setRelative(localX, blockY - startY, localZ, newBlock);
                    }
                }
            }
        }

        if (this.structurePlacer != null) {
            this.structurePlacer.placeStructures(unit, surfaceHeights, this.biomeZoomer, this.settings);
        }

        if (this.generateEndStructures) {
            this.placeEndPodium(unit, surfaceHeights);
        }

        this.placeFeatures(unit, surfaceHeights, waterHeights);
    }

    @SuppressWarnings("unchecked")
    private void placeFeatures(GenerationUnit unit, int[] surfaceHeights, int[] waterHeights) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var sizeX = unit.size().blockX();
        var sizeZ = unit.size().blockZ();
        var centerLocalX = sizeX / 2;
        var centerLocalZ = sizeZ / 2;
        var centerSurfaceY = surfaceHeights[centerLocalX * sizeZ + centerLocalZ];

        if (centerSurfaceY == Integer.MIN_VALUE) {
            centerSurfaceY = this.settings.seaLevel();
        }

        var biomeKey = this.biomeZoomer.biome(startX + centerLocalX, centerSurfaceY, startZ + centerLocalZ);
        var biomeFeatures = this.featureLoader.getBiomeFeatures(biomeKey);

        var forkPadding = 16;
        var forkStart = new BlockVec(startX - forkPadding, this.settings.minY(), startZ - forkPadding);
        var forkEnd = new BlockVec(startX + sizeX + forkPadding, this.settings.maxYInclusive() + 1,
                startZ + sizeZ + forkPadding);
        var featureUnit = unit.fork(forkStart, forkEnd);
        var levelAdapter = new GenerationUnitAdapter(featureUnit);
        var randomFactory = this.settings.randomState().getOrCreateRandomFactory(Key.key("minecraft:feature"));

        var placementContext = new PlacementContext(
                levelAdapter,
                startX,
                startZ,
                sizeX,
                sizeZ,
                surfaceHeights,
                waterHeights,
                this.settings.minY(),
                this.settings.maxYInclusive(),
                this.settings.seaLevel(),
                this.biomeZoomer,
                biomeKey);

        for (var stepIndex = 0; stepIndex < biomeFeatures.size(); stepIndex++) {
            var step = biomeFeatures.get(stepIndex);

            for (var featureIndex = 0; featureIndex < step.size(); featureIndex++) {
                var placedFeatureKey = step.get(featureIndex);
                var placedFeature = this.featureLoader.getPlacedFeature(placedFeatureKey);
                if (placedFeature == null) {
                    continue;
                }

                var placementRandom = randomFactory
                        .fromHashOf(placedFeatureKey.asString() + ":" + stepIndex + ":" + featureIndex)
                        .forkPositional()
                        .at(startX, 0, startZ);

                var origin = new BlockVec(startX, 0, startZ);
                var placementPositions = placedFeature.getPositions(placementContext, placementRandom, origin);

                for (var position : placementPositions) {
                    if (position.blockY() < this.settings.minY() || position.blockY() > this.settings.maxYInclusive()) {
                        continue;
                    }

                    var featureRandom = randomFactory
                            .fromHashOf(placedFeatureKey.asString() + ":" + stepIndex + ":" + featureIndex)
                            .forkPositional()
                            .at(position.blockX(), position.blockY(), position.blockZ());

                    var configuredFeature = placedFeature.configuredFeature(this.featureLoader);
                    if (configuredFeature == null) {
                        continue;
                    }

                    var context = new FeaturePlaceContext<>(
                            levelAdapter,
                            featureRandom,
                            position,
                            configuredFeature.config(),
                            this.settings.randomState().seed(),
                            this.settings.minY(),
                            this.settings.maxYInclusive());

                    var featureImpl = configuredFeature.feature();
                    if (featureImpl instanceof RandomSelectorFeature randomSelector) {
                        randomSelector.place(context, this.featureLoader);
                    } else {
                        ((Feature) featureImpl).place(context);
                    }
                }
            }
        }
    }

    private static boolean isSteep(int[] surfaceHeights, int sizeZ, int localX, int localZ) {
        var southZ = Math.max(localZ - 1, 0);
        var northZ = Math.min(localZ + 1, sizeZ - 1);
        var southHeight = surfaceHeights[localX * sizeZ + southZ];
        var northHeight = surfaceHeights[localX * sizeZ + northZ];
        if (northHeight >= southHeight + 4) {
            return true;
        }

        var westX = Math.max(localX - 1, 0);
        var eastX = Math.min(localX + 1, surfaceHeights.length / sizeZ - 1);
        var westHeight = surfaceHeights[westX * sizeZ + localZ];
        var eastHeight = surfaceHeights[eastX * sizeZ + localZ];
        return westHeight >= eastHeight + 4;
    }

    private static boolean isEndFeature(Key key) {
        return key.asString().equals("minecraft:end_platform")
                || key.asString().equals("minecraft:end_spike");
    }

    private void placeEndPodium(GenerationUnit unit, int[] surfaceHeights) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var sizeX = unit.size().blockX();
        var sizeZ = unit.size().blockZ();
        if (startX > 0 || startX + sizeX <= 0 || startZ > 0 || startZ + sizeZ <= 0) {
            return;
        }

        var localX = -startX;
        var localZ = -startZ;
        var surfaceY = surfaceHeights[localX * sizeZ + localZ];
        if (surfaceY == Integer.MIN_VALUE) {
            surfaceY = this.settings.seaLevel();
        }

        var forkPadding = 16;
        var forkStart = new BlockVec(-forkPadding, this.settings.minY(), -forkPadding);
        var forkEnd = new BlockVec(forkPadding + 1, this.settings.maxYInclusive() + 1, forkPadding + 1);
        var featureUnit = unit.fork(forkStart, forkEnd);
        var levelAdapter = new GenerationUnitAdapter(featureUnit);
        EndPodiumFeature.place(levelAdapter, new BlockVec(0, surfaceY, 0), false);
    }

    private static boolean shouldApplySurfaceRule(int yIndex, int height, int stoneDepthAbove, int stoneDepthBelow) {
        if (yIndex <= 8 || yIndex >= height - 8) {
            return true;
        }

        return stoneDepthAbove <= 32 || stoneDepthBelow <= 32;
    }

    private static void fillBiomesFromNoise(GenerationUnit unit, BiomeSource biomes, int minY, int maxY) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var endX = startX + unit.size().blockX() - 1;
        var endZ = startZ + unit.size().blockZ() - 1;

        var startQuartX = Math.floorDiv(startX, 4);
        var endQuartX = Math.floorDiv(endX, 4);
        var startQuartZ = Math.floorDiv(startZ, 4);
        var endQuartZ = Math.floorDiv(endZ, 4);
        var startQuartY = Math.floorDiv(minY, 4);
        var endQuartY = Math.floorDiv(maxY, 4);

        for (var quartX = startQuartX; quartX <= endQuartX; quartX++) {
            for (var quartZ = startQuartZ; quartZ <= endQuartZ; quartZ++) {
                for (var quartY = startQuartY; quartY <= endQuartY; quartY++) {
                    var biome = biomes.biome(quartX, quartY, quartZ);
                    var biomeKey = RegistryKey.<Biome>unsafeOf(biome);

                    // Fill all 4x4x4 positions in the biome palette for this quart
                    // This matches Minecraft's fillBiomesFromNoise behavior
                    var baseX = quartX << 2;
                    var baseY = quartY << 2;
                    var baseZ = quartZ << 2;

                    for (var dx = 0; dx < 4; dx++) {
                        for (var dy = 0; dy < 4; dy++) {
                            for (var dz = 0; dz < 4; dz++) {
                                unit.modifier().setBiome(baseX + dx, baseY + dy, baseZ + dz, biomeKey);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void fillBiomes(GenerationUnit unit, BiomeZoomer zoomer, int minY, int maxY) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var endX = startX + unit.size().blockX() - 1;
        var endZ = startZ + unit.size().blockZ() - 1;

        var startQuartX = Math.floorDiv(startX, 4);
        var endQuartX = Math.floorDiv(endX, 4);
        var startQuartZ = Math.floorDiv(startZ, 4);
        var endQuartZ = Math.floorDiv(endZ, 4);
        var startQuartY = Math.floorDiv(minY, 4);
        var endQuartY = Math.floorDiv(maxY, 4);

        for (var quartX = startQuartX; quartX <= endQuartX; quartX++) {
            var centerX = (quartX << 2) + 2;

            for (var quartZ = startQuartZ; quartZ <= endQuartZ; quartZ++) {
                var centerZ = (quartZ << 2) + 2;

                for (var quartY = startQuartY; quartY <= endQuartY; quartY++) {
                    var centerY = (quartY << 2) + 2;
                    var biome = zoomer.biome(centerX, centerY, centerZ);
                    var biomeKey = RegistryKey.<Biome>unsafeOf(biome);
                    unit.modifier().setBiome(centerX, centerY, centerZ, biomeKey);
                }
            }
        }
    }
}
