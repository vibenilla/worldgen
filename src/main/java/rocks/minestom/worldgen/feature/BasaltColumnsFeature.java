package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.ColumnFeatureConfiguration;

import java.util.Set;

/**
 * Port of vanilla {@code BasaltColumnsFeature}: clusters of basalt columns
 * rising out of the lava ocean in basalt deltas. The lava sea level comes from
 * the dimension's generator sea level (32 in the nether).
 */
public final class BasaltColumnsFeature implements Feature<ColumnFeatureConfiguration> {
    /** Vanilla {@code BasaltColumnsFeature.CANNOT_PLACE_ON} (26.2 contents). */
    private static final Set<String> CANNOT_PLACE_ON = Set.of(
            "minecraft:lava", "minecraft:bedrock", "minecraft:magma_block", "minecraft:soul_sand",
            "minecraft:nether_bricks", "minecraft:nether_brick_fence", "minecraft:nether_brick_stairs",
            "minecraft:nether_wart", "minecraft:chest", "minecraft:spawner");

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<ColumnFeatureConfiguration, T> context) {
        var lavaSeaLevel = context.seaLevel();
        var origin = context.origin();
        var level = context.accessor();
        var random = context.random();
        var config = context.config();
        if (!canPlaceAt(level, lavaSeaLevel, origin)) {
            return false;
        }

        var columnHeight = config.height().sample(random);
        var generateClustered = random.nextFloat() < 0.9F;
        var reach = Math.min(columnHeight, generateClustered ? 5 : 8);
        var count = generateClustered ? 50 : 15;
        var placed = false;

        // Vanilla BlockPos.randomBetweenClosed: count positions, each drawing
        // nextInt over the x, y and z spans (the y span is 1 but still draws).
        for (var attempt = 0; attempt < count; attempt++) {
            var x = origin.blockX() - reach + random.nextInt(reach * 2 + 1);
            var y = origin.blockY() + random.nextInt(1);
            var z = origin.blockZ() - reach + random.nextInt(reach * 2 + 1);
            var pos = new BlockVec(x, y, z);
            var blocksToPlaceY = columnHeight - BlockPosIterators.distManhattan(pos, origin);
            if (blocksToPlaceY >= 0) {
                placed |= this.placeColumn(level, lavaSeaLevel, pos, blocksToPlaceY, config.reach().sample(random),
                        context.minY(), context.maxY());
            }
        }

        return placed;
    }

    private <T extends Block.Getter & Block.Setter> boolean placeColumn(T level, int lavaSeaLevel, BlockVec origin,
            int columnHeight, int reach, int minY, int maxY) {
        var placedAny = false;

        // Vanilla BlockPos.betweenClosed over the y-plane: x varies fastest, then z.
        for (var z = origin.blockZ() - reach; z <= origin.blockZ() + reach; z++) {
            for (var x = origin.blockX() - reach; x <= origin.blockX() + reach; x++) {
                var pos = new BlockVec(x, origin.blockY(), z);
                var stepLimit = BlockPosIterators.distManhattan(pos, origin);
                var columnPos = isAirOrLavaOcean(level, lavaSeaLevel, pos)
                        ? findSurface(level, lavaSeaLevel, pos, stepLimit, minY)
                        : findAir(level, pos, stepLimit, maxY);
                if (columnPos == null) {
                    continue;
                }

                var blocksY = columnHeight - stepLimit / 2;

                for (var cursor = columnPos; blocksY >= 0; blocksY--) {
                    if (isAirOrLavaOcean(level, lavaSeaLevel, cursor)) {
                        level.setBlock(cursor, Block.BASALT);
                        cursor = cursor.add(0, 1, 0);
                        placedAny = true;
                    } else {
                        if (!level.getBlock(cursor).compare(Block.BASALT)) {
                            break;
                        }

                        cursor = cursor.add(0, 1, 0);
                    }
                }
            }
        }

        return placedAny;
    }

    private static BlockVec findSurface(Block.Getter level, int lavaSeaLevel, BlockVec cursor, int limit, int minY) {
        while (cursor.blockY() > minY + 1 && limit > 0) {
            limit--;
            if (canPlaceAt(level, lavaSeaLevel, cursor)) {
                return cursor;
            }

            cursor = cursor.add(0, -1, 0);
        }

        return null;
    }

    private static boolean canPlaceAt(Block.Getter level, int lavaSeaLevel, BlockVec pos) {
        if (!isAirOrLavaOcean(level, lavaSeaLevel, pos)) {
            return false;
        }

        var blockState = level.getBlock(pos.add(0, -1, 0));
        return !blockState.isAir() && !CANNOT_PLACE_ON.contains(blockState.name());
    }

    private static BlockVec findAir(Block.Getter level, BlockVec cursor, int limit, int maxY) {
        while (cursor.blockY() <= maxY && limit > 0) {
            limit--;
            var blockState = level.getBlock(cursor);
            if (CANNOT_PLACE_ON.contains(blockState.name())) {
                return null;
            }

            if (blockState.isAir()) {
                return cursor;
            }

            cursor = cursor.add(0, 1, 0);
        }

        return null;
    }

    private static boolean isAirOrLavaOcean(Block.Getter level, int lavaSeaLevel, BlockVec pos) {
        var blockState = level.getBlock(pos);
        return blockState.isAir() || blockState.compare(Block.LAVA) && pos.blockY() <= lavaSeaLevel;
    }
}
