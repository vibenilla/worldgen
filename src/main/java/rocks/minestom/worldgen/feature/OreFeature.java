package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.OreConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.BitSet;

/**
 * Port of vanilla {@code OreFeature}. The random call order matches vanilla
 * exactly, which is required for block-for-block parity.
 */
public final class OreFeature implements Feature<OreConfiguration> {
    /**
     * Vanilla {@code Mth.SIN} lookup table, required because vanilla uses the
     * quantized sine for the strand radius computation.
     */
    private static final float[] SIN = new float[65536];

    static {
        for (var index = 0; index < SIN.length; index++) {
            SIN[index] = (float) Math.sin(index / 10430.378350470453);
        }
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<OreConfiguration, T> context) {
        var random = context.random();
        var origin = context.origin();
        var level = context.accessor();
        var config = context.config();
        var dir = random.nextFloat() * (float) Math.PI;
        var spreadXY = config.size() / 8.0F;
        var maxRadius = ceil((config.size() / 16.0F * 2.0F + 1.0F) / 2.0F);
        var x0 = origin.blockX() + Math.sin(dir) * spreadXY;
        var x1 = origin.blockX() - Math.sin(dir) * spreadXY;
        var z0 = origin.blockZ() + Math.cos(dir) * spreadXY;
        var z1 = origin.blockZ() - Math.cos(dir) * spreadXY;
        var y0 = (double) (origin.blockY() + random.nextInt(3) - 2);
        var y1 = (double) (origin.blockY() + random.nextInt(3) - 2);
        var xStart = origin.blockX() - ceil(spreadXY) - maxRadius;
        var yStart = origin.blockY() - 2 - maxRadius;
        var zStart = origin.blockZ() - ceil(spreadXY) - maxRadius;
        var sizeXZ = 2 * (ceil(spreadXY) + maxRadius);
        var sizeY = 2 * (2 + maxRadius);

        var debugProbe = System.getProperty("worldgen.oreProbeDebug") != null;
        for (var xprobe = xStart; xprobe <= xStart + sizeXZ; xprobe++) {
            for (var zprobe = zStart; zprobe <= zStart + sizeXZ; zprobe++) {
                if (yStart <= getHeight(level, context, xprobe, zprobe)) {
                    if (debugProbe) {
                        System.out.println("OREPROBE origin=" + origin + " yStart=" + yStart
                                + " pass=" + xprobe + "," + zprobe
                                + " height=" + getHeight(level, context, xprobe, zprobe));
                    }
                    return this.doPlace(level, random, context, config,
                            x0, x1, z0, z1, y0, y1,
                            xStart, yStart, zStart, sizeXZ, sizeY);
                }
            }
        }

        if (debugProbe) {
            System.out.println("OREPROBE origin=" + origin + " yStart=" + yStart + " SKIP");
        }
        return false;
    }

