package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.IcebergConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code IcebergFeature}: a rounded or elliptical iceberg
 * generated around the dimension's sea level, with an above-water dome, a
 * below-water keel, an optional snow cap, and an optional carved-out cutout.
 * Random call order matches vanilla exactly.
 */
public final class IcebergFeature implements Feature<IcebergConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<IcebergConfiguration, T> context) {
        var level = context.accessor();
        var random = context.random();
        var origin = new BlockVec(context.origin().blockX(), context.seaLevel(), context.origin().blockZ());
        var snowOnTop = random.nextDouble() > 0.7;
        var mainBlockState = context.config().state();
        var shapeAngle = random.nextDouble() * 2.0 * Math.PI;
        var shapeEllipseA = 11 - random.nextInt(5);
        var shapeEllipseC = 3 + random.nextInt(3);
        var isEllipse = random.nextDouble() > 0.7;
        var overWaterHeight = isEllipse ? random.nextInt(6) + 6 : random.nextInt(15) + 3;
        if (!isEllipse && random.nextDouble() > 0.9) {
            overWaterHeight += random.nextInt(19) + 7;
        }

        var underWaterHeight = Math.min(overWaterHeight + random.nextInt(11), 18);
        var width = Math.min(overWaterHeight + random.nextInt(7) - random.nextInt(5), 11);
        var a = isEllipse ? shapeEllipseA : 11;

        for (var xo = -a; xo < a; xo++) {
            for (var zo = -a; zo < a; zo++) {
                for (var yOff = 0; yOff < overWaterHeight; yOff++) {
                    var radius = isEllipse
                            ? heightDependentRadiusEllipse(yOff, overWaterHeight, width)
                            : heightDependentRadiusRound(random, yOff, overWaterHeight, width);
                    if (isEllipse || xo < radius) {
                        generateIcebergBlock(level, random, origin, overWaterHeight, xo, yOff, zo, radius, a,
                                isEllipse, shapeEllipseC, shapeAngle, snowOnTop, mainBlockState);
                    }
                }
            }
        }

        smooth(level, origin, width, overWaterHeight, isEllipse, shapeEllipseA);

        for (var xo = -a; xo < a; xo++) {
            for (var zo = -a; zo < a; zo++) {
                for (var yOff = -1; yOff > -underWaterHeight; yOff--) {
                    var newA = isEllipse
                            ? (int) Math.ceil((float) a * (1.0F - (float) Math.pow(yOff, 2.0) / ((float) underWaterHeight * 8.0F)))
                            : a;
                    var radius = heightDependentRadiusSteep(random, -yOff, underWaterHeight, width);
                    if (xo < radius) {
                        generateIcebergBlock(level, random, origin, underWaterHeight, xo, yOff, zo, radius, newA,
                                isEllipse, shapeEllipseC, shapeAngle, snowOnTop, mainBlockState);
                    }
                }
            }
        }

        var doCutOut = isEllipse ? random.nextDouble() > 0.1 : random.nextDouble() > 0.7;
        if (doCutOut) {
            generateCutOut(random, level, width, overWaterHeight, origin, isEllipse, shapeEllipseA, shapeAngle, shapeEllipseC);
        }

