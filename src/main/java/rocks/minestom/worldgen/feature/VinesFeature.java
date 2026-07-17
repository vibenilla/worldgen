package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Port of vanilla {@code VinesFeature}: places a single vine block at the
 * origin, attached to the first acceptable neighbouring face.
 */
public final class VinesFeature implements Feature<NoneFeatureConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();

        if (!level.getBlock(origin).isAir()) {
            return false;
        }

        for (var direction : Direction.values()) {
            if (direction != Direction.DOWN && isAcceptableNeighbour(level, direction.relative(origin))) {
                level.setBlock(origin, Block.VINE.withProperty(direction.serializedName(), "true"));
                return true;
            }
        }

        return false;
    }

    /**
     * Vanilla {@code VineBlock.isAcceptableNeighbour} (MultifaceBlock.canAttachTo):
     * the neighbour must expose a full face toward the vine. Solid blocks
     * approximate it (leaves qualify in vanilla via their full collision face).
     */
    private static boolean isAcceptableNeighbour(Block.Getter level, BlockVec position) {
        return level.getBlock(position).registry().isSolid();
    }
}
