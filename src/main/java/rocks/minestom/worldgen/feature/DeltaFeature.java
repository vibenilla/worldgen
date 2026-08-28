package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.DeltaFeatureConfiguration;

import java.util.Set;

/**
 * Port of vanilla {@code DeltaFeature}: lava (or other content) pools with an
 * optional offset rim, filling one-deep surface pockets in basalt deltas.
 * 26.2 vanilla guards replacement with a static block list only; it performs
 * no structure bounding-box queries.
 */
public final class DeltaFeature implements Feature<DeltaFeatureConfiguration> {
    /** Vanilla {@code DeltaFeature.CANNOT_REPLACE} (26.2 contents). */
    private static final Set<String> CANNOT_REPLACE = Set.of(
            "minecraft:bedrock", "minecraft:nether_bricks", "minecraft:nether_brick_fence",
            "minecraft:nether_brick_stairs", "minecraft:nether_wart", "minecraft:chest", "minecraft:spawner");

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<DeltaFeatureConfiguration, T> context) {
        var anyPlaced = false;
        var random = context.random();
        var level = context.accessor();
        var config = context.config();
        var origin = context.origin();
        var spawnRim = random.nextDouble() < 0.9;
        var rimX = spawnRim ? config.rimSize().sample(random) : 0;
        var rimZ = spawnRim ? config.rimSize().sample(random) : 0;
        var hasRim = spawnRim && rimX != 0 && rimZ != 0;
        var radiusX = config.size().sample(random);
        var radiusZ = config.size().sample(random);
        var radiusLimit = Math.max(radiusX, radiusZ);

        for (var pos : BlockPosIterators.withinManhattan(origin, radiusX, 0, radiusZ)) {
            if (BlockPosIterators.distManhattan(pos, origin) > radiusLimit) {
                break;
            }

            if (isClear(level, pos, config)) {
                if (hasRim) {
                    anyPlaced = true;
                    level.setBlock(pos, config.rim());
                }

                var posOffset = pos.add(rimX, 0, rimZ);
                if (isClear(level, posOffset, config)) {
                    anyPlaced = true;
                    level.setBlock(posOffset, config.contents());
                }
            }
        }

        return anyPlaced;
    }

    private static boolean isClear(Block.Getter level, BlockVec pos, DeltaFeatureConfiguration config) {
        var state = level.getBlock(pos);
        if (state.compare(config.contents())) {
            return false;
        }

        if (CANNOT_REPLACE.contains(state.name())) {
            return false;
        }

        for (var direction : Direction.values()) {
            var isAir = level.getBlock(direction.relative(pos)).air();
            if (isAir && direction != Direction.UP || !isAir && direction == Direction.UP) {
                return false;
            }
        }

        return true;
    }
}