    private <T extends Block.Getter & Block.Setter> boolean doPlace(
            T level,
            RandomSource random,
            FeaturePlaceContext<OreConfiguration, T> context,
            OreConfiguration config,
            double x0,
            double x1,
            double z0,
            double z1,
            double y0,
            double y1,
            int xStart,
            int yStart,
            int zStart,
            int sizeXZ,
            int sizeY
    ) {
        var placed = 0;
        var tested = new BitSet(sizeXZ * sizeY * sizeXZ);
        var size = config.size();
        var data = new double[size * 4];

        for (var i = 0; i < size; i++) {
            var step = (float) i / size;
            var xx = lerp(step, x0, x1);
            var yy = lerp(step, y0, y1);
            var zz = lerp(step, z0, z1);
            var ss = random.nextDouble() * size / 16.0;
            var r = ((sin((float) Math.PI * step) + 1.0F) * ss + 1.0) / 2.0;
            data[i * 4 + 0] = xx;
            data[i * 4 + 1] = yy;
            data[i * 4 + 2] = zz;
            data[i * 4 + 3] = r;
        }

        for (var i1 = 0; i1 < size - 1; i1++) {
            if (!(data[i1 * 4 + 3] <= 0.0)) {
                for (var i2 = i1 + 1; i2 < size; i2++) {
                    if (!(data[i2 * 4 + 3] <= 0.0)) {
                        var dx = data[i1 * 4 + 0] - data[i2 * 4 + 0];
                        var dy = data[i1 * 4 + 1] - data[i2 * 4 + 1];
                        var dz = data[i1 * 4 + 2] - data[i2 * 4 + 2];
                        var dr = data[i1 * 4 + 3] - data[i2 * 4 + 3];
                        if (dr * dr > dx * dx + dy * dy + dz * dz) {
                            if (dr > 0.0) {
                                data[i2 * 4 + 3] = -1.0;
                            } else {
                                data[i1 * 4 + 3] = -1.0;
                            }
                        }
                    }
                }
            }
        }

        for (var i = 0; i < size; i++) {
            var r = data[i * 4 + 3];
            if (!(r < 0.0)) {
                var xx = data[i * 4 + 0];
                var yy = data[i * 4 + 1];
                var zz = data[i * 4 + 2];
                var xMin = Math.max(floor(xx - r), xStart);
                var yMin = Math.max(floor(yy - r), yStart);
                var zMin = Math.max(floor(zz - r), zStart);
                var xMax = Math.max(floor(xx + r), xMin);
                var yMax = Math.max(floor(yy + r), yMin);
                var zMax = Math.max(floor(zz + r), zMin);

                for (var x = xMin; x <= xMax; x++) {
                    var xd = (x + 0.5 - xx) / r;
                    if (xd * xd < 1.0) {
                        for (var y = yMin; y <= yMax; y++) {
                            var yd = (y + 0.5 - yy) / r;
                            if (xd * xd + yd * yd < 1.0) {
                                for (var z = zMin; z <= zMax; z++) {
                                    var zd = (z + 0.5 - zz) / r;
                                    if (xd * xd + yd * yd + zd * zd < 1.0
                                            && y >= context.minY() && y <= context.maxY()) {
                                        var bitSetIndex = x - xStart + (y - yStart) * sizeXZ + (z - zStart) * sizeXZ * sizeY;
                                        if (!tested.get(bitSetIndex)) {
                                            tested.set(bitSetIndex);
                                            var orePos = new BlockVec(x, y, z);
                                            var blockState = level.getBlock(x, y, z);

                                            for (var targetState : config.targetStates()) {
                                                if (canPlaceOre(blockState, level, random, config, targetState, orePos)) {
                                                    level.setBlock(x, y, z, targetState.state());
                                                    placed++;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return placed > 0;
    }

    public static boolean canPlaceOre(
            Block blockState,
            Block.Getter blockGetter,
            RandomSource random,
            OreConfiguration config,
            OreConfiguration.TargetBlockState targetState,
            BlockVec orePos
    ) {
        if (!targetState.target().test(blockState, random)) {
            return false;
        }

        return shouldSkipAirCheck(random, config.discardChanceOnAirExposure()) || !isAdjacentToAir(blockGetter, orePos);
    }

    private static boolean shouldSkipAirCheck(RandomSource random, float discardChanceOnAirExposure) {
        if (discardChanceOnAirExposure <= 0.0F) {
            return true;
        }

        return !(discardChanceOnAirExposure >= 1.0F) && random.nextFloat() >= discardChanceOnAirExposure;
    }

    private static boolean isAdjacentToAir(Block.Getter blockGetter, BlockVec pos) {
        // Vanilla Direction order: DOWN, UP, NORTH, SOUTH, WEST, EAST.
        return blockGetter.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ()).isAir()
                || blockGetter.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ()).isAir()
                || blockGetter.getBlock(pos.blockX(), pos.blockY(), pos.blockZ() - 1).isAir()
                || blockGetter.getBlock(pos.blockX(), pos.blockY(), pos.blockZ() + 1).isAir()
                || blockGetter.getBlock(pos.blockX() - 1, pos.blockY(), pos.blockZ()).isAir()
                || blockGetter.getBlock(pos.blockX() + 1, pos.blockY(), pos.blockZ()).isAir();
    }

    private static int getHeight(Block.Getter level, FeaturePlaceContext<?, ?> context, int x, int z) {
        // Vanilla OCEAN_FLOOR_WG: frozen post-carver terrain, blind to
        // structure and feature writes
        if (level instanceof GenerationUnitAdapter adapter) {
            var frozen = adapter.frozenOceanFloor(x, z);
            if (frozen != Integer.MAX_VALUE) {
                return frozen;
            }
            return adapter.getHeight(x, z);
        }

        return context.maxY();
    }

    private static float sin(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453) & 65535L)];
    }

    private static double lerp(double alpha, double p0, double p1) {
        return p0 + alpha * (p1 - p0);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    private static int ceil(float value) {
        return (int) Math.ceil(value);
    }
}
