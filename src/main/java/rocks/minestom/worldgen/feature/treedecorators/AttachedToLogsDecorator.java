package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's {@code AttachedToLogsDecorator}: every log (in shuffled order)
 * rolls a random configured direction, then the probability, and places the
 * provided block if the target is air.
 */
public record AttachedToLogsDecorator(
        float probability,
        BlockStateProvider blockProvider,
        List<Direction> directions
) implements TreeDecorator {
    public static final Codec<AttachedToLogsDecorator> CODEC = StructCodec.struct(
            "probability", Codec.FLOAT, AttachedToLogsDecorator::probability,
            "block_provider", BlockStateProviders.CODEC, AttachedToLogsDecorator::blockProvider,
            "directions", Direction.CODEC.list(), AttachedToLogsDecorator::directions,
            AttachedToLogsDecorator::new
    );

    @Override
    public void place(TreeDecorator.Context context) {
        var random = context.random();
        var logs = new ArrayList<>(context.logs());
        TreeDecorator.shuffle(logs, random);

        for (var log : logs) {
            var direction = this.directions.get(random.nextInt(this.directions.size()));
            var position = direction.relative(log);
            if (random.nextFloat() <= this.probability && context.isAir(position)) {
                context.setBlock(position, this.blockProvider.getState(context.level(), random, position));
            }
        }
    }
}
