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

    /** Vanilla's {@code BubbleColumnBlock.updateColumn}, applied after {@link #place} finishes. */
    public static <T extends Block.Getter & Block.Setter> void convertBubbleColumnsAfterPlacement(
            T level, FeaturePlaceContext<UnderwaterMagmaConfiguration, T> context) {
        var origin = context.origin();
        var config = context.config();

        var column = Column.scan(level, origin, config.floorSearchRange(),
                block -> block.compare(Block.WATER),
                block -> !block.compare(Block.WATER));
        if (column.isEmpty() || column.get().floor().isEmpty()) {
            return;
        }

        var floorY = column.get().floor().getAsInt();
        var radius = config.placementRadiusAroundFloor();
        var minX = origin.blockX() - radius;
        var maxX = origin.blockX() + radius;
        var minY = floorY - radius;
        var maxY = floorY + radius;
        var minZ = origin.blockZ() - radius;
        var maxZ = origin.blockZ() + radius;

        for (var z = minZ; z <= maxZ; z++) {
            for (var x = minX; x <= maxX; x++) {
                for (var y = minY; y <= maxY; y++) {
                    if (level.getBlock(x, y, z).compare(Block.MAGMA_BLOCK)) {
                        convertWaterAboveToBubbleColumn(level, x, y, z, context.maxY());
                    }
                }
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> boolean isValidPlacement(T level, int x, int y, int z) {
        var block = level.getBlock(x, y, z);
        if (block.air() || block.compare(Block.WATER)) {
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
        return !level.getBlock(x, y, z).solid();
    }

    /**
     * Port of vanilla's {@code BubbleColumnBlock.updateColumn}: a magma block
     * placed under a water source turns the water above it (and every water
     * source stacked above that) into a downward-dragging bubble column.
     */
    private static <T extends Block.Getter & Block.Setter> void convertWaterAboveToBubbleColumn(T level, int x, int y, int z, int maxY) {
        var bubbleColumn = Block.BUBBLE_COLUMN.withProperty("drag", "true");
        var aboveY = y + 1;
        while (aboveY <= maxY && isWaterSource(level.getBlock(x, aboveY, z))) {
            level.setBlock(x, aboveY, z, bubbleColumn);
            aboveY++;
        }
    }

    private static boolean isWaterSource(Block block) {
        return block.compare(Block.WATER) && "0".equals(block.getProperty("level"));
    }
}
