package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Vanilla {@code PosRuleTest}: position-based rule predicates measuring
 * against the structure reference position.
 */
public interface PosRuleTest {
    boolean test(BlockVec inTemplatePos, BlockVec worldPos, BlockVec worldReference, RandomSource random);

    record PosAlwaysTrueTest() implements PosRuleTest {
        public static final PosAlwaysTrueTest INSTANCE = new PosAlwaysTrueTest();

        @Override
        public boolean test(BlockVec inTemplatePos, BlockVec worldPos, BlockVec worldReference, RandomSource random) {
            return true;
        }
    }

    record LinearPosTest(float minChance, float maxChance, int minDist, int maxDist) implements PosRuleTest {
        @Override
        public boolean test(BlockVec inTemplatePos, BlockVec worldPos, BlockVec worldReference, RandomSource random) {
            var dist = Math.abs(worldPos.blockX() - worldReference.blockX())
                    + Math.abs(worldPos.blockY() - worldReference.blockY())
                    + Math.abs(worldPos.blockZ() - worldReference.blockZ());
            return random.nextFloat() <= chance(dist, this.minDist, this.maxDist, this.minChance, this.maxChance);
        }
    }

    record AxisAlignedLinearPosTest(float minChance, float maxChance, int minDist, int maxDist, String axis)
            implements PosRuleTest {
        @Override
        public boolean test(BlockVec inTemplatePos, BlockVec worldPos, BlockVec worldReference, RandomSource random) {
            var dist = switch (this.axis) {
                case "x" -> Math.abs(worldPos.blockX() - worldReference.blockX());
                case "z" -> Math.abs(worldPos.blockZ() - worldReference.blockZ());
                default -> Math.abs(worldPos.blockY() - worldReference.blockY());
            };
            return random.nextFloat() <= chance(dist, this.minDist, this.maxDist, this.minChance, this.maxChance);
        }
    }

    private static float chance(int dist, int minDist, int maxDist, float minChance, float maxChance) {
        // Mth.clampedLerp(Mth.inverseLerp(dist, minDist, maxDist), minChance, maxChance)
        var delta = ((float) dist - (float) minDist) / ((float) maxDist - (float) minDist);
        var clamped = delta < 0.0F ? 0.0F : Math.min(delta, 1.0F);
        return minChance + clamped * (maxChance - minChance);
    }
}
