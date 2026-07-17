package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.BlockColumnConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementContext;

/**
 * Port of vanilla {@code BlockColumnFeature}: layered columns like cave vines,
 * basalt pillars, and twisting vines growing in a fixed direction.
 */
public final class BlockColumnFeature implements Feature<BlockColumnConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<BlockColumnConfiguration, T> context) {
        var level = context.accessor();
        var config = context.config();
        var random = context.random();
        var layerCount = config.layers().size();
        var layerHeights = new int[layerCount];
        var totalHeight = 0;

        for (var index = 0; index < layerCount; index++) {
            layerHeights[index] = config.layers().get(index).height().sample(random);
            totalHeight += layerHeights[index];
        }

        if (totalHeight == 0) {
            return false;
        }

        var predicateContext = new PlacementContext(
                level, 0, 0, 0, 0, null, null,
                context.minY(), context.maxY(), 0, null, null, null);

        var origin = context.origin();
        var nextX = origin.blockX();
        var nextY = origin.blockY() + config.directionY();
        var nextZ = origin.blockZ();
        for (var step = 0; step < totalHeight; step++) {
            if (!config.allowedPlacement().test(predicateContext, new BlockVec(nextX, nextY, nextZ))) {
                truncate(layerHeights, totalHeight, step, config.prioritizeTip());
                break;
            }

            nextY += config.directionY();
        }

        var placeX = origin.blockX();
        var placeY = origin.blockY();
        var placeZ = origin.blockZ();
        for (var index = 0; index < layerCount; index++) {
            var count = layerHeights[index];
            if (count == 0) {
                continue;
            }

            var layer = config.layers().get(index);
            for (var step = 0; step < count; step++) {
                var position = new BlockVec(placeX, placeY, placeZ);
                level.setBlock(position, layer.state().getState(level, random, position));
                placeY += config.directionY();
            }
        }

        return true;
    }

    private static void truncate(int[] layerHeights, int totalHeight, int newHeight, boolean prioritizeTip) {
        var amountToRemove = totalHeight - newHeight;
        var direction = prioritizeTip ? 1 : -1;
        var start = prioritizeTip ? 0 : layerHeights.length - 1;
        var end = prioritizeTip ? layerHeights.length : -1;

        for (var index = start; index != end && amountToRemove > 0; index += direction) {
            var thisLayer = layerHeights[index];
            var toRemoveFromLayer = Math.min(thisLayer, amountToRemove);
            amountToRemove -= toRemoveFromLayer;
            layerHeights[index] -= toRemoveFromLayer;
        }
    }
}
