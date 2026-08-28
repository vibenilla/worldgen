package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.LakeConfiguration;

/**
 * Port of vanilla's {@code LakeFeature}. Random call order matches vanilla
 * exactly. The biome-driven ice pass for freezing water lake surfaces is not
 * applied (it consumes no random calls in vanilla).
 */
public final class LakeFeature implements Feature<LakeConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<LakeConfiguration, T> context) {
        var origin = context.origin();
        var level = context.accessor();
        var random = context.random();
        var config = context.config();
        if (origin.blockY() <= context.minY() + 4) {
            return false;
        }

        origin = origin.add(-8, -4, -8);
        var predicateContext = Feature.predicateContext(level, context.minY(), context.maxY());
        var grid = new boolean[2048];
        var spots = random.nextInt(4) + 4;

        for (var spot = 0; spot < spots; spot++) {
            var xr = random.nextDouble() * 6.0D + 3.0D;
            var yr = random.nextDouble() * 4.0D + 2.0D;
            var zr = random.nextDouble() * 6.0D + 3.0D;
            var xp = random.nextDouble() * (16.0D - xr - 2.0D) + 1.0D + xr / 2.0D;
            var yp = random.nextDouble() * (8.0D - yr - 4.0D) + 2.0D + yr / 2.0D;
            var zp = random.nextDouble() * (16.0D - zr - 2.0D) + 1.0D + zr / 2.0D;

            for (var xx = 1; xx < 15; xx++) {
                for (var zz = 1; zz < 15; zz++) {
                    for (var yy = 1; yy < 7; yy++) {
                        var xd = (xx - xp) / (xr / 2.0D);
                        var yd = (yy - yp) / (yr / 2.0D);
                        var zd = (zz - zp) / (zr / 2.0D);
                        var distance = xd * xd + yd * yd + zd * zd;
                        if (distance < 1.0D) {
                            grid[(xx * 16 + zz) * 8 + yy] = true;
                        }
                    }
                }
            }
        }

        var fluid = config.fluid().getState(level, random, origin);

        for (var xx = 0; xx < 16; xx++) {
            for (var zz = 0; zz < 16; zz++) {
                for (var yy = 0; yy < 8; yy++) {
                    var isBorder = !grid[(xx * 16 + zz) * 8 + yy]
                            && (xx < 15 && grid[((xx + 1) * 16 + zz) * 8 + yy]
                                    || xx > 0 && grid[((xx - 1) * 16 + zz) * 8 + yy]
                                    || zz < 15 && grid[(xx * 16 + zz + 1) * 8 + yy]
                                    || zz > 0 && grid[(xx * 16 + (zz - 1)) * 8 + yy]
                                    || yy < 7 && grid[(xx * 16 + zz) * 8 + yy + 1]
                                    || yy > 0 && grid[(xx * 16 + zz) * 8 + (yy - 1)]);
                    if (!isBorder) {
                        continue;
                    }

                    var offsetPos = origin.add(xx, yy, zz);
                    var blockState = level.getBlock(offsetPos);
                    if (yy >= 4 && blockState.liquid()) {
                        return false;
                    }

                    if (yy < 4 && !blockState.solid() && !blockState.compare(fluid, Block.Comparator.STATE)) {
                        return false;
                    }

                    if (!config.canPlaceFeature().test(predicateContext, offsetPos)) {
                        return false;
                    }
                }
            }
        }

        for (var xx = 0; xx < 16; xx++) {
            for (var zz = 0; zz < 16; zz++) {
                for (var yy = 0; yy < 8; yy++) {
                    if (!grid[(xx * 16 + zz) * 8 + yy]) {
                        continue;
                    }

                    var placePos = origin.add(xx, yy, zz);
                    if (config.canReplaceWithAirOrFluid().test(predicateContext, placePos)) {
                        var placeAir = yy >= 4;
                        level.setBlock(placePos, placeAir ? Block.CAVE_AIR : fluid);
                    }
                }
            }
        }

        var barrier = config.barrier().getState(level, random, origin);
        if (!barrier.air()) {
            for (var xx = 0; xx < 16; xx++) {
                for (var zz = 0; zz < 16; zz++) {
                    for (var yy = 0; yy < 8; yy++) {
                        var isBorder = !grid[(xx * 16 + zz) * 8 + yy]
                                && (xx < 15 && grid[((xx + 1) * 16 + zz) * 8 + yy]
                                        || xx > 0 && grid[((xx - 1) * 16 + zz) * 8 + yy]
                                        || zz < 15 && grid[(xx * 16 + zz + 1) * 8 + yy]
                                        || zz > 0 && grid[(xx * 16 + (zz - 1)) * 8 + yy]
                                        || yy < 7 && grid[(xx * 16 + zz) * 8 + yy + 1]
                                        || yy > 0 && grid[(xx * 16 + zz) * 8 + (yy - 1)]);
                        if (isBorder && (yy < 4 || random.nextInt(2) != 0)) {
                            var offsetPos = origin.add(xx, yy, zz);
                            var blockState = level.getBlock(offsetPos);
                            if (blockState.solid() && config.canReplaceWithBarrier().test(predicateContext, offsetPos)) {
                                level.setBlock(offsetPos, barrier);
                            }
                        }
                    }
                }
            }
        }

        return true;
    }
}
