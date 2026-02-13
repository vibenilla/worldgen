package rocks.minestom.worldgen.feature.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.biome.BiomeZoomer;

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
            Key sourceBiome
    ) {
        this.accessor = accessor;
        this.startX = startX;
        this.startZ = startZ;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.surfaceHeights = surfaceHeights;
        this.waterHeights = waterHeights;
        this.minY = minY;
        this.maxY = maxY;
        this.seaLevel = seaLevel;
        this.biomeZoomer = biomeZoomer;
        this.sourceBiome = sourceBiome;
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
        var localX = blockX - this.startX;
        var localZ = blockZ - this.startZ;
        if (localX >= 0 && localX < this.sizeX && localZ >= 0 && localZ < this.sizeZ) {
            var index = localX * this.sizeZ + localZ;
            var surfaceHeight = this.surfaceHeights[index];
            if (surfaceHeight == Integer.MIN_VALUE) {
                return this.minY;
            }

            return switch (type) {
                case WORLD_SURFACE_WG, WORLD_SURFACE, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES -> surfaceHeight + 1;
                case OCEAN_FLOOR_WG, OCEAN_FLOOR -> {
                    var waterHeight = this.waterHeights[index];
                    if (waterHeight == Integer.MIN_VALUE) {
                        yield surfaceHeight + 1;
                    }

                    yield Math.min(waterHeight + 1, surfaceHeight + 1);
                }
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
