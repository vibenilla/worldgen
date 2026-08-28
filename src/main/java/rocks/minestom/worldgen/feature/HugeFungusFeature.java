package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.HugeFungusConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.Set;

/**
 * Port of vanilla {@code HugeFungusFeature}: huge crimson/warped fungi (stem +
 * wart-block hat with shroomlight decor and hanging weeping vines).
 */
public final class HugeFungusFeature implements Feature<HugeFungusConfiguration> {
    private static final float HUGE_PROBABILITY = 0.06F;

    /**
     * Blocks whose state reports {@code canBeReplaced()} in vanilla 26.2 (the
     * {@code replaceable} block property), excluding the air blocks which are
     * covered by {@link Block#isAir()}.
     */
    static final Set<String> REPLACEABLE = Set.of(
            "minecraft:water", "minecraft:lava", "minecraft:bubble_column",
            "minecraft:fire", "minecraft:soul_fire", "minecraft:snow",
            "minecraft:vine", "minecraft:glow_lichen", "minecraft:resin_clump",
            "minecraft:light", "minecraft:structure_void",
            "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
            "minecraft:dead_bush", "minecraft:bush", "minecraft:firefly_bush",
            "minecraft:short_dry_grass", "minecraft:tall_dry_grass", "minecraft:leaf_litter",
            "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:hanging_roots",
            "minecraft:warped_roots", "minecraft:crimson_roots", "minecraft:nether_sprouts");

    /** Vanilla {@code BlockStateBase.canBeReplaced()}. */
    static boolean canBeReplaced(Block block) {
        return block.air() || REPLACEABLE.contains(block.name());
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<HugeFungusConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var random = context.random();
        var config = context.config();

        var belowState = level.getBlock(origin.sub(0, 1, 0));
        if (!belowState.compare(config.validBaseState())) {
            return false;
        }

        var totalHeight = WeepingVinesFeature.nextIntInclusive(random, 4, 13);
        if (random.nextInt(12) == 0) {
            totalHeight *= 2;
        }

        if (!config.planted()) {
            // Vanilla: chunkGenerator.getGenDepth() (nether noise settings
            // span 0..127, so genDepth is the world height handed to us)
            var genDepth = context.maxY() - context.minY() + 1;
            if (origin.blockY() + totalHeight + 1 >= genDepth) {
                return false;
            }
        }

        var isHuge = !config.planted() && random.nextFloat() < HUGE_PROBABILITY;
        var predicateContext = Feature.predicateContext(level, context.minY(), context.maxY());
        level.setBlock(origin, Block.AIR);
        placeStem(level, predicateContext, random, config, origin, totalHeight, isHuge);
        placeHat(level, predicateContext, random, config, origin, totalHeight, isHuge);
        return true;
    }

    private static boolean isReplaceable(
            Block.Getter level, PlacementContext predicateContext, BlockVec pos,
            HugeFungusConfiguration config, boolean checkNonReplaceablePlants) {
        if (canBeReplaced(level.getBlock(pos))) {
            return true;
        }

        return checkNonReplaceablePlants && config.replaceableBlocks().test(predicateContext, pos);
    }

