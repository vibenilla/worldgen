package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;

/**
 * Vanilla's {@code CocoaDecorator}: with the configured probability, cocoa
 * pods grow on the horizontal sides of the lowest trunk logs.
 */
public record CocoaDecorator(float probability) implements TreeDecorator {
    public static final Codec<CocoaDecorator> CODEC = StructCodec.struct(
            "probability", Codec.FLOAT, CocoaDecorator::probability,
            CocoaDecorator::new
    );

    @Override
    public void place(TreeDecorator.Context context) {
        var random = context.random();
        if (random.nextFloat() >= this.probability) {
            return;
        }

        var logs = context.logs();
        if (logs.isEmpty()) {
            return;
        }

        var treeY = logs.getFirst().blockY();
        for (var log : logs) {
            if (log.blockY() - treeY > 2) {
                continue;
            }

            for (var direction : Direction.HORIZONTAL) {
                if (random.nextFloat() <= 0.25F) {
                    var opposite = direction.opposite();
                    var cocoaPos = log.add(opposite.stepX(), 0, opposite.stepZ());
                    if (context.isAir(cocoaPos)) {
                        context.setBlock(cocoaPos, Block.COCOA
                                .withProperty("age", Integer.toString(random.nextInt(3)))
                                .withProperty("facing", direction.name().toLowerCase()));
                    }
                }
            }
        }
    }
}
