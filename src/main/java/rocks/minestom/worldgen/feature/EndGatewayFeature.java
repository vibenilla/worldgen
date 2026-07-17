package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.EndGatewayConfiguration;

/**
 * Exact port of vanilla {@code EndGatewayFeature}. Carves a three by five by
 * three frame centered on the origin: the center block becomes an end
 * gateway, the four horizontal and vertical bedrock struts running through
 * the center become bedrock, and the remaining corners and the middle
 * horizontal ring become air. Vanilla additionally stores the configured
 * exit position and exactness flag on the end gateway block entity created
 * at the center block so teleporting entities can be routed to another
 * gateway or the overworld spawn; this codebase has no block entity model,
 * so {@link EndGatewayConfiguration#exit()} and
 * {@link EndGatewayConfiguration#exact()} are read by callers directly
 * rather than being attached to the placed block.
 */
public final class EndGatewayFeature implements Feature<EndGatewayConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<EndGatewayConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var originX = origin.blockX();
        var originY = origin.blockY();
        var originZ = origin.blockZ();

        for (var dx = -1; dx <= 1; dx++) {
            for (var dy = -2; dy <= 2; dy++) {
                for (var dz = -1; dz <= 1; dz++) {
                    var sameX = dx == 0;
                    var sameY = dy == 0;
                    var sameZ = dz == 0;
                    var end = Math.abs(dy) == 2;
                    var x = originX + dx;
                    var y = originY + dy;
                    var z = originZ + dz;

                    if (sameX && sameY && sameZ) {
                        level.setBlock(x, y, z, Block.END_GATEWAY);
                    } else if (sameY) {
                        level.setBlock(x, y, z, Block.AIR);
                    } else if (end && sameX && sameZ) {
                        level.setBlock(x, y, z, Block.BEDROCK);
                    } else if ((sameX || sameZ) && !end) {
                        level.setBlock(x, y, z, Block.BEDROCK);
                    } else {
                        level.setBlock(x, y, z, Block.AIR);
                    }
                }
            }
        }

        return true;
    }
}
