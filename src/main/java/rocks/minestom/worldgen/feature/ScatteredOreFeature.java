package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.OreConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code ScatteredOreFeature}. The random call order matches
 * vanilla exactly, which is required for block-for-block parity.
 */
public final class ScatteredOreFeature implements Feature<OreConfiguration> {
    private static final int MAX_DIST_FROM_ORIGIN = 7;

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<OreConfiguration, T> context) {
        var level = context.accessor();
        var random = context.random();
        var config = context.config();
        var origin = context.origin();
        var numberOfTries = random.nextInt(config.size() + 1);

        for (var i = 0; i < numberOfTries; i++) {
            var targetPos = this.offsetTargetPos(random, origin, Math.min(i, MAX_DIST_FROM_ORIGIN));
            var blockState = level.getBlock(targetPos.blockX(), targetPos.blockY(), targetPos.blockZ());

            for (var targetState : config.targetStates()) {
                if (OreFeature.canPlaceOre(blockState, level, random, config, targetState, targetPos)) {
                    level.setBlock(targetPos.blockX(), targetPos.blockY(), targetPos.blockZ(), targetState.state());
                    break;
                }
            }
        }

        return true;
    }

    private BlockVec offsetTargetPos(RandomSource random, BlockVec origin, int maxDistFromOriginForThisTry) {
        var xd = this.getRandomPlacementInOneAxisRelativeToOrigin(random, maxDistFromOriginForThisTry);
        var yd = this.getRandomPlacementInOneAxisRelativeToOrigin(random, maxDistFromOriginForThisTry);
        var zd = this.getRandomPlacementInOneAxisRelativeToOrigin(random, maxDistFromOriginForThisTry);

        return new BlockVec(origin.blockX() + xd, origin.blockY() + yd, origin.blockZ() + zd);
    }

    private int getRandomPlacementInOneAxisRelativeToOrigin(RandomSource random, int maxDistanceFromOrigin) {
        return Math.round((random.nextFloat() - random.nextFloat()) * maxDistanceFromOrigin);
    }
}