        return true;
    }

    private static <T extends Block.Getter & Block.Setter> void generateCutOut(
            RandomSource random, T level, int width, int height, BlockVec globalOrigin, boolean isEllipse,
            int shapeEllipseA, double shapeAngle, int shapeEllipseC) {
        var randomSignX = random.nextBoolean() ? -1 : 1;
        var randomSignZ = random.nextBoolean() ? -1 : 1;
        var xOff = random.nextInt(Math.max(width / 2 - 2, 1));
        if (random.nextBoolean()) {
            xOff = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }

        var zOff = random.nextInt(Math.max(width / 2 - 2, 1));
        if (random.nextBoolean()) {
            zOff = width / 2 + 1 - random.nextInt(Math.max(width - width / 2 - 1, 1));
        }

        if (isEllipse) {
            xOff = zOff = random.nextInt(Math.max(shapeEllipseA - 5, 1));
        }

        var localOrigin = new BlockVec(randomSignX * xOff, 0, randomSignZ * zOff);
        var angle = isEllipse ? shapeAngle + Math.PI / 2 : random.nextDouble() * 2.0 * Math.PI;

        for (var yOff = 0; yOff < height - 3; yOff++) {
            var radius = heightDependentRadiusRound(random, yOff, height, width);
            carve(radius, yOff, globalOrigin, level, false, angle, localOrigin, shapeEllipseA, shapeEllipseC);
        }

        for (var yOff = -1; yOff > -height + random.nextInt(5); yOff--) {
            var radius = heightDependentRadiusSteep(random, -yOff, height, width);
            carve(radius, yOff, globalOrigin, level, true, angle, localOrigin, shapeEllipseA, shapeEllipseC);
        }
    }

    private static <T extends Block.Getter & Block.Setter> void carve(
            int radius, int yOff, BlockVec globalOrigin, T level, boolean underWater, double angle,
            BlockVec localOrigin, int shapeEllipseA, int shapeEllipseC) {
        var a = radius + 1 + shapeEllipseA / 3;
        var c = Math.min(radius - 3, 3) + shapeEllipseC / 2 - 1;

        for (var xo = -a; xo < a; xo++) {
            for (var zo = -a; zo < a; zo++) {
                var signedDist = signedDistanceEllipse(xo, zo, localOrigin.blockX(), localOrigin.blockZ(), a, c, angle);
                if (signedDist < 0.0) {
                    var pos = globalOrigin.add(xo, yOff, zo);
                    var state = level.getBlock(pos);
                    if (isIcebergState(state) || state.compare(Block.SNOW_BLOCK)) {
                        if (underWater) {
                            level.setBlock(pos, Block.WATER);
                        } else {
                            level.setBlock(pos, Block.AIR);
                            removeFloatingSnowLayer(level, pos);
                        }
                    }
                }
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> void removeFloatingSnowLayer(T level, BlockVec pos) {
        var above = pos.add(0, 1, 0);
        if (level.getBlock(above).compare(Block.SNOW)) {
            level.setBlock(above, Block.AIR);
        }
    }

    private static <T extends Block.Getter & Block.Setter> void generateIcebergBlock(
            T level, RandomSource random, BlockVec origin, int height, int xo, int yOff, int zo, int radius, int a,
            boolean isEllipse, int shapeEllipseC, double shapeAngle, boolean snowOnTop, Block mainBlockState) {
        var signedDist = isEllipse
                ? signedDistanceEllipse(xo, zo, 0, 0, a, getEllipseC(yOff, height, shapeEllipseC), shapeAngle)
                : signedDistanceCircle(xo, zo, radius, random);
        if (signedDist < 0.0) {
            var pos = origin.add(xo, yOff, zo);
            var compareVal = isEllipse ? -0.5 : (double) (-6 - random.nextInt(3));
            if (signedDist > compareVal && random.nextDouble() > 0.9) {
                return;
            }

            setIcebergBlock(pos, level, random, height - yOff, height, isEllipse, snowOnTop, mainBlockState);
        }
    }

    private static <T extends Block.Getter & Block.Setter> void setIcebergBlock(
            BlockVec pos, T level, RandomSource random, int heightDifference, int height, boolean isEllipse,
            boolean snowOnTop, Block mainBlockState) {
        var state = level.getBlock(pos);
        if (state.isAir() || state.compare(Block.SNOW_BLOCK) || state.compare(Block.ICE) || state.compare(Block.WATER)) {
            var randomness = !isEllipse || random.nextDouble() > 0.05;
            var divisor = isEllipse ? 3 : 2;
            if (snowOnTop && !state.compare(Block.WATER)
                    && (double) heightDifference <= (double) random.nextInt(Math.max(1, height / divisor)) + (double) height * 0.6
                    && randomness) {
                level.setBlock(pos, Block.SNOW_BLOCK);
            } else {
                level.setBlock(pos, mainBlockState);
            }
        }
    }

    private static int getEllipseC(int yOff, int height, int shapeEllipseC) {
        var c = shapeEllipseC;
        if (yOff > 0 && height - yOff <= 3) {
            c = shapeEllipseC - (4 - (height - yOff));
        }

        return c;
    }

    private static double signedDistanceCircle(int xo, int zo, int radius, RandomSource random) {
        var off = 10.0F * clamp(random.nextFloat(), 0.2F, 0.8F) / (float) radius;
        return off + Math.pow(xo, 2.0) + Math.pow(zo, 2.0) - Math.pow(radius, 2.0);
    }

    private static double signedDistanceEllipse(int xo, int zo, int originX, int originZ, int a, int c, double angle) {
        return Math.pow(((xo - originX) * Math.cos(angle) - (zo - originZ) * Math.sin(angle)) / (double) a, 2.0)
                + Math.pow(((xo - originX) * Math.sin(angle) + (zo - originZ) * Math.cos(angle)) / (double) c, 2.0)
                - 1.0;
    }

    private static int heightDependentRadiusRound(RandomSource random, int yOff, int height, int width) {
        var k = 3.5F - random.nextFloat();
        var scale = (1.0F - (float) Math.pow(yOff, 2.0) / ((float) height * k)) * (float) width;
        if (height > 15 + random.nextInt(5)) {
            var tempYOff = yOff < 3 + random.nextInt(6) ? yOff / 2 : yOff;
            scale = (1.0F - (float) tempYOff / ((float) height * k * 0.4F)) * (float) width;
        }

        return (int) Math.ceil(scale / 2.0F);
    }

    private static int heightDependentRadiusEllipse(int yOff, int height, int width) {
        var scale = (1.0F - (float) Math.pow(yOff, 2.0) / (float) height) * (float) width;
        return (int) Math.ceil(scale / 2.0F);
    }

    private static int heightDependentRadiusSteep(RandomSource random, int yOff, int height, int width) {
        var k = 1.0F + random.nextFloat() / 2.0F;
        var scale = (1.0F - (float) yOff / ((float) height * k)) * (float) width;
        return (int) Math.ceil(scale / 2.0F);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    private static boolean isIcebergState(Block block) {
        return block.compare(Block.PACKED_ICE) || block.compare(Block.SNOW_BLOCK) || block.compare(Block.BLUE_ICE);
    }

    private static <T extends Block.Getter & Block.Setter> boolean belowIsAir(T level, BlockVec pos) {
        return level.getBlock(pos.add(0, -1, 0)).isAir();
    }

    private static <T extends Block.Getter & Block.Setter> void smooth(
            T level, BlockVec origin, int width, int height, boolean isEllipse, int shapeEllipseA) {
        var a = isEllipse ? shapeEllipseA : width / 2;

        for (var x = -a; x <= a; x++) {
            for (var z = -a; z <= a; z++) {
                for (var yOff = 0; yOff <= height; yOff++) {
                    var pos = origin.add(x, yOff, z);
                    var state = level.getBlock(pos);
                    if (isIcebergState(state) || state.compare(Block.SNOW)) {
                        if (belowIsAir(level, pos)) {
                            level.setBlock(pos, Block.AIR);
                            level.setBlock(pos.add(0, 1, 0), Block.AIR);
                        } else if (isIcebergState(state)) {
                            var west = level.getBlock(pos.add(-1, 0, 0));
                            var east = level.getBlock(pos.add(1, 0, 0));
                            var north = level.getBlock(pos.add(0, 0, -1));
                            var south = level.getBlock(pos.add(0, 0, 1));
                            var counter = 0;
                            if (!isIcebergState(west)) {
                                counter++;
                            }
                            if (!isIcebergState(east)) {
                                counter++;
                            }
                            if (!isIcebergState(north)) {
                                counter++;
                            }
                            if (!isIcebergState(south)) {
                                counter++;
                            }

                            if (counter >= 3) {
                                level.setBlock(pos, Block.AIR);
                            }
                        }
                    }
                }
            }
        }
    }
}
