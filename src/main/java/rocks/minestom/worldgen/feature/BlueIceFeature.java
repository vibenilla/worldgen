package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Port of vanilla {@code BlueIceFeature}: converts a packed ice block
 * adjacent to water into blue ice, then grows the patch by 200 random attach
 * attempts that each require exactly one blue ice neighbor.
 */
public final class BlueIceFeature implements Feature<NoneFeatureConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var random = context.random();
        if (origin.blockY() > context.seaLevel() - 1) {
            return false;
        }

        if (!level.getBlock(origin).compare(Block.WATER) && !level.getBlock(origin.sub(0, 1, 0)).compare(Block.WATER)) {
            return false;
        }

        var foundPackedIce = false;
        for (var direction : Direction.values()) {
            if (direction != Direction.DOWN && level.getBlock(direction.relative(origin)).compare(Block.PACKED_ICE)) {
                foundPackedIce = true;
                break;
            }
        }

        if (!foundPackedIce) {
            return false;
        }

        level.setBlock(origin, Block.BLUE_ICE);

        for (var attempt = 0; attempt < 200; attempt++) {
            var yOff = random.nextInt(5) - random.nextInt(6);
            var xzDiff = 3;
            if (yOff < 2) {
                xzDiff += yOff / 2;
            }

            if (xzDiff >= 1) {
                var placePos = origin.add(
                        random.nextInt(xzDiff) - random.nextInt(xzDiff),
                        yOff,
                        random.nextInt(xzDiff) - random.nextInt(xzDiff));
                var placeState = level.getBlock(placePos);
                if (placeState.isAir() || placeState.compare(Block.WATER) || placeState.compare(Block.PACKED_ICE)
                        || placeState.compare(Block.ICE)) {
                    for (var direction : Direction.values()) {
                        if (level.getBlock(direction.relative(placePos)).compare(Block.BLUE_ICE)) {
                            level.setBlock(placePos, Block.BLUE_ICE);
                            break;
                        }
                    }
                }
            }
        }

        return true;
    }
}
