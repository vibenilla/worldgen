package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla's {@code BeehiveDecorator}: with the configured probability, a bee
 * nest facing south is attached next to a log one block below the lowest
 * leaves. The bee occupant rolls are consumed too, since they come from the
 * shared worldgen random.
 */
public record BeehiveDecorator(float probability) implements TreeDecorator {
    public static final Codec<BeehiveDecorator> CODEC = StructCodec.struct(
            "probability", Codec.FLOAT, BeehiveDecorator::probability,
            BeehiveDecorator::new
    );

    /** Horizontal directions minus north (the worldgen facing's opposite). */
    private static final List<Direction> SPAWN_DIRECTIONS = List.of(Direction.EAST, Direction.SOUTH, Direction.WEST);

    @Override
    public void place(TreeDecorator.Context context) {
        var leaves = context.leaves();
        var logs = context.logs();
        if (logs.isEmpty()) {
            return;
        }

        var random = context.random();
        if (random.nextFloat() >= this.probability) {
            return;
        }

        var hiveY = !leaves.isEmpty()
                ? Math.max(leaves.getFirst().blockY() - 1, logs.getFirst().blockY() + 1)
                : Math.min(logs.getFirst().blockY() + 1 + random.nextInt(3), logs.getLast().blockY());

        var hivePlacements = new ArrayList<BlockVec>();
        for (var log : logs) {
            if (log.blockY() == hiveY) {
                for (var direction : SPAWN_DIRECTIONS) {
                    hivePlacements.add(direction.relative(log));
                }
            }
        }
        if (hivePlacements.isEmpty()) {
            return;
        }

        TreeDecorator.shuffle(hivePlacements, random);

        BlockVec hivePos = null;
        for (var position : hivePlacements) {
            if (context.isAir(position) && context.isAir(Direction.SOUTH.relative(position))) {
                hivePos = position;
                break;
            }
        }
        if (hivePos == null) {
            return;
        }

        context.setBlock(hivePos, Block.BEE_NEST.withProperty("facing", "south"));

        // Vanilla stores 2-3 bees in the block entity, each rolling ticks in
        // the hive from the shared random
        var beeCount = 2 + random.nextInt(2);
        for (var bee = 0; bee < beeCount; bee++) {
            random.nextInt(599);
        }
    }
}
