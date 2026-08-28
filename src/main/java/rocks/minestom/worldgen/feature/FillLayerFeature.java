package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.LayerConfiguration;

/**
 * Port of vanilla {@code FillLayerFeature}: fills every air block of a single
 * horizontal layer across the 16x16 origin chunk with the configured state.
 */
public final class FillLayerFeature implements Feature<LayerConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<LayerConfiguration, T> context) {
        var origin = context.origin();
        var config = context.config();
        var level = context.accessor();
        var y = context.minY() + config.height();

        for (var offsetX = 0; offsetX < 16; offsetX++) {
            for (var offsetZ = 0; offsetZ < 16; offsetZ++) {
                var x = origin.blockX() + offsetX;
                var z = origin.blockZ() + offsetZ;
                if (level.getBlock(x, y, z).air()) {
                    level.setBlock(x, y, z, config.state());
                }
            }
        }

        return true;
    }
}
