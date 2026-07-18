package rocks.minestom.worldgen.feature.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;

public final class PlacementContext {
    private final Block.Getter accessor;
    private final int startX;
    private final int startZ;
    private final int sizeX;
    private final int sizeZ;
    private final int[] surfaceHeights;
    private final int[] waterHeights;
    private final int minY;
    private final int maxY;
    private final int seaLevel;
    private final BiomeZoomer biomeZoomer;
    private final Key sourceBiome;
    private final FeatureLoader featureLoader;
    private Key currentFeature;

    public PlacementContext(
            Block.Getter accessor,
            int startX,
            int startZ,
            int sizeX,
            int sizeZ,
            int[] surfaceHeights,
            int[] waterHeights,
            int minY,
            int maxY,
            int seaLevel,
            BiomeZoomer biomeZoomer,
            Key sourceBiome,
            FeatureLoader featureLoader
    ) {
        this.accessor = accessor;
        this.startX = startX;
        this.startZ = startZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.surfaceHeights = surfaceHeights;
        // Structure-phase writes become visible to decoration here, like
        // vanilla features reading the proto-chunk after structure placement.
        rocks.minestom.worldgen.structure.StructureWrites.replay(surfaceHeights, accessor);
        if (accessor instanceof GenerationUnitAdapter adapter) {
            rocks.minestom.worldgen.structure.StructureWrites.captureTerrainLookup(adapter.terrainLookup());
        }
        this.waterHeights = waterHeights;
        this.minY = minY;
        this.maxY = maxY;
        this.seaLevel = seaLevel;
        this.biomeZoomer = biomeZoomer;
        this.sourceBiome = sourceBiome;
        this.featureLoader = featureLoader;
    }

    /**
     * Marks the placed feature currently running through the modifier pipeline,
     * consulted by the biome placement modifier.
     */
    public Key currentFeature() {
        return this.currentFeature;
    }

    public void currentFeature(Key placedFeatureId) {
        this.currentFeature = placedFeatureId;
    }

    /**
     * Vanilla biome filter: the position's biome must list the current placed
     * feature in its generation settings.
     */
    public boolean currentFeatureInBiomeAt(BlockVec position) {
        if (this.featureLoader == null || this.currentFeature == null || this.biomeZoomer == null) {
            return true;
        }
        var biome = this.biomeZoomer.biome(position.blockX(), position.blockY(), position.blockZ());
        return this.featureLoader.biomeHasFeature(biome, this.currentFeature);
    }

    public Block.Getter accessor() {
        return this.accessor;
    }

    public int minY() {
        return this.minY;
    }

    public int maxY() {
        return this.maxY;
    }

    public int seaLevel() {
        return this.seaLevel;
    }

    public Key sourceBiome() {
        return this.sourceBiome;
    }

    public int getHeight(HeightmapType type, int blockX, int blockZ) {
        // Live heightmaps over generated blocks, like vanilla's chunk maps
        // that update while decoration runs. OCEAN_FLOOR counts motion
        // blockers (leaves yes, litter/plants no) while WORLD_SURFACE counts
        // everything non-air, which is what makes the surface-water-depth
        // filter reject tree positions on littered or planted ground.
        if (this.accessor instanceof GenerationUnitAdapter adapter) {
            // The WG heightmaps are frozen post-carver terrain in vanilla
            // (decoration-stage writes stop updating them once a chunk passes
            // CARVERS); only the non-WG maps are live over generated blocks.
            var height = switch (type) {
                case WORLD_SURFACE_WG -> adapter.frozenWorldSurface(blockX, blockZ);
                case OCEAN_FLOOR_WG -> adapter.frozenOceanFloor(blockX, blockZ);
                case WORLD_SURFACE -> adapter.heightmap(GenerationUnitAdapter.HeightmapType.WORLD_SURFACE, blockX, blockZ);
                case OCEAN_FLOOR -> adapter.heightmap(GenerationUnitAdapter.HeightmapType.OCEAN_FLOOR, blockX, blockZ);
                case MOTION_BLOCKING -> adapter.heightmap(GenerationUnitAdapter.HeightmapType.MOTION_BLOCKING, blockX, blockZ);
                case MOTION_BLOCKING_NO_LEAVES -> adapter.heightmap(GenerationUnitAdapter.HeightmapType.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
            };
            if (height != Integer.MAX_VALUE) {
                return height;
            }
        }

        var localX = blockX - this.startX;
        var localZ = blockZ - this.startZ;
        if (localX >= 0 && localX < this.sizeX && localZ >= 0 && localZ < this.sizeZ && this.surfaceHeights != null) {
            var index = localX * this.sizeZ + localZ;
            var surfaceHeight = this.surfaceHeights[index];
            if (surfaceHeight == Integer.MIN_VALUE) {
                return this.minY;
            }

            return switch (type) {
                case WORLD_SURFACE_WG, WORLD_SURFACE, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES -> {
                    var waterHeight = this.waterHeights[index];
                    if (waterHeight != Integer.MIN_VALUE) {
                        yield Math.max(surfaceHeight + 1, waterHeight);
                    }
                    yield surfaceHeight + 1;
                }
                case OCEAN_FLOOR_WG, OCEAN_FLOOR -> surfaceHeight + 1;
            };
        }

        return this.seaLevel + 1;
    }



    public Key biomeAt(BlockVec position) {
        return this.biomeZoomer.biome(position.blockX(), position.blockY(), position.blockZ());
    }

    public boolean inWorldBounds(BlockVec position) {
        return position.blockY() >= this.minY && position.blockY() <= this.maxY;
    }

    public enum HeightmapType {
        WORLD_SURFACE_WG,
        WORLD_SURFACE,
        OCEAN_FLOOR_WG,
        OCEAN_FLOOR,
        MOTION_BLOCKING,
        MOTION_BLOCKING_NO_LEAVES;

        public static HeightmapType fromString(String value) {
            return switch (value) {
                case "WORLD_SURFACE_WG" -> WORLD_SURFACE_WG;
                case "WORLD_SURFACE" -> WORLD_SURFACE;
                case "OCEAN_FLOOR_WG" -> OCEAN_FLOOR_WG;
                case "OCEAN_FLOOR" -> OCEAN_FLOOR;
                case "MOTION_BLOCKING" -> MOTION_BLOCKING;
                case "MOTION_BLOCKING_NO_LEAVES" -> MOTION_BLOCKING_NO_LEAVES;
                default -> null;
            };
        }
    }
}
