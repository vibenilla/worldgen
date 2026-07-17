package rocks.minestom.worldgen.terrain;

import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.random.PositionalRandomFactory;

/**
 * Port of vanilla's {@code OreVeinifier}. Places the large ore vein structures
 * (deepslate iron veins with tuff filler, copper veins with granite filler) driven
 * by the vein_toggle/vein_ridged/vein_gap router channels.
 */
public final class OreVeinifier {
    private static final float VEININESS_THRESHOLD = 0.4F;
    private static final int EDGE_ROUNDOFF_BEGIN = 20;
    private static final double MAX_EDGE_ROUNDOFF = 0.2;
    private static final float VEIN_SOLIDNESS = 0.7F;
    private static final float MIN_RICHNESS = 0.1F;
    private static final float MAX_RICHNESS = 0.3F;
    private static final float MAX_RICHNESS_THRESHOLD = 0.6F;
    private static final float CHANCE_OF_RAW_ORE_BLOCK = 0.02F;
    private static final float SKIP_ORE_IF_GAP_NOISE_IS_BELOW = -0.3F;

    private OreVeinifier() {
    }

    /**
     * Returns the vein block for the current position, or {@code null} when the
     * default block should be used instead.
     */
    @FunctionalInterface
    public interface BlockFiller {
        @Nullable
        Block calculate(DensityFunction.Context context);
    }

    public static BlockFiller create(
            DensityFunction veinToggle,
            DensityFunction veinRidged,
            DensityFunction veinGap,
            PositionalRandomFactory oreVeinsPositionalRandomFactory) {
        return context -> {
            var oreVeininessNoiseValue = veinToggle.compute(context);
            var posY = context.blockY();
            var veinType = oreVeininessNoiseValue > 0.0 ? VeinType.COPPER : VeinType.IRON;
            var veininessRidged = Math.abs(oreVeininessNoiseValue);
            var distanceFromTop = veinType.maxY - posY;
            var distanceFromBottom = posY - veinType.minY;
            if (distanceFromBottom < 0 || distanceFromTop < 0) {
                return null;
            }
            var distanceFromEdge = Math.min(distanceFromTop, distanceFromBottom);
            var edgeRoundoff = clampedMap(distanceFromEdge, 0.0, EDGE_ROUNDOFF_BEGIN, -MAX_EDGE_ROUNDOFF, 0.0);
            if (veininessRidged + edgeRoundoff < VEININESS_THRESHOLD) {
                return null;
            }
            var positionalRandom = oreVeinsPositionalRandomFactory.at(context.blockX(), posY, context.blockZ());
            if (positionalRandom.nextFloat() > VEIN_SOLIDNESS) {
                return null;
            }
            if (veinRidged.compute(context) >= 0.0) {
                return null;
            }
            var richness = clampedMap(veininessRidged, VEININESS_THRESHOLD, MAX_RICHNESS_THRESHOLD, MIN_RICHNESS, MAX_RICHNESS);
            if (positionalRandom.nextFloat() < richness && veinGap.compute(context) > SKIP_ORE_IF_GAP_NOISE_IS_BELOW) {
                return positionalRandom.nextFloat() < CHANCE_OF_RAW_ORE_BLOCK ? veinType.rawOreBlock : veinType.ore;
            }
            return veinType.filler;
        };
    }

    // Exact port of Mth.clampedMap (clampedLerp over inverseLerp)
    private static double clampedMap(double value, double fromMin, double fromMax, double toMin, double toMax) {
        var delta = (value - fromMin) / (fromMax - fromMin);
        if (delta < 0.0) {
            return toMin;
        }
        if (delta > 1.0) {
            return toMax;
        }
        return VMath.lerp(delta, toMin, toMax);
    }

    private enum VeinType {
        COPPER(Block.COPPER_ORE, Block.RAW_COPPER_BLOCK, Block.GRANITE, 0, 50),
        IRON(Block.DEEPSLATE_IRON_ORE, Block.RAW_IRON_BLOCK, Block.TUFF, -60, -8);

        private final Block ore;
        private final Block rawOreBlock;
        private final Block filler;
        private final int minY;
        private final int maxY;

        VeinType(Block ore, Block rawOreBlock, Block filler, int minY, int maxY) {
            this.ore = ore;
            this.rawOreBlock = rawOreBlock;
            this.filler = filler;
            this.minY = minY;
            this.maxY = maxY;
        }
    }
}
