package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

import java.util.Set;

/**
 * Port of vanilla's original {@code IceSpikeFeature}, superseded in later
 * vanilla versions by the generic {@code SpikeFeature} (see
 * {@link SpikeFeature}) but retained here for datapacks that still reference
 * the {@code minecraft:ice_spike} feature type. Behaves exactly like the
 * current {@code minecraft:ice_spike} configured feature, which configures
 * {@code minecraft:spike} with a packed ice state, a snow block placement
 * surface, and the {@code #minecraft:ice_spike_replaceable} block tag.
 */
public final class IceSpikeFeature implements Feature<NoneFeatureConfiguration> {
    /** Vanilla {@code #minecraft:ice_spike_replaceable} block tag (26.2 contents). */
    private static final Set<String> REPLACEABLE = Set.of(
            // #minecraft:substrate_overworld
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots",
            "minecraft:moss_block", "minecraft:pale_moss_block",
            "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
            // direct entries
            "minecraft:snow_block", "minecraft:ice");

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var origin = context.origin();
        var random = context.random();
        var level = context.accessor();

        while (level.getBlock(origin).isAir() && origin.blockY() > context.minY() + 2) {
            origin = origin.sub(0, 1, 0);
        }

        if (!level.getBlock(origin).compare(Block.SNOW_BLOCK)) {
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
                        if (state.isAir() || isReplaceable(state)) {
                            level.setBlock(positiveOffset, Block.PACKED_ICE);
                        }

                        if (yOff != 0 && newWidth > 1) {
                            var negativeOffset = origin.add(xo, -yOff, zo);
                            state = level.getBlock(negativeOffset);
                            if (state.isAir() || isReplaceable(state)) {
                                level.setBlock(negativeOffset, Block.PACKED_ICE);
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

                while (cursor.blockY() > 50) {
                    var state = level.getBlock(cursor);
                    if (!state.isAir() && !isReplaceable(state) && !state.compare(Block.PACKED_ICE, Block.Comparator.STATE)) {
                        break;
                    }

                    level.setBlock(cursor, Block.PACKED_ICE);
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

    private static boolean isReplaceable(Block block) {
        return REPLACEABLE.contains(block.name());
    }
}
