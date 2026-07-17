package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.DiskConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code DiskFeature}: sand/gravel/clay disks on river and
 * ocean floors, replacing target blocks in a cylinder around the origin.
 */
public final class DiskFeature implements Feature<DiskConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<DiskConfiguration, T> context) {
        var config = context.config();
        var origin = context.origin();
        var level = context.accessor();
        var random = context.random();
        var placedAny = false;
        var originY = origin.blockY();
        var top = originY + config.halfHeight();
        var bottom = originY - config.halfHeight() - 1;
        var radius = config.radius().sample(random);

        var predicateContext = new PlacementContext(
                level, 0, 0, 0, 0, null, null,
                context.minY(), context.maxY(), 0, null, null, null);

        // Vanilla BlockPos.betweenClosed iterates x fastest, then z
        for (var z = origin.blockZ() - radius; z <= origin.blockZ() + radius; z++) {
            for (var x = origin.blockX() - radius; x <= origin.blockX() + radius; x++) {
                var dx = x - origin.blockX();
                var dz = z - origin.blockZ();
                if (dx * dx + dz * dz <= radius * radius) {
                    placedAny |= this.placeColumn(config, level, random, predicateContext, top, bottom, x, z);
                }
            }
        }

        return placedAny;
    }

    private <T extends Block.Getter & Block.Setter> boolean placeColumn(DiskConfiguration config, T level,
            RandomSource random, PlacementContext predicateContext, int top, int bottom, int x, int z) {
        var placedAny = false;

        for (var y = top; y > bottom; y--) {
            var position = new BlockVec(x, y, z);
            if (config.target().test(predicateContext, position)) {
                var state = config.stateProvider().getOptionalState(level, random, position);
                if (state != null) {
                    level.setBlock(position, state);
                    placedAny = true;
                }
            }
        }

        return placedAny;
    }
}
