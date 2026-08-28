package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SpikeConfiguration;

/**
 * Port of vanilla's {@code SpikeFeature}, the generic version of the old ice
 * spike feature. Random call order matches vanilla exactly.
 */
public final class SpikeFeature implements Feature<SpikeConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SpikeConfiguration, T> context) {
        var origin = context.origin();
        var random = context.random();
        var level = context.accessor();

        while (level.getBlock(origin).air() && origin.blockY() > context.minY() + 2) {
            origin = origin.sub(0, 1, 0);
        }

        var config = context.config();
        var predicateContext = Feature.predicateContext(level, context.minY(), context.maxY());
        if (!config.canPlaceOn().test(predicateContext, origin)) {
            return false;
        }

        origin = origin.add(0, random.nextInt(4), 0);
        var height = random.nextInt(4) + 7;
        var width = height / 4 + random.nextInt(2);
        if (width > 1 && random.nextInt(60) == 0) {
            origin = origin.add(0, 10 + random.nextInt(30), 0);
        }

        for (var yOff = 0; yOff < height; yOff++) {
            var scale = (1.0F - (float) yOff / height) * width;
            var newWidth = (int) Math.ceil(scale);

            for (var xo = -newWidth; xo <= newWidth; xo++) {
                var dx = Math.abs(xo) - 0.25F;

                for (var zo = -newWidth; zo <= newWidth; zo++) {
                    var dz = Math.abs(zo) - 0.25F;
                    if ((xo == 0 && zo == 0 || !(dx * dx + dz * dz > scale * scale))
                            && (xo != -newWidth && xo != newWidth && zo != -newWidth && zo != newWidth || !(random.nextFloat() > 0.75F))) {
                        var positiveOffset = origin.add(xo, yOff, zo);
                        var state = level.getBlock(positiveOffset);
                        if (state.air() || config.canReplace().test(predicateContext, positiveOffset)) {
                            level.setBlock(positiveOffset, config.state());
                        }

                        if (yOff != 0 && newWidth > 1) {
                            var negativeOffset = origin.add(xo, -yOff, zo);
                            state = level.getBlock(negativeOffset);
                            if (state.air() || config.canReplace().test(predicateContext, negativeOffset)) {
                                level.setBlock(negativeOffset, config.state());
                            }
                        }
                    }
                }
            }
        }

        var pillarWidth = width - 1;
        if (pillarWidth < 0) {
            pillarWidth = 0;
        } else if (pillarWidth > 1) {
            pillarWidth = 1;
        }

        for (var xo = -pillarWidth; xo <= pillarWidth; xo++) {
            for (var zo = -pillarWidth; zo <= pillarWidth; zo++) {
                var cursor = new BlockVec(origin.blockX() + xo, origin.blockY() - 1, origin.blockZ() + zo);
                var runLength = 50;
                if (Math.abs(xo) == 1 && Math.abs(zo) == 1) {
                    runLength = random.nextInt(5);
                }

                // Vanilla hardcodes the Y=50 cutoff for the support pillar.
                while (cursor.blockY() > 50) {
                    var state = level.getBlock(cursor);
                    if (!state.air()
                            && !config.canReplace().test(predicateContext, cursor)
                            && !state.compare(config.state(), Block.Comparator.STATE)) {
                        break;
                    }

                    level.setBlock(cursor, config.state());
                    cursor = cursor.sub(0, 1, 0);
                    if (--runLength <= 0) {
                        cursor = cursor.sub(0, random.nextInt(5) + 1, 0);
                        runLength = random.nextInt(5);
                    }
                }
            }
        }

        return true;
    }
}
