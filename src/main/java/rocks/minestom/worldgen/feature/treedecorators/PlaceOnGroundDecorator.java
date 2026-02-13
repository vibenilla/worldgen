package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

import java.util.ArrayList;
import java.util.List;

public record PlaceOnGroundDecorator(int tries, int radius, int height, BlockStateProvider blockStateProvider) implements TreeDecorator {
    public static final Codec<PlaceOnGroundDecorator> CODEC = StructCodec.struct(
            "tries", Codec.INT.optional(128), PlaceOnGroundDecorator::tries,
            "radius", Codec.INT.optional(2), PlaceOnGroundDecorator::radius,
            "height", Codec.INT.optional(1), PlaceOnGroundDecorator::height,
            "block_state_provider", BlockStateProviders.CODEC, PlaceOnGroundDecorator::blockStateProvider,
            PlaceOnGroundDecorator::new
    );

    @Override
    public void place(TreeDecorator.Context context) {
        var lowestTrunkOrRoot = getLowestTrunkOrRootOfTree(context);

        if (lowestTrunkOrRoot.isEmpty()) {
            return;
        }

        var firstPosition = lowestTrunkOrRoot.getFirst();
        var baseY = firstPosition.blockY();
        var minX = firstPosition.blockX();
        var maxX = firstPosition.blockX();
        var minZ = firstPosition.blockZ();
        var maxZ = firstPosition.blockZ();

        for (var position : lowestTrunkOrRoot) {
            if (position.blockY() == baseY) {
                minX = Math.min(minX, position.blockX());
                maxX = Math.max(maxX, position.blockX());
                minZ = Math.min(minZ, position.blockZ());
                maxZ = Math.max(maxZ, position.blockZ());
            }
        }

        var random = context.random();
        var boundingMinX = minX - this.radius;
        var boundingMaxX = maxX + this.radius;
        var boundingMinY = baseY;
        var boundingMaxY = baseY + this.height;
        var boundingMinZ = minZ - this.radius;
        var boundingMaxZ = maxZ + this.radius;

        for (var attempt = 0; attempt < this.tries; attempt++) {
            var randomX = random.nextInt(boundingMaxX - boundingMinX + 1) + boundingMinX;
            var randomY = random.nextInt(boundingMaxY - boundingMinY + 1) + boundingMinY;
            var randomZ = random.nextInt(boundingMaxZ - boundingMinZ + 1) + boundingMinZ;
            var randomPosition = new BlockVec(randomX, randomY, randomZ);
            this.attemptToPlaceBlockAbove(context, randomPosition);
        }
    }

    private void attemptToPlaceBlockAbove(TreeDecorator.Context context, BlockVec position) {
        var positionAbove = position.add(0, 1, 0);
        var blockAbove = context.level().getBlock(positionAbove);
        var blockBelow = context.level().getBlock(position);

        // Check if position above is air and position below is solid
        if ((blockAbove.isAir() || blockAbove.name().equals("minecraft:vine")) && isSolid(blockBelow)) {
            context.setBlock(positionAbove, this.blockStateProvider.getState(context.random(), positionAbove));
        }
    }

    private static boolean isSolid(Block block) {
        // Check if block is solid (not air, not water, not lava, etc.)
        return !block.isAir() && !block.isLiquid();
    }

    private static List<BlockVec> getLowestTrunkOrRootOfTree(TreeDecorator.Context context) {
        var result = new ArrayList<BlockVec>();
        var roots = context.roots();
        var logs = context.logs();

        if (roots.isEmpty()) {
            result.addAll(logs);
        } else if (!logs.isEmpty() && !roots.isEmpty() && roots.getFirst().blockY() == logs.getFirst().blockY()) {
            result.addAll(logs);
            result.addAll(roots);
        } else {
            result.addAll(roots);
        }

        return result;
    }
}
