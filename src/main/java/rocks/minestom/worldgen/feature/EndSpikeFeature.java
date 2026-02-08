package rocks.minestom.worldgen.feature;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.feature.configurations.SpikeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.List;

public final class EndSpikeFeature implements Feature<SpikeConfiguration> {
    private static final int SPIKE_COUNT = 10;
    private static final int SPIKE_DISTANCE = 42;

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SpikeConfiguration, T> context) {
        var chunkStartX = context.origin().blockX() & ~15;
        var chunkStartZ = context.origin().blockZ() & ~15;
        var spikes = context.config().spikes();
        if (spikes.isEmpty()) {
            spikes = getSpikesForSeed(context.worldSeed());
        }

        var level = context.accessor();
        var minY = context.minY();
        var maxY = context.maxY();
        var placed = false;
        for (var spike : spikes) {
            if (!spike.isCenterWithinChunk(chunkStartX, chunkStartZ)) {
                continue;
            }

            this.placeSpike(level, minY, maxY, spike);
            placed = true;
        }

        return placed;
    }

    private <T extends Block.Getter & Block.Setter> void placeSpike(T level, int minY, int maxY, EndSpike spike) {
        var radius = spike.radius();
        var centerX = spike.centerX();
        var centerZ = spike.centerZ();

        for (var x = centerX - radius; x <= centerX + radius; x++) {
            for (var z = centerZ - radius; z <= centerZ + radius; z++) {
                var dx = x - centerX;
                var dz = z - centerZ;
                if (dx * dx + dz * dz > radius * radius + 1) {
                    continue;
                }

                var height = Math.min(spike.height(), maxY + 1);
                for (var y = minY; y < height; y++) {
                    level.setBlock(x, y, z, Block.OBSIDIAN);
                }
            }
        }

        if (spike.height() <= maxY) {
            level.setBlock(centerX, spike.height(), centerZ, Block.BEDROCK);
        }

        if (spike.guarded() && spike.height() <= maxY) {
            placeCage(level, spike);
        }
    }

    private static <T extends Block.Getter & Block.Setter> void placeCage(T level, EndSpike spike) {
        var baseY = spike.height();
        for (var offsetX = -2; offsetX <= 2; offsetX++) {
            for (var offsetZ = -2; offsetZ <= 2; offsetZ++) {
                for (var offsetY = 0; offsetY <= 3; offsetY++) {
                    var edgeX = Math.abs(offsetX) == 2;
                    var edgeZ = Math.abs(offsetZ) == 2;
                    var top = offsetY == 3;
                    if (!edgeX && !edgeZ && !top) {
                        continue;
                    }

                    var connectX = offsetX == -2 || offsetX == 2 || top;
                    var connectZ = offsetZ == -2 || offsetZ == 2 || top;
                    var north = connectX && offsetZ != -2;
                    var south = connectX && offsetZ != 2;
                    var west = connectZ && offsetX != -2;
                    var east = connectZ && offsetX != 2;

                    var block = Block.IRON_BARS
                            .withProperty("north", Boolean.toString(north))
                            .withProperty("south", Boolean.toString(south))
                            .withProperty("west", Boolean.toString(west))
                            .withProperty("east", Boolean.toString(east));

                    level.setBlock(spike.centerX() + offsetX, baseY + offsetY, spike.centerZ() + offsetZ, block);
                }
            }
        }
    }

    private static List<EndSpike> getSpikesForSeed(long seed) {
        var randomSource = new XoroshiroRandomSource(seed);
        var value = randomSource.nextLong() & 65535L;
        var shuffled = shuffledIndices(new XoroshiroRandomSource(value));
        var spikes = new ArrayList<EndSpike>(SPIKE_COUNT);

        for (var index = 0; index < SPIKE_COUNT; index++) {
            var angle = 2.0D * (-Math.PI + Math.PI / 10.0D * (double) index);
            var centerX = VMath.floor(SPIKE_DISTANCE * Math.cos(angle));
            var centerZ = VMath.floor(SPIKE_DISTANCE * Math.sin(angle));
            var shuffledValue = shuffled.get(index);
            var radius = 2 + shuffledValue / 3;
            var height = 76 + shuffledValue * 3;
            var guarded = shuffledValue == 1 || shuffledValue == 2;
            spikes.add(new EndSpike(centerX, centerZ, radius, height, guarded));
        }

        return spikes;
    }

    private static List<Integer> shuffledIndices(RandomSource random) {
        var values = new ArrayList<Integer>(SPIKE_COUNT);
        for (var index = 0; index < SPIKE_COUNT; index++) {
            values.add(index);
        }

        for (var index = 0; index < values.size(); index++) {
            var swapIndex = index + random.nextInt(values.size() - index);
            var current = values.get(index);
            values.set(index, values.get(swapIndex));
            values.set(swapIndex, current);
        }

        return values;
    }

    public record EndSpike(int centerX, int centerZ, int radius, int height, boolean guarded) {
        public static final Codec<EndSpike> CODEC = StructCodec.struct(
                "centerX", Codec.INT.optional(0), EndSpike::centerX,
                "centerZ", Codec.INT.optional(0), EndSpike::centerZ,
                "radius", Codec.INT.optional(0), EndSpike::radius,
                "height", Codec.INT.optional(0), EndSpike::height,
                "guarded", Codec.BOOLEAN.optional(false), EndSpike::guarded,
                EndSpike::new
        );

        public boolean isCenterWithinChunk(int chunkStartX, int chunkStartZ) {
            return this.centerX >= chunkStartX && this.centerX < chunkStartX + 16
                    && this.centerZ >= chunkStartZ && this.centerZ < chunkStartZ + 16;
        }
    }
}
