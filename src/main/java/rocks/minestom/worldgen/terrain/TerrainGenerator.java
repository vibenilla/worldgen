package rocks.minestom.worldgen.terrain;

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
    private Aquifer aquifer;

    public TerrainGenerator(NoiseGeneratorSettingsRuntime settings) {
        this.settings = settings;
    }

    /**
     * The aquifer built for the last {@link #generate(int, int)} call. Carving
     * reuses it (with its position caches) the same way vanilla shares the
     * chunk's NoiseChunk aquifer between the noise fill and the carvers stage.
     */
    public Aquifer aquifer() {
        return this.aquifer;
    }

    /**
     * {@link #generate(int, int, Beardifier)} without structure terrain
     * adaptation - the un-bearded terrain vanilla exposes before the noise
     * stage (structure start height probes, tests).
     */
    public TerrainData generate(int chunkX, int chunkZ) {
        return this.generate(chunkX, chunkZ, Beardifier.EMPTY);
    }

    /**
     * Pure function of the chunk position: computes base terrain (density, aquifers,
     * ore veins) into a {@link TerrainData} without touching any generation unit,
     * so results can be memoized and neighbor chunks queried during decoration.
     * The beardifier contribution is added per block to the interpolated density
     * before the solid/air decision, matching vanilla's
     * {@code cacheAllInCell(add(finalDensity, beardifier))} filler.
     */
    public TerrainData generate(int chunkX, int chunkZ, Beardifier beardifier) {
        var startX = chunkX * 16;
        var startZ = chunkZ * 16;
        var sizeX = 16;
        var sizeZ = 16;

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
        var blocks = data.blocks();

        // Initialize NoiseChunk for efficient interpolation
        var noiseChunk = new NoiseChunk(startX, startZ, cellWidth, cellHeight, minY, height,
                this.settings.finalDensity(), this.settings.preliminarySurfaceLevel());
        var cellCountXZ = noiseChunk.cellCountXZ();
        var cellCountY = noiseChunk.cellCountY();
        var minCellY = noiseChunk.minCellY();

        // Aquifer + ore veins must wrap their density functions through the NoiseChunk
        // (matching vanilla router.mapAll(this::wrap)) before interpolation starts so
        // interpolated channels (vein_toggle/vein_ridged/vein_gap) join the fill loop.
        var randomState = this.settings.randomState();
        var fluidPicker = Aquifer.createGlobalFluidPicker(seaLevel, defaultFluid);
        Aquifer aquifer;
        if (this.settings.aquifersEnabled()) {
            var climateSampler = this.settings.climateSampler();
            aquifer = Aquifer.create(
                    noiseChunk,
                    startX,
                    startZ,
                    noiseChunk.wrap(this.settings.barrier()),
                    noiseChunk.wrap(this.settings.fluidLevelFloodedness()),
                    noiseChunk.wrap(this.settings.fluidLevelSpread()),
                    noiseChunk.wrap(this.settings.lava()),
                    noiseChunk.wrap(climateSampler.erosion()),
                    noiseChunk.wrap(climateSampler.depth()),
                    randomState.aquiferRandom(),
                    minY,
                    height,
                    fluidPicker);
        } else {
            aquifer = Aquifer.createDisabled(fluidPicker);
        }
        this.aquifer = aquifer;
        var oreVeinifier = this.settings.oreVeinsEnabled()
                ? OreVeinifier.create(
                        noiseChunk.wrap(this.settings.veinToggle()),
                        noiseChunk.wrap(this.settings.veinRidged()),
                        noiseChunk.wrap(this.settings.veinGap()),
                        randomState.oreRandom())
                : null;

        noiseChunk.initializeForFirstCellX();

        // --- The Noise Loop ---
        // Iterate over 'Cells' (the optimization unit)
        for (var cellOffsetX = 0; cellOffsetX < cellCountXZ; cellOffsetX++) {
            noiseChunk.advanceCellX(cellOffsetX);

            for (var cellOffsetZ = 0; cellOffsetZ < cellCountXZ; cellOffsetZ++) {

                // Iterate cells top-to-bottom for heightmap tracking
                for (var cellOffsetY = cellCountY - 1; cellOffsetY >= 0; cellOffsetY--) {
                    noiseChunk.selectCellYZ(cellOffsetY, cellOffsetZ);

                    // Iterate blocks WITHIN the cell
                    for (var inCellY = cellHeight - 1; inCellY >= 0; inCellY--) {
                        var blockY = (minCellY + cellOffsetY) * cellHeight + inCellY;
                        if (blockY < minY || blockY > maxY) {
                            continue;
                        }

                        var deltaY = (double) inCellY / (double) cellHeight;
                        noiseChunk.updateForY(blockY, deltaY);

                        for (var inCellX = 0; inCellX < cellWidth; inCellX++) {
                            var blockX = startX + cellOffsetX * cellWidth + inCellX;
                            var localX = blockX - startX;
                            var deltaX = (double) inCellX / (double) cellWidth;
                            noiseChunk.updateForX(blockX, deltaX);

                            for (var inCellZ = 0; inCellZ < cellWidth; inCellZ++) {
                                var blockZ = startZ + cellOffsetZ * cellWidth + inCellZ;
                                var localZ = blockZ - startZ;
                                var deltaZ = (double) inCellZ / (double) cellWidth;
                                noiseChunk.updateForZ(blockZ, deltaZ);

                                var density = noiseChunk.getInterpolatedDensity()
                                        + beardifier.compute(blockX, blockY, blockZ);
                                var densityProbe = System.getProperty("worldgen.densityProbe");
                                if (densityProbe != null && densityProbe.equals(blockX + "," + blockZ)) {
                                    System.out.println("DENSITY " + blockX + " " + blockY + " " + blockZ + " " + density);
                                }
                                var surfaceIndex = localX * sizeZ + localZ;
                                var yIndex = blockY - minY;
                                var maskIndex = surfaceIndex * height;

                                // Vanilla block state chain: the aquifer decides the substance
                                // for non-solid density (air/water/lava, or null for a pressure
                                // barrier turned solid); solid positions may be overridden by
                                // ore veins, otherwise fall back to the default block.
                                var state = aquifer.computeSubstance(noiseChunk, density);
                                if (state == null) {
                                    // Solid Ground
                                    var veinState = oreVeinifier != null ? oreVeinifier.calculate(noiseChunk) : null;
                                    if (veinState != null) {
                                        stoneMask[maskIndex + yIndex] = TerrainData.SOLID_OTHER;
                                        blocks[maskIndex + yIndex] = veinState;
                                    } else {
                                        stoneMask[maskIndex + yIndex] = TerrainData.SOLID;
                                        blocks[maskIndex + yIndex] = defaultBlock;
                                    }

                                    // Capture surface height (first solid from top)
                                    if (surfaceHeights[surfaceIndex] == Integer.MIN_VALUE) {
                                        surfaceHeights[surfaceIndex] = blockY;
                                    }
                                } else if (!state.isAir()) {
                                    // Ocean/Aquifer Liquid
                                    stoneMask[maskIndex + yIndex] = TerrainData.FLUID;
                                    blocks[maskIndex + yIndex] = state;
                                    if (aquifer.shouldScheduleFluidUpdate() && !state.isAir()) {
                                        data.fluidTicks().add(new net.minestom.server.coordinate.BlockVec(blockX, blockY, blockZ));
                                    }

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
