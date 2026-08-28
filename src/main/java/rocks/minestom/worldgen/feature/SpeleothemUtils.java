package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Port of vanilla's {@code SpeleothemUtils}, reduced to the helpers used by
 * {@code minecraft:speleothem} and {@code minecraft:speleothem_cluster}.
 */
public final class SpeleothemUtils {
    /** Vanilla Mth sin lookup table, needed for exact circle embedding checks. */
    private static final float[] SIN = new float[65536];

    static {
        for (var index = 0; index < SIN.length; index++) {
            SIN[index] = (float) Math.sin(index * Math.PI * 2.0D / 65536.0D);
        }
    }

    private SpeleothemUtils() {
    }

    static float sin(float value) {
        return SIN[(int) (value * 10430.378F) & 65535];
    }

    static float cos(float value) {
        return SIN[(int) (value * 10430.378F + 16384.0F) & 65535];
    }

    /** Vanilla {@code SpeleothemUtils.getSpeleothemHeight} (large speleothem profile). */
    static double getSpeleothemHeight(double xzDistanceFromCenter, double speleothemRadius, double scale, double bluntness) {
        if (xzDistanceFromCenter < bluntness) {
            xzDistanceFromCenter = bluntness;
        }

        var r = xzDistanceFromCenter / speleothemRadius * 0.384D;
        var part1 = 0.75D * Math.pow(r, 1.3333333333333333D);
        var part2 = Math.pow(r, 0.6666666666666666D);
        var part3 = 0.3333333333333333D * Math.log(r);
        var heightRelativeToMaxRadius = Math.max(scale * (part1 - part2 - part3), 0.0D);
        return heightRelativeToMaxRadius / 0.384D * speleothemRadius;
    }

    static boolean isCircleMostlyEmbeddedInStone(Block.Getter level, BlockVec center, int xzRadius) {
        if (isEmptyOrWaterOrLava(level.getBlock(center))) {
            return false;
        }

        var angleIncrement = 6.0F / xzRadius;
        for (var angle = 0.0F; angle < (float) (Math.PI * 2); angle += angleIncrement) {
            var dx = (int) (cos(angle) * xzRadius);
            var dz = (int) (sin(angle) * xzRadius);
            if (isEmptyOrWaterOrLava(level.getBlock(center.add(dx, 0, dz)))) {
                return false;
            }
        }

        return true;
    }

    static boolean isEmptyOrWaterOrLava(Block state) {
        return state.air() || state.compare(Block.WATER) || state.compare(Block.LAVA);
    }

    static boolean isBaseOrLava(Block state, Block baseBlock, Set<Key> replaceableBlocks) {
        return isBase(state, baseBlock, replaceableBlocks) || state.compare(Block.LAVA);
    }

    static boolean isEmptyOrWater(Block.Getter level, BlockVec position) {
        return isEmptyOrWater(level.getBlock(position));
    }

    static boolean isEmptyOrWater(Block state) {
        return state.air() || state.compare(Block.WATER);
    }

    static boolean isNeitherEmptyNorWater(Block state) {
        return !state.air() && !state.compare(Block.WATER);
    }

    static boolean isBase(Block state, Block baseBlock, Set<Key> replaceableBlocks) {
        return state.compare(baseBlock) || replaceableBlocks.contains(state.key());
    }

    static boolean isWater(Block state) {
        return state.compare(Block.WATER) || "true".equals(state.getProperty("waterlogged"));
    }

    static void buildBaseToTipColumn(Direction direction, int totalLength, boolean mergedTip, Consumer<Block> consumer, Block pointedBlock) {
        if (totalLength >= 3) {
            consumer.accept(createPointedBlock(direction, "base", pointedBlock));

            for (var index = 0; index < totalLength - 3; index++) {
                consumer.accept(createPointedBlock(direction, "middle", pointedBlock));
            }
        }

        if (totalLength >= 2) {
            consumer.accept(createPointedBlock(direction, "frustum", pointedBlock));
        }

        if (totalLength >= 1) {
            consumer.accept(createPointedBlock(direction, mergedTip ? "tip_merge" : "tip", pointedBlock));
        }
    }

    static <T extends Block.Getter & Block.Setter> void growSpeleothem(
            T level,
            BlockVec startPos,
            Direction tipDirection,
            int height,
            boolean mergedTip,
            Block baseBlock,
            Block pointedBlock,
            Set<Key> replaceableBlocks
    ) {
        if (!isBase(level.getBlock(tipDirection.opposite().relative(startPos)), baseBlock, replaceableBlocks)) {
            return;
        }

        var cursor = new BlockVec[]{startPos};
        buildBaseToTipColumn(tipDirection, height, mergedTip, state -> {
            if (state.compare(pointedBlock)) {
                state = state.withProperty("waterlogged", String.valueOf(isWater(level.getBlock(cursor[0]))));
            }

            level.setBlock(cursor[0], state);
            cursor[0] = tipDirection.relative(cursor[0]);
        }, pointedBlock);
    }

    static <T extends Block.Getter & Block.Setter> boolean placeBaseBlockIfPossible(
            T level, BlockVec position, Block baseBlock, Set<Key> replaceableBlocks
    ) {
        var state = level.getBlock(position);
        if (replaceableBlocks.contains(state.key())) {
            level.setBlock(position, baseBlock);
            return true;
        }

        return false;
    }

    private static Block createPointedBlock(Direction direction, String thickness, Block pointedBlock) {
        return pointedBlock
                .withProperty("vertical_direction", direction.serializedName())
                .withProperty("thickness", thickness);
    }
}