    private static <T extends Block.Getter & Block.Setter> void placeStem(
            T level,
            PlacementContext predicateContext,
            RandomSource random,
            HugeFungusConfiguration config,
            BlockVec surfaceOrigin,
            int totalHeight,
            boolean isHuge
    ) {
        var stem = config.stemState();
        var stemRadius = isHuge ? 1 : 0;

        for (var dx = -stemRadius; dx <= stemRadius; dx++) {
            for (var dz = -stemRadius; dz <= stemRadius; dz++) {
                var cornerOfHugeStem = isHuge && Math.abs(dx) == stemRadius && Math.abs(dz) == stemRadius;

                for (var dy = 0; dy < totalHeight; dy++) {
                    var blockPos = surfaceOrigin.add(dx, dy, dz);
                    if (isReplaceable(level, predicateContext, blockPos, config, true)) {
                        if (config.planted()) {
                            if (!level.getBlock(blockPos.sub(0, 1, 0)).air()) {
                                // Vanilla destroyBlock: clear before replacing
                                level.setBlock(blockPos, Block.AIR);
                            }

                            level.setBlock(blockPos, stem);
                        } else if (cornerOfHugeStem) {
                            if (random.nextFloat() < 0.1F) {
                                level.setBlock(blockPos, stem);
                            }
                        } else {
                            level.setBlock(blockPos, stem);
                        }
                    }
                }
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> void placeHat(
            T level,
            PlacementContext predicateContext,
            RandomSource random,
            HugeFungusConfiguration config,
            BlockVec surfaceOrigin,
            int totalHeight,
            boolean isHuge
    ) {
        var placeVines = config.hatState().compare(Block.NETHER_WART_BLOCK);
        var hatHeight = Math.min(random.nextInt(1 + totalHeight / 3) + 5, totalHeight);
        var hatStartY = totalHeight - hatHeight;

        for (var dy = hatStartY; dy <= totalHeight; dy++) {
            var radius = dy < totalHeight - random.nextInt(3) ? 2 : 1;
            if (hatHeight > 8 && dy < hatStartY + 4) {
                radius = 3;
            }

            if (isHuge) {
                radius++;
            }

            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    var isEdgeX = dx == -radius || dx == radius;
                    var isEdgeZ = dz == -radius || dz == radius;
                    var inside = !isEdgeX && !isEdgeZ && dy != totalHeight;
                    var corner = isEdgeX && isEdgeZ;
                    var isHatBottom = dy < hatStartY + 3;
                    var blockPos = surfaceOrigin.add(dx, dy, dz);
                    if (isReplaceable(level, predicateContext, blockPos, config, false)) {
                        if (config.planted() && !level.getBlock(blockPos.sub(0, 1, 0)).air()) {
                            // Vanilla destroyBlock: clear before replacing
                            level.setBlock(blockPos, Block.AIR);
                        }

                        if (isHatBottom) {
                            if (!inside) {
                                placeHatDropBlock(level, random, blockPos, config.hatState(), placeVines);
                            }
                        } else if (inside) {
                            placeHatBlock(level, random, config, blockPos, 0.1F, 0.2F, placeVines ? 0.1F : 0.0F);
                        } else if (corner) {
                            placeHatBlock(level, random, config, blockPos, 0.01F, 0.7F, placeVines ? 0.083F : 0.0F);
                        } else {
                            placeHatBlock(level, random, config, blockPos, 5.0E-4F, 0.98F, placeVines ? 0.07F : 0.0F);
                        }
                    }
                }
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> void placeHatBlock(
            T level,
            RandomSource random,
            HugeFungusConfiguration config,
            BlockVec blockPos,
            float decorBlockProbability,
            float hatBlockProbability,
            float vinesProbability
    ) {
        if (random.nextFloat() < decorBlockProbability) {
            level.setBlock(blockPos, config.decorState());
        } else if (random.nextFloat() < hatBlockProbability) {
            level.setBlock(blockPos, config.hatState());
            if (random.nextFloat() < vinesProbability) {
                tryPlaceWeepingVines(blockPos, level, random);
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> void placeHatDropBlock(
            T level, RandomSource random, BlockVec blockPos, Block hatState, boolean placeVines) {
        if (level.getBlock(blockPos.sub(0, 1, 0)).compare(hatState)) {
            level.setBlock(blockPos, hatState);
        } else if (random.nextFloat() < 0.15F) {
            level.setBlock(blockPos, hatState);
            if (placeVines && random.nextInt(11) == 0) {
                tryPlaceWeepingVines(blockPos, level, random);
            }
        }
    }

    private static <T extends Block.Getter & Block.Setter> void tryPlaceWeepingVines(
            BlockVec hatBlockPos, T level, RandomSource random) {
        var placePos = hatBlockPos.sub(0, 1, 0);
        if (level.getBlock(placePos).air()) {
            var goalVineHeight = WeepingVinesFeature.nextIntInclusive(random, 1, 5);
            if (random.nextInt(7) == 0) {
                goalVineHeight *= 2;
            }

            WeepingVinesFeature.placeWeepingVinesColumn(level, random, placePos, goalVineHeight, 23, 25);
        }
    }
}
