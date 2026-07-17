package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.biome.BiomeClimate;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.surface.BiomeResolver;

import java.util.Set;

/**
 * Port of vanilla {@code SnowAndFreezeFeature}: the per-column top-layer pass
 * that freezes exposed water sources to ice and caps cold surfaces with a snow
 * layer, using the height-adjusted biome temperature at the motion-blocking
 * heightmap. Needs biome and heightmap lookups, so the generator invokes it
 * through the overload that carries the placement context.
 */
public final class FreezeTopLayerFeature implements Feature<NoneFeatureConfiguration> {
    private static final Set<String> CANNOT_SUPPORT_SNOW_LAYER = Set.of(
            "minecraft:ice", "minecraft:packed_ice", "minecraft:barrier");
    private static final Set<String> SUPPORT_OVERRIDE_SNOW_LAYER = Set.of(
            "minecraft:honey_block", "minecraft:soul_sand", "minecraft:mud");

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        return false;
    }

    public <T extends Block.Getter & Block.Setter> boolean place(
            FeaturePlaceContext<?, T> context,
            PlacementContext placementContext,
            BiomeResolver biomeResolver
    ) {
        var level = context.accessor();
        var origin = context.origin();
        var seaLevel = context.seaLevel();

        for (var offsetX = 0; offsetX < 16; offsetX++) {
            for (var offsetZ = 0; offsetZ < 16; offsetZ++) {
                var x = origin.blockX() + offsetX;
                var z = origin.blockZ() + offsetZ;
                var y = placementContext.getHeight(PlacementContext.HeightmapType.MOTION_BLOCKING, x, z);
                var topPosition = new BlockVec(x, y, z);
                var belowPosition = new BlockVec(x, y - 1, z);
                var biome = placementContext.biomeAt(topPosition);

                if (this.shouldFreeze(level, placementContext, biomeResolver, biome, belowPosition, seaLevel)) {
                    level.setBlock(belowPosition, Block.ICE);
                }

                if (this.shouldSnow(level, placementContext, biomeResolver, biome, topPosition, seaLevel)) {
                    level.setBlock(topPosition, Block.SNOW);
                    var belowState = level.getBlock(belowPosition);
                    if (belowState.getProperty("snowy") != null) {
                        level.setBlock(belowPosition, belowState.withProperty("snowy", "true"));
                    }
                }
            }
        }

        return true;
    }

    private boolean shouldFreeze(
            Block.Getter level,
            PlacementContext placementContext,
            BiomeResolver biomeResolver,
            Key biome,
            BlockVec position,
            int seaLevel
    ) {
        if (BiomeClimate.warmEnoughToRain(biomeResolver, biome, position.blockX(), position.blockY(), position.blockZ(), seaLevel)) {
            return false;
        }

        if (!placementContext.inWorldBounds(position)) {
            return false;
        }

        var state = level.getBlock(position);
        return state.compare(Block.WATER) && "0".equals(state.getProperty("level"));
    }

    private boolean shouldSnow(
            Block.Getter level,
            PlacementContext placementContext,
            BiomeResolver biomeResolver,
            Key biome,
            BlockVec position,
            int seaLevel
    ) {
        if (!biomeResolver.hasPrecipitation(biome)) {
            return false;
        }

        if (BiomeClimate.warmEnoughToRain(biomeResolver, biome, position.blockX(), position.blockY(), position.blockZ(), seaLevel)) {
            return false;
        }

        if (!placementContext.inWorldBounds(position)) {
            return false;
        }

        var state = level.getBlock(position);
        if (!state.isAir() && !state.compare(Block.SNOW)) {
            return false;
        }

        return this.snowCanSurvive(level, position);
    }

    private boolean snowCanSurvive(Block.Getter level, BlockVec position) {
        var below = level.getBlock(position.sub(0, 1, 0));
        if (CANNOT_SUPPORT_SNOW_LAYER.contains(below.name())) {
            return false;
        }

        if (SUPPORT_OVERRIDE_SNOW_LAYER.contains(below.name())) {
            return true;
        }

        if (below.compare(Block.SNOW) && "8".equals(below.getProperty("layers"))) {
            return true;
        }

        return below.registry().isSolid();
    }
}
