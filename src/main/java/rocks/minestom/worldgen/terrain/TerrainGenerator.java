package rocks.minestom.worldgen.terrain;

import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseChunk;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;

/**
 * Handles the base terrain generation phase (The "Noise Phase").
 * This step calculates the shape of the world using perlin noise and fills the
 * chunk
 * with Stone (solid) or Water/Air (fluid/empty).
 * <p>
 * It uses {@link NoiseChunk} to optimize calculations by only processing
 * density at cell corners and interpolating for the blocks in between.
 */
public final class TerrainGenerator {
    private final NoiseGeneratorSettingsRuntime settings;

    public TerrainGenerator(NoiseGeneratorSettingsRuntime settings) {
        this.settings = settings;
    }

    public TerrainData generate(GenerationUnit unit) {
        var startX = unit.absoluteStart().blockX();
        var startY = unit.absoluteStart().blockY();
        var startZ = unit.absoluteStart().blockZ();
        var sizeX = unit.size().blockX();
        var sizeZ = unit.size().blockZ();

        var minY = this.settings.minY();
        var maxY = this.settings.maxYInclusive();
        var height = maxY - minY + 1;

        var seaLevel = this.settings.seaLevel();
        var defaultBlock = this.settings.defaultBlock();
        var defaultFluid = this.settings.defaultFluid();

        var cellWidth = this.settings.cellWidth();
        var cellHeight = this.settings.cellHeight();

        // Prepare output data
        var data = TerrainData.create(sizeX, sizeZ, height);
        var surfaceHeights = data.surfaceHeights();
        var waterHeights = data.waterHeights();
        var stoneMask = data.stoneMask();
        var modifier = unit.modifier();

        // Initialize NoiseChunk for efficient interpolation
        var noiseChunk = new NoiseChunk(startX, startZ, cellWidth, cellHeight, minY, height,
                this.settings.finalDensity());
        var cellCountXZ = noiseChunk.cellCountXZ();
        var cellCountY = noiseChunk.cellCountY();
        var minCellY = noiseChunk.minCellY();

        noiseChunk.initializeForFirstCellX();

        // --- The Noise Loop ---
        // Iterate over 'Cells' (the optimization unit)
        for (int cellOffsetX = 0; cellOffsetX < cellCountXZ; cellOffsetX++) {
            noiseChunk.advanceCellX(cellOffsetX);

            for (int cellOffsetZ = 0; cellOffsetZ < cellCountXZ; cellOffsetZ++) {

                // Iterate cells top-to-bottom for heightmap tracking
                for (int cellOffsetY = cellCountY - 1; cellOffsetY >= 0; cellOffsetY--) {
                    noiseChunk.selectCellYZ(cellOffsetY, cellOffsetZ);

                    // Iterate blocks WITHIN the cell
                    for (int inCellY = cellHeight - 1; inCellY >= 0; inCellY--) {
                        var blockY = (minCellY + cellOffsetY) * cellHeight + inCellY;
                        if (blockY < minY || blockY > maxY) {
                            continue;
                        }

                        var deltaY = (double) inCellY / (double) cellHeight;
                        noiseChunk.updateForY(blockY, deltaY);

                        for (int inCellX = 0; inCellX < cellWidth; inCellX++) {
                            var blockX = startX + cellOffsetX * cellWidth + inCellX;
                            var localX = blockX - startX;
                            var deltaX = (double) inCellX / (double) cellWidth;
                            noiseChunk.updateForX(blockX, deltaX);

                            for (int inCellZ = 0; inCellZ < cellWidth; inCellZ++) {
                                var blockZ = startZ + cellOffsetZ * cellWidth + inCellZ;
                                var localZ = blockZ - startZ;
                                var deltaZ = (double) inCellZ / (double) cellWidth;
                                noiseChunk.updateForZ(blockZ, deltaZ);

                                var density = noiseChunk.getInterpolatedDensity();
                                var surfaceIndex = localX * sizeZ + localZ;
                                var yIndex = blockY - minY;
                                var maskIndex = surfaceIndex * height;

                                if (density > 0.0D) {
                                    // Solid Ground
                                    modifier.setRelative(localX, blockY - startY, localZ, defaultBlock);

                                    // Capture surface height (first solid from top)
                                    if (surfaceHeights[surfaceIndex] == Integer.MIN_VALUE) {
                                        surfaceHeights[surfaceIndex] = blockY;
                                    }
                                    stoneMask[maskIndex + yIndex] = 1;
                                } else if (blockY < seaLevel) {
                                    // Ocean/Liquid
                                    modifier.setRelative(localX, blockY - startY, localZ, defaultFluid);

                                    // Capture water level (first liquid from top)
                                    if (waterHeights[surfaceIndex] == Integer.MIN_VALUE) {
                                        waterHeights[surfaceIndex] = blockY + 1;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // CRITICAL: Must swap slices after finishing a Z-row of cells
            noiseChunk.swapSlices();
        }

        noiseChunk.stopInterpolation();
        return data;
    }
}
