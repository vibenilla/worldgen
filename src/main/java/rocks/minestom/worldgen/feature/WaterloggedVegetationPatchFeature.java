package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
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

        var debug = VegetationPatchFeature.debugMatch(origin.blockX() >> 4, origin.blockZ() >> 4);
        for (var surfacePos : surface) {
            var exposed = isExposed(level, surfacePos);
            if (debug) {
                var neighbors = new StringBuilder();
                var names = new String[] {"north", "east", "south", "west", "down"};
                var offsets = new int[][] {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, -1, 0}};
                for (var index = 0; index < names.length; index++) {
                    neighbors.append(' ').append(names[index]).append('=').append(level.getBlock(
                            surfacePos.x() + offsets[index][0],
                            surfacePos.y() + offsets[index][1],
                            surfacePos.z() + offsets[index][2]).name());
                }
                System.out.println("VWATER " + surfacePos.x() + "," + surfacePos.y() + "," + surfacePos.z()
                        + " exposed=" + exposed + neighbors);
            }
            if (!exposed) {
                waterSurface.add(surfacePos);
            }
        }

        for (var surfacePos : waterSurface) {
            level.setBlock(surfacePos.x(), surfacePos.y(), surfacePos.z(), Block.WATER);
        }

        return waterSurface;
    }

    private static <T extends Block.Getter & Block.Setter> boolean isExposed(T level, VanillaPos pos) {
        return isExposedDirection(level, pos.x(), pos.y(), pos.z() - 1, BlockFace.SOUTH)
                || isExposedDirection(level, pos.x() + 1, pos.y(), pos.z(), BlockFace.WEST)
                || isExposedDirection(level, pos.x(), pos.y(), pos.z() + 1, BlockFace.NORTH)
                || isExposedDirection(level, pos.x() - 1, pos.y(), pos.z(), BlockFace.EAST)
                || isExposedDirection(level, pos.x(), pos.y() - 1, pos.z(), BlockFace.TOP);
    }

    /** Vanilla's {@code BlockState.isFaceSturdy} (default {@code SupportType.FULL}), approximated with the collision shape. */
    private static <T extends Block.Getter & Block.Setter> boolean isExposedDirection(T level, int x, int y, int z, BlockFace faceTowardOrigin) {
        return !level.getBlock(x, y, z).registry().collisionShape().isFaceFull(faceTowardOrigin);
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
