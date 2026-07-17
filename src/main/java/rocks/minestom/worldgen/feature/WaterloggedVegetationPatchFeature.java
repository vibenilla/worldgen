package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.VegetationPatchConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Port of vanilla {@code WaterloggedVegetationPatchFeature}: a vegetation patch
 * whose interior fills with water (lush cave clay pools), waterlogging placed
 * vegetation.
 */
public final class WaterloggedVegetationPatchFeature extends VegetationPatchFeature {

    @Override
    protected <T extends Block.Getter & Block.Setter> Set<VanillaPos> placeGroundPatch(T level,
            VegetationPatchConfiguration config, RandomSource random, BlockVec origin, int xRadius, int zRadius) {
        var surface = super.placeGroundPatch(level, config, random, origin, xRadius, zRadius);
        var waterSurface = new HashSet<VanillaPos>();

        for (var surfacePos : surface) {
            if (!isExposed(level, surfacePos)) {
                waterSurface.add(surfacePos);
            }
        }

        for (var surfacePos : waterSurface) {
            level.setBlock(surfacePos.x(), surfacePos.y(), surfacePos.z(), Block.WATER);
        }

        return waterSurface;
    }

    private static <T extends Block.Getter & Block.Setter> boolean isExposed(T level, VanillaPos pos) {
        return isExposedDirection(level, pos.x(), pos.y(), pos.z() - 1)
                || isExposedDirection(level, pos.x() + 1, pos.y(), pos.z())
                || isExposedDirection(level, pos.x(), pos.y(), pos.z() + 1)
                || isExposedDirection(level, pos.x() - 1, pos.y(), pos.z())
                || isExposedDirection(level, pos.x(), pos.y() - 1, pos.z());
    }

    private static <T extends Block.Getter & Block.Setter> boolean isExposedDirection(T level, int x, int y, int z) {
        return !level.getBlock(x, y, z).isSolid();
    }

    @Override
    protected <T extends Block.Getter & Block.Setter> boolean placeVegetation(
            FeaturePlaceContext<VegetationPatchConfiguration, T> context, T level,
            VegetationPatchConfiguration config, RandomSource random, VanillaPos surfacePos) {
        // The surface block is the water pool itself; vanilla shifts down one so
        // the vegetation lands inside the water, then waterlogs it
        var below = new VanillaPos(surfacePos.x(), surfacePos.y() - 1, surfacePos.z());
        if (super.placeVegetation(context, level, config, random, below)) {
            var placed = level.getBlock(surfacePos.x(), surfacePos.y(), surfacePos.z());
            if ("false".equals(placed.getProperty("waterlogged"))) {
                level.setBlock(surfacePos.x(), surfacePos.y(), surfacePos.z(), placed.withProperty("waterlogged", "true"));
            }
            return true;
        }
        return false;
    }
}
