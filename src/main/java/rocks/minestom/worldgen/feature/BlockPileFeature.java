package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.BlockPileConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public final class BlockPileFeature implements Feature<BlockPileConfiguration> {

    public BlockPileFeature() {
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<BlockPileConfiguration, T> context) {
        var origin = context.origin();
        if (origin.y() < context.minY() + 5) {
            return false;
        }

        var random = context.random();
        var config = context.config();
        var radiusX = 2 + random.nextInt(2);
        var radiusZ = 2 + random.nextInt(2);

        var originX = origin.x();
        var originZ = origin.z();

        for (var offsetX = -radiusX; offsetX <= radiusX; offsetX++) {
            for (var offsetZ = -radiusZ; offsetZ <= radiusZ; offsetZ++) {
                for (var offsetY = 0; offsetY <= 1; offsetY++) {
                    var targetPosition = origin.add(offsetX, offsetY, offsetZ);
                    var deltaX = originX - targetPosition.x();
                    var deltaZ = originZ - targetPosition.z();

                    var distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                    var randomThreshold = random.nextFloat() * 10.0F - random.nextFloat() * 6.0F;
                    if ((float) distanceSquared <= randomThreshold || random.nextFloat() < 0.031F) {
                        this.tryPlaceBlock(context.accessor(), targetPosition, random, config);
                    }
                }
            }
        }

        return true;
    }

    private boolean mayPlaceOn(Block.Getter accessor, BlockVec position, RandomSource random) {
        var belowPosition = position.sub(0, 1, 0);
        var belowBlock = accessor.getBlock(belowPosition);
        if (belowBlock.compare(Block.DIRT_PATH)) {
            return random.nextBoolean();
        }

        return belowBlock.registry().isSolid();
    }

    private <T extends Block.Getter & Block.Setter> void tryPlaceBlock(
            T accessor,
            BlockVec position,
            RandomSource random,
            BlockPileConfiguration config
    ) {
        var currentBlock = accessor.getBlock(position);
        if (!currentBlock.isAir()) {
            return;
        }

        if (!this.mayPlaceOn(accessor, position, random)) {
            return;
        }

        var block = config.stateProvider().getState(random, position);
        accessor.setBlock(position, block);
    }
}
