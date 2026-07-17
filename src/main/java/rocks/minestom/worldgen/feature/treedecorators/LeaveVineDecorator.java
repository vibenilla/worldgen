package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;

/**
 * Vanilla's {@code LeaveVineDecorator}: leaves sprout hanging vines on their
 * horizontal sides with the configured probability.
 */
public record LeaveVineDecorator(float probability) implements TreeDecorator {
    public static final Codec<LeaveVineDecorator> CODEC = StructCodec.struct(
            "probability", Codec.FLOAT, LeaveVineDecorator::probability,
            LeaveVineDecorator::new
    );

    @Override
    public void place(TreeDecorator.Context context) {
        var random = context.random();
        for (var leaf : context.leaves()) {
            if (random.nextFloat() < this.probability) {
                var west = leaf.add(-1, 0, 0);
                if (context.isAir(west)) {
                    addHangingVine(west, "east", context);
                }
            }

            if (random.nextFloat() < this.probability) {
                var east = leaf.add(1, 0, 0);
                if (context.isAir(east)) {
                    addHangingVine(east, "west", context);
                }
            }

            if (random.nextFloat() < this.probability) {
                var north = leaf.add(0, 0, -1);
                if (context.isAir(north)) {
                    addHangingVine(north, "south", context);
                }
            }

            if (random.nextFloat() < this.probability) {
                var south = leaf.add(0, 0, 1);
                if (context.isAir(south)) {
                    addHangingVine(south, "north", context);
                }
            }
        }
    }

    private static void addHangingVine(BlockVec position, String directionProperty, TreeDecorator.Context context) {
        context.placeVine(position, directionProperty);

        var remaining = 4;
        for (var below = position.add(0, -1, 0); context.isAir(below) && remaining > 0; remaining--) {
            context.placeVine(below, directionProperty);
            below = below.add(0, -1, 0);
        }
    }
}
