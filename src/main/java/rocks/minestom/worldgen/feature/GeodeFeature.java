package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.GeodeConfiguration;
import rocks.minestom.worldgen.noise.NormalNoise;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Port of vanilla {@code GeodeFeature}: an amethyst geode carved as a set of
 * inverse-square-root-weighted layers around a handful of random points, with
 * an optional crack and crystal bud growths on the inner air pocket walls.
 * Random call order matches vanilla exactly.
 */
public final class GeodeFeature implements Feature<GeodeConfiguration> {

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<GeodeConfiguration, T> context) {
        var config = context.config();
        var random = context.random();
        var origin = context.origin();
        var level = context.accessor();
        var minGenOffset = config.minGenOffset();
        var maxGenOffset = config.maxGenOffset();
        var points = new ArrayList<PointOffset>();
        var numPoints = config.distributionPoints().sample(random);
        var noiseRandom = new WorldgenRandom(new LegacyRandomSource(context.worldSeed()));
        var noise = NormalNoise.create(noiseRandom, new NormalNoise.NoiseParameters(-4, new double[]{1.0}));
        var crackPoints = new ArrayList<BlockVec>();
        var crackSizeAdjustment = (double) numPoints / (double) config.outerWallDistance().maxValue();
        var layerSettings = config.geodeLayerSettings();
        var blockSettings = config.geodeBlockSettings();
        var crackSettings = config.geodeCrackSettings();
        var innerAir = 1.0 / Math.sqrt(layerSettings.filling());
        var innermostBlockLayer = 1.0 / Math.sqrt(layerSettings.innerLayer() + crackSizeAdjustment);
        var innerCrust = 1.0 / Math.sqrt(layerSettings.middleLayer() + crackSizeAdjustment);
        var outerCrust = 1.0 / Math.sqrt(layerSettings.outerLayer() + crackSizeAdjustment);
        var crackSize = 1.0 / Math.sqrt(crackSettings.baseCrackSize() + random.nextDouble() / 2.0
                + (numPoints > 3 ? crackSizeAdjustment : 0.0));
        var shouldGenerateCrack = (double) random.nextFloat() < crackSettings.generateCrackChance();
        var numInvalidPoints = 0;

        for (var i = 0; i < numPoints; i++) {
            var x = config.outerWallDistance().sample(random);
            var y = config.outerWallDistance().sample(random);
            var z = config.outerWallDistance().sample(random);
            var pos = origin.add(x, y, z);
            var state = level.getBlock(pos);
            if (state.air() || blockSettings.invalidBlocks().contains(state.key())) {
                if (++numInvalidPoints > config.invalidBlocksThreshold()) {
                    return false;
                }
            }

            points.add(new PointOffset(pos, config.pointOffset().sample(random)));
        }

        if (shouldGenerateCrack) {
            var offsetIndex = random.nextInt(4);
            var crackOffset = numPoints * 2 + 1;
            if (offsetIndex == 0) {
                crackPoints.add(origin.add(crackOffset, 7, 0));
                crackPoints.add(origin.add(crackOffset, 5, 0));
                crackPoints.add(origin.add(crackOffset, 1, 0));
            } else if (offsetIndex == 1) {
                crackPoints.add(origin.add(0, 7, crackOffset));
                crackPoints.add(origin.add(0, 5, crackOffset));
                crackPoints.add(origin.add(0, 1, crackOffset));
            } else if (offsetIndex == 2) {
                crackPoints.add(origin.add(crackOffset, 7, crackOffset));
                crackPoints.add(origin.add(crackOffset, 5, crackOffset));
                crackPoints.add(origin.add(crackOffset, 1, crackOffset));
            } else {
                crackPoints.add(origin.add(0, 7, 0));
                crackPoints.add(origin.add(0, 5, 0));
                crackPoints.add(origin.add(0, 1, 0));
            }
        }

        var potentialCrystalPlacements = new ArrayList<BlockVec>();
        var cannotReplace = blockSettings.cannotReplace();
        Predicate<Block> canReplace = state -> !cannotReplace.contains(state.key());

        for (var z = minGenOffset; z <= maxGenOffset; z++) {
            for (var y = minGenOffset; y <= maxGenOffset; y++) {
                for (var x = minGenOffset; x <= maxGenOffset; x++) {
                    var pointInside = origin.add(x, y, z);
                    var noiseOffset = noise.getValue(pointInside.blockX(), pointInside.blockY(), pointInside.blockZ())
                            * config.noiseMultiplier();
                    var distSumShell = 0.0;
                    var distSumCrack = 0.0;

                    for (var point : points) {
                        distSumShell += invSqrt(pointInside.distanceSquared(point.position()) + point.offset()) + noiseOffset;
                    }

                    for (var point : crackPoints) {
                        distSumCrack += invSqrt(pointInside.distanceSquared(point) + crackSettings.crackPointOffset()) + noiseOffset;
                    }

                    if (!(distSumShell < outerCrust)) {
                        if (shouldGenerateCrack && distSumCrack >= crackSize && distSumShell < innerAir) {
                            safeSetBlock(level, pointInside, Block.AIR, canReplace);
                        } else if (distSumShell >= innerAir) {
                            safeSetBlock(level, pointInside, blockSettings.fillingProvider().getState(level, random, pointInside), canReplace);
                        } else if (distSumShell >= innermostBlockLayer) {
                            var useAlternateLayer = (double) random.nextFloat() < config.useAlternateLayer0Chance();
                            if (useAlternateLayer) {
                                safeSetBlock(level, pointInside,
                                        blockSettings.alternateInnerLayerProvider().getState(level, random, pointInside), canReplace);
                            } else {
                                safeSetBlock(level, pointInside,
                                        blockSettings.innerLayerProvider().getState(level, random, pointInside), canReplace);
                            }

                            if ((!config.placementsRequireLayer0Alternate() || useAlternateLayer)
                                    && (double) random.nextFloat() < config.usePotentialPlacementsChance()) {
                                potentialCrystalPlacements.add(pointInside);
                            }
                        } else if (distSumShell >= innerCrust) {
                            safeSetBlock(level, pointInside, blockSettings.middleLayerProvider().getState(level, random, pointInside), canReplace);
                        } else if (distSumShell >= outerCrust) {
                            safeSetBlock(level, pointInside, blockSettings.outerLayerProvider().getState(level, random, pointInside), canReplace);
                        }
                    }
                }
            }
        }

        var innerPlacements = blockSettings.innerPlacements();

        for (var crystalPos : potentialCrystalPlacements) {
            var blockState = innerPlacements.get(random.nextInt(innerPlacements.size()));

            for (var direction : Direction.values()) {
                if (blockState.getProperty("facing") != null) {
                    blockState = blockState.withProperty("facing", direction.serializedName());
                }

                var placePos = direction.relative(crystalPos);
                var placeState = level.getBlock(placePos);
                if (blockState.getProperty("waterlogged") != null) {
                    blockState = blockState.withProperty("waterlogged", String.valueOf(isSourceWater(placeState)));
                }

                if (canClusterGrowAtState(placeState)) {
                    safeSetBlock(level, placePos, blockState, canReplace);
                    break;
                }
            }
        }

        return true;
    }

    private static <T extends Block.Getter & Block.Setter> void safeSetBlock(T level, BlockVec pos, Block state, Predicate<Block> canReplace) {
        if (canReplace.test(level.getBlock(pos))) {
            level.setBlock(pos, state);
        }
    }

    /** Vanilla {@code Mth.invSqrt(double)}: exactly {@code 1.0 / Math.sqrt(x)}. */
    private static double invSqrt(double value) {
        return 1.0 / Math.sqrt(value);
    }

    /** Vanilla {@code BuddingAmethystBlock.canClusterGrowAtState}. */
    private static boolean canClusterGrowAtState(Block state) {
        return state.air() || (state.compare(Block.WATER) && isFullWater(state));
    }

    private static boolean isFullWater(Block state) {
        var level = waterLevel(state);
        return level == 0 || level >= 8;
    }

    private static boolean isSourceWater(Block state) {
        return state.compare(Block.WATER) && waterLevel(state) == 0;
    }

    private static int waterLevel(Block state) {
        var property = state.getProperty("level");
        return property == null ? 0 : Integer.parseInt(property);
    }

    private record PointOffset(BlockVec position, int offset) {
    }
}
