package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Exact port of vanilla {@code VoidStartPlatformFeature}, the obsidian-free
 * stone platform generated around the end spawn point when a world has no
 * natural terrain to stand on. The platform is centered on world x8 z8 at
 * three blocks above the origin's height, forming a square with a
 * checkerboard (Chebyshev) radius of sixteen blocks; every block within that
 * radius is stone except for the single cobblestone block at the exact
 * center. Only the chunk containing the placement origin is filled per
 * call, and placement is skipped entirely once that chunk is more than one
 * chunk away from the platform's own chunk.
 */
public final class VoidStartPlatformFeature implements Feature<NoneFeatureConfiguration> {
    private static final int PLATFORM_OFFSET_X = 8;
    private static final int PLATFORM_OFFSET_Y = 3;
    private static final int PLATFORM_OFFSET_Z = 8;
    private static final int PLATFORM_ORIGIN_CHUNK_X = Math.floorDiv(PLATFORM_OFFSET_X, 16);
    private static final int PLATFORM_ORIGIN_CHUNK_Z = Math.floorDiv(PLATFORM_OFFSET_Z, 16);
    private static final int PLATFORM_RADIUS = 16;

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var currentChunkX = Math.floorDiv(origin.blockX(), 16);
        var currentChunkZ = Math.floorDiv(origin.blockZ(), 16);
        if (checkerboardDistance(currentChunkX, currentChunkZ, PLATFORM_ORIGIN_CHUNK_X, PLATFORM_ORIGIN_CHUNK_Z) > 1) {
            return true;
        }

        var platformOriginX = PLATFORM_OFFSET_X;
        var platformOriginY = origin.blockY() + PLATFORM_OFFSET_Y;
        var platformOriginZ = PLATFORM_OFFSET_Z;
        var minBlockX = currentChunkX * 16;
        var maxBlockX = minBlockX + 15;
        var minBlockZ = currentChunkZ * 16;
        var maxBlockZ = minBlockZ + 15;

        for (var z = minBlockZ; z <= maxBlockZ; z++) {
            for (var x = minBlockX; x <= maxBlockX; x++) {
                if (checkerboardDistance(platformOriginX, platformOriginZ, x, z) <= PLATFORM_RADIUS) {
                    var isCenter = x == platformOriginX && z == platformOriginZ;
                    level.setBlock(x, platformOriginY, z, isCenter ? Block.COBBLESTONE : Block.STONE);
                }
            }
        }

        return true;
    }

    private static int checkerboardDistance(int xa, int za, int xb, int zb) {
        return Math.max(Math.abs(xa - xb), Math.abs(za - zb));
    }
}
