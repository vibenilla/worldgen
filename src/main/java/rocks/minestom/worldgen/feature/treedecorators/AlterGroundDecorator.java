package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

/**
 * Vanilla's {@code AlterGroundDecorator}: replaces the ground around the
 * lowest logs (podzol under spruces) in overlapping circles plus a few random
 * edge circles.
 */
public record AlterGroundDecorator(BlockStateProvider provider) implements TreeDecorator {
    public static final Codec<AlterGroundDecorator> CODEC = StructCodec.struct(
            "provider", BlockStateProviders.CODEC, AlterGroundDecorator::provider,
            AlterGroundDecorator::new
    );

    @Override
    public void place(TreeDecorator.Context context) {
        var lowestTrunkOrRoot = PlaceOnGroundDecorator.getLowestTrunkOrRootOfTree(context);
        if (lowestTrunkOrRoot.isEmpty()) {
            return;
        }

        var minY = lowestTrunkOrRoot.getFirst().blockY();
        for (var position : lowestTrunkOrRoot) {
            if (position.blockY() != minY) {
                continue;
            }

            this.placeCircle(context, position.add(-1, 0, -1));
            this.placeCircle(context, position.add(2, 0, -1));
            this.placeCircle(context, position.add(-1, 0, 2));
            this.placeCircle(context, position.add(2, 0, 2));

            for (var attempt = 0; attempt < 5; attempt++) {
                var placement = context.random().nextInt(64);
                var offsetX = placement % 8;
                var offsetZ = placement / 8;
                if (offsetX == 0 || offsetX == 7 || offsetZ == 0 || offsetZ == 7) {
                    this.placeCircle(context, position.add(-3 + offsetX, 0, -3 + offsetZ));
                }
            }
        }
    }

    private void placeCircle(TreeDecorator.Context context, BlockVec center) {
        for (var x = -2; x <= 2; x++) {
            for (var z = -2; z <= 2; z++) {
                if (Math.abs(x) != 2 || Math.abs(z) != 2) {
                    this.placeBlockAt(context, center.add(x, 0, z));
                }
            }
        }
    }

    private void placeBlockAt(TreeDecorator.Context context, BlockVec position) {
        for (var dy = 2; dy >= -3; dy--) {
            var cursor = position.add(0, dy, 0);
            var replaceWith = this.provider.getOptionalState(context.level(), context.random(), cursor);
            if (replaceWith != null) {
                context.setBlock(cursor, replaceWith);
                break;
            }

            if (!context.isAir(cursor) && dy < 0) {
                break;
            }
        }
    }
}
