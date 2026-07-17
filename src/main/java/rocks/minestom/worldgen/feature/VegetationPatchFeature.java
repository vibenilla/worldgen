package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.VegetationPatchConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.HashSet;
import java.util.Set;

/**
 * Port of vanilla {@code VegetationPatchFeature}: replaces ground with a patch
 * (moss, clay, ...) and sprinkles a nested vegetation feature on top. Surface
 * positions are collected in a set with vanilla {@code BlockPos} hashing so
 * the vegetation pass consumes randoms in the same iteration order.
 */
public class VegetationPatchFeature implements Feature<VegetationPatchConfiguration> {

    /**
     * Coordinates hashed exactly like vanilla BlockPos so HashSet iteration
     * order matches vanilla's.
     */
    protected record VanillaPos(int x, int y, int z) {
        @Override
        public int hashCode() {
            return (this.y + this.z * 31) * 31 + this.x;
        }
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<VegetationPatchConfiguration, T> context) {
        var level = context.accessor();
        var config = context.config();
        var random = context.random();
        var origin = context.origin();
        var xRadius = config.xzRadius().sample(random) + 1;
        var zRadius = config.xzRadius().sample(random) + 1;
        var surface = this.placeGroundPatch(level, config, random, origin, xRadius, zRadius);
        this.distributeVegetation(context, level, config, random, surface);
        return !surface.isEmpty();
    }

    protected <T extends Block.Getter & Block.Setter> Set<VanillaPos> placeGroundPatch(T level,
            VegetationPatchConfiguration config, RandomSource random, BlockVec origin, int xRadius, int zRadius) {
        var inwards = config.ceiling() ? 1 : -1;
        var outwards = -inwards;
        var surface = new HashSet<VanillaPos>();

        for (var dx = -xRadius; dx <= xRadius; dx++) {
            var isXEdge = dx == -xRadius || dx == xRadius;

            for (var dz = -zRadius; dz <= zRadius; dz++) {
                var isZEdge = dz == -zRadius || dz == zRadius;
                var isEdge = isXEdge || isZEdge;
                var isCorner = isXEdge && isZEdge;
                var isEdgeButNotCorner = isEdge && !isCorner;
                if (isCorner) {
                    continue;
                }
                if (isEdgeButNotCorner && (config.extraEdgeColumnChance() == 0.0F || random.nextFloat() > config.extraEdgeColumnChance())) {
                    continue;
                }

                var x = origin.blockX() + dx;
                var y = origin.blockY();
                var z = origin.blockZ() + dz;
                for (var offset = 0; level.getBlock(x, y, z).isAir() && offset < config.verticalRange(); offset++) {
                    y += inwards;
                }

                for (var offset = 0; !level.getBlock(x, y, z).isAir() && offset < config.verticalRange(); offset++) {
                    y += outwards;
                }

                var belowY = y + inwards;
                var belowBlock = level.getBlock(x, belowY, z);
                if (level.getBlock(x, y, z).isAir() && belowBlock.isSolid()) {
                    var depth = config.depth().sample(random)
                            + (config.extraBottomBlockChance() > 0.0F && random.nextFloat() < config.extraBottomBlockChance() ? 1 : 0);
                    var groundPos = new VanillaPos(x, belowY, z);
                    if (this.placeGround(level, config, random, x, belowY, z, inwards, depth)) {
                        surface.add(groundPos);
                    }
                }
            }
        }

        return surface;
    }

    private <T extends Block.Getter & Block.Setter> boolean placeGround(T level, VegetationPatchConfiguration config,
            RandomSource random, int x, int startY, int z, int inwards, int depth) {
        var y = startY;
        for (var index = 0; index < depth; index++) {
            var stateToPlace = config.groundState().getState(level, random, new BlockVec(x, y, z));
            var currentState = level.getBlock(x, y, z);
            if (!stateToPlace.compare(currentState)) {
                if (!config.replaceable().contains(currentState.key())) {
                    return index != 0;
                }

                level.setBlock(x, y, z, stateToPlace);
                y += inwards;
            }
        }

        return true;
    }

    protected <T extends Block.Getter & Block.Setter> void distributeVegetation(
            FeaturePlaceContext<VegetationPatchConfiguration, T> context, T level,
            VegetationPatchConfiguration config, RandomSource random, Set<VanillaPos> surface) {
        for (var surfacePos : surface) {
            if (config.vegetationChance() > 0.0F && random.nextFloat() < config.vegetationChance()) {
                this.placeVegetation(context, level, config, random, surfacePos);
            }
        }
    }

    protected <T extends Block.Getter & Block.Setter> boolean placeVegetation(
            FeaturePlaceContext<VegetationPatchConfiguration, T> context, T level,
            VegetationPatchConfiguration config, RandomSource random, VanillaPos surfacePos) {
        var outwards = config.ceiling() ? 1 : -1;
        var vegetationOrigin = new BlockVec(surfacePos.x(), surfacePos.y() - outwards, surfacePos.z());
        var vegetationContext = new FeaturePlaceContext<>(
                level,
                random,
                vegetationOrigin,
                context.config(),
                context.worldSeed(),
                context.minY(),
                context.maxY());
        return RandomSelectorFeature.placePlacedFeature(vegetationContext, config.loader(), config.vegetationFeature());
    }
}
