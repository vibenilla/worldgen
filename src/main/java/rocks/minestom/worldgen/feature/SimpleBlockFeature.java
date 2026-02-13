package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SimpleBlockConfiguration;

public final class SimpleBlockFeature implements Feature<SimpleBlockConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SimpleBlockConfiguration, T> context) {
        var targetPosition = context.origin();
        var currentBlock = context.accessor().getBlock(targetPosition);
        if (!currentBlock.isAir() && !currentBlock.compare(Block.WATER) && !currentBlock.compare(Block.LAVA)) {
            return false;
        }

        var toPlace = context.config().toPlace().getState(context.random(), targetPosition);
        context.accessor().setBlock(targetPosition, toPlace);
        return true;
    }
}
