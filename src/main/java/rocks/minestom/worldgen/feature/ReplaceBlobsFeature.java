package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.ReplaceSphereConfiguration;

/**
 * Port of vanilla {@code ReplaceBlobsFeature} (netherrack replace blobs):
 * walks down from the origin to the first target block and replaces target
 * blocks within a random Manhattan-distance blob around it.
 */
public final class ReplaceBlobsFeature implements Feature<ReplaceSphereConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<ReplaceSphereConfiguration, T> context) {
        var config = context.config();
        var level = context.accessor();
        var random = context.random();
        var origin = context.origin();
        var startY = Math.min(Math.max(origin.blockY(), context.minY() + 1), context.maxY());
        var centerPos = findTarget(level, new BlockVec(origin.blockX(), startY, origin.blockZ()),
                config.targetState(), context.minY());
        if (centerPos == null) {
            return false;
        }

        var radiusX = config.radius().sample(random);
        var radiusY = config.radius().sample(random);
        var radiusZ = config.radius().sample(random);
        var maximumRadius = Math.max(radiusX, Math.max(radiusY, radiusZ));
        var replacedAny = false;

        for (var pos : BlockPosIterators.withinManhattan(centerPos, radiusX, radiusY, radiusZ)) {
            if (BlockPosIterators.distManhattan(pos, centerPos) > maximumRadius) {
                break;
            }

            if (level.getBlock(pos).compare(config.targetState())) {
                level.setBlock(pos, config.replaceState());
                replacedAny = true;
            }
        }

        return replacedAny;
    }

    private static BlockVec findTarget(Block.Getter level, BlockVec cursor, Block target, int minY) {
        while (cursor.blockY() > minY + 1) {
            if (level.getBlock(cursor).compare(target)) {
                return cursor;
            }

            cursor = cursor.add(0, -1, 0);
        }

        return null;
    }
}
