package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.UnderwaterMagmaConfiguration;

/**
 * Port of vanilla's {@code UnderwaterMagmaFeature}: finds the water/floor
 * boundary below the origin and rolls magma blocks into a cube around it,
 * keeping only fully buried candidates (no exposed face towards open space).
 */
public final class UnderwaterMagmaFeature implements Feature<UnderwaterMagmaConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<UnderwaterMagmaConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var config = context.config();
        var random = context.random();

        var column = Column.scan(level, origin, config.floorSearchRange(),
                block -> block.compare(Block.WATER),
                block -> !block.compare(Block.WATER));
        if (column.isEmpty() || column.get().floor().isEmpty()) {
            return false;
        }

        var floorY = column.get().floor().getAsInt();
        var radius = config.placementRadiusAroundFloor();
        var minX = origin.blockX() - radius;
        var maxX = origin.blockX() + radius;
        var minY = floorY - radius;
        var maxY = floorY + radius;
        var minZ = origin.blockZ() - radius;
        var maxZ = origin.blockZ() + radius;

        var placedAny = false;
        for (var z = minZ; z <= maxZ; z++) {
            for (var y = minY; y <= maxY; y++) {
                for (var x = minX; x <= maxX; x++) {
                    if (random.nextFloat() < config.placementProbabilityPerValidPosition() && isValidPlacement(level, x, y, z)) {
                        level.setBlock(x, y, z, Block.MAGMA_BLOCK);
                        placedAny = true;
                    }
                }
            }
        }

        return placedAny;
    }

    private static <T extends Block.Getter & Block.Setter> boolean isValidPlacement(T level, int x, int y, int z) {
        var block = level.getBlock(x, y, z);
        if (block.isAir() || block.compare(Block.WATER)) {
            return false;
        }

        if (isVisibleFromOutside(level, x, y - 1, z)) {
            return false;
        }

        for (var direction : Direction.HORIZONTAL) {
            if (isVisibleFromOutside(level, x + direction.stepX(), y, z + direction.stepZ())) {
                return false;
            }
        }

        return true;
    }

    private static boolean isVisibleFromOutside(Block.Getter level, int x, int y, int z) {
        return !level.getBlock(x, y, z).registry().isSolid();
    }
}
