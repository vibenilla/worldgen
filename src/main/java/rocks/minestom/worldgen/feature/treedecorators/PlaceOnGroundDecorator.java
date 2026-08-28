package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
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
        var boundingMinY = baseY - this.height;
        var boundingMaxY = baseY + this.height;
        var boundingMinZ = minZ - this.radius;
        var boundingMaxZ = maxZ + this.radius;

        if (rocks.minestom.worldgen.feature.TreeFeature.TRACE) {
            try {
                var sourceField = random.getClass().getDeclaredField("randomSource");
                sourceField.setAccessible(true);
                var source = sourceField.get(random);
                var generatorField = source.getClass().getDeclaredField("randomNumberGenerator");
                generatorField.setAccessible(true);
                var generator = generatorField.get(source);
                var loField = generator.getClass().getDeclaredField("seedLo");
                var hiField = generator.getClass().getDeclaredField("seedHi");
                loField.setAccessible(true);
                hiField.setAccessible(true);
                System.out.println("TRACE litterstart box=[" + boundingMinX + ".." + boundingMaxX + "]x["
                        + boundingMinY + ".." + boundingMaxY + "]x[" + boundingMinZ + ".." + boundingMaxZ
                        + "] tries=" + this.tries + " rng=" + loField.getLong(generator) + " " + hiField.getLong(generator));
            } catch (ReflectiveOperationException exception) {
                System.out.println("TRACE litterstart rng unavailable");
            }
        }

        for (var attempt = 0; attempt < this.tries; attempt++) {
            var randomX = random.nextInt(boundingMaxX - boundingMinX + 1) + boundingMinX;
            var randomY = random.nextInt(boundingMaxY - boundingMinY + 1) + boundingMinY;
            var randomZ = random.nextInt(boundingMaxZ - boundingMinZ + 1) + boundingMinZ;
            var randomPosition = new BlockVec(randomX, randomY, randomZ);
            this.attemptToPlaceBlockAbove(context, randomPosition);
        }
    }

    private void attemptToPlaceBlockAbove(TreeDecorator.Context context, BlockVec position) {
        var trace = rocks.minestom.worldgen.feature.TreeFeature.TRACE;
        var positionAbove = position.add(0, 1, 0);
        var blockAbove = context.level().getBlock(positionAbove);

        // Vanilla: above must be air or vine, the block itself must be a solid
        // renderer, and the position must be at the MOTION_BLOCKING_NO_LEAVES
        // heightmap surface
        if (!blockAbove.air() && !blockAbove.compare(Block.VINE)) {
            if (trace) {
                System.out.println("TRACE litter " + position + " reject above=" + blockAbove.name());
            }
            return;
        }
        if (!isSolidRender(context.level().getBlock(position))) {
            if (trace) {
                System.out.println("TRACE litter " + position + " reject solid=" + context.level().getBlock(position).name());
            }
            return;
        }
        if (context.level() instanceof GenerationUnitAdapter adapter) {
            var surfaceY = adapter.heightmap(
                    GenerationUnitAdapter.HeightmapType.MOTION_BLOCKING_NO_LEAVES,
                    position.blockX(), position.blockZ());
            if (surfaceY != Integer.MAX_VALUE && surfaceY > positionAbove.blockY()) {
                if (trace) {
                    System.out.println("TRACE litter " + position + " reject heightmap=" + surfaceY);
                }
                return;
            }
        }

        var state = this.blockStateProvider.getState(context.level(), context.random(), positionAbove);
        if (trace) {
            System.out.println("TRACE litter " + position + " place " + state.name() + state.properties());
        }
        context.setBlock(positionAbove, state);
    }

    /**
     * Approximation of vanilla's {@code isSolidRender} (opaque full cube):
     * solid blocks except leaves, which are solid but not opaque.
     */
    private static boolean isSolidRender(Block block) {
        return block.solid() && !block.name().endsWith("_leaves");
    }

    static List<BlockVec> getLowestTrunkOrRootOfTree(TreeDecorator.Context context) {
        var result = new ArrayList<BlockVec>();
        var roots = context.roots();
        var logs = context.logs();

        if (roots.isEmpty()) {
            result.addAll(logs);
        } else if (!logs.isEmpty() && roots.getFirst().blockY() == logs.getFirst().blockY()) {
            result.addAll(logs);
            result.addAll(roots);
        } else {
            result.addAll(roots);
        }

        return result;
    }
}
