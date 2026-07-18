package rocks.minestom.worldgen.carver;

import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Base for cave/canyon carvers. Exact port of vanilla {@code WorldCarver}
 * including the sin lookup table (float parity matters for tunnel paths) and
 * the ellipsoid carving loop. Random call order is preserved throughout.
 */
public abstract class WorldCarver<C> {
    // Vanilla Mth.SIN: sin sampled at 65536 points around the circle.
    private static final float[] SIN = new float[65536];
    static {
        for (var index = 0; index < SIN.length; index++) {
            SIN[index] = (float) Math.sin(index / 10430.378350470453);
        }
    }

    static float sin(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453) & 65535L)];
    }

    static float cos(double value) {
        return SIN[(int) ((long) (value * 10430.378350470453 + 16384.0) & 65535L)];
    }

    public int getRange() {
        return 4;
    }

    public abstract boolean isStartChunk(C configuration, RandomSource random);

    public abstract boolean carve(CarvingContext context, C configuration, RandomSource random,
            int sourceChunkX, int sourceChunkZ);

    protected abstract CarverConfiguration baseConfig(C configuration);

    protected boolean carveEllipsoid(
            CarvingContext context,
            C configuration,
            double x,
            double y,
            double z,
            double horizontalRadius,
            double verticalRadius,
            CarveSkipChecker skipChecker) {
        double centerX = context.middleBlockX();
        double centerZ = context.middleBlockZ();
        var maxDelta = 16.0 + horizontalRadius * 2.0;
        if (Math.abs(x - centerX) > maxDelta || Math.abs(z - centerZ) > maxDelta) {
            return false;
        }

        var chunkMinX = context.minBlockX();
        var chunkMinZ = context.minBlockZ();
        var minXIndex = Math.max(VMath.floor(x - horizontalRadius) - chunkMinX - 1, 0);
        var maxXIndex = Math.min(VMath.floor(x + horizontalRadius) - chunkMinX, 15);
        var minY = Math.max(VMath.floor(y - verticalRadius) - 1, context.minGenY() + 1);
        // Vanilla protects the top 7 blocks of the generation range.
        var maxY = Math.min(VMath.floor(y + verticalRadius) + 1, context.minGenY() + context.genDepth() - 1 - 7);
        var minZIndex = Math.max(VMath.floor(z - horizontalRadius) - chunkMinZ - 1, 0);
        var maxZIndex = Math.min(VMath.floor(z + horizontalRadius) - chunkMinZ, 15);
        var carved = false;

        for (var xIndex = minXIndex; xIndex <= maxXIndex; xIndex++) {
            var worldX = chunkMinX + xIndex;
            var xd = (worldX + 0.5 - x) / horizontalRadius;

            for (var zIndex = minZIndex; zIndex <= maxZIndex; zIndex++) {
                var worldZ = chunkMinZ + zIndex;
                var zd = (worldZ + 0.5 - z) / horizontalRadius;
                if (xd * xd + zd * zd >= 1.0) {
                    continue;
                }

                var hasGrass = new MutableBoolean();
                for (var worldY = maxY; worldY > minY; worldY--) {
                    var yd = (worldY - 0.5 - y) / verticalRadius;
                    if (!skipChecker.shouldSkip(context, xd, yd, zd, worldY)
                            && !context.maskGet(xIndex, worldY, zIndex)) {
                        context.maskSet(xIndex, worldY, zIndex);
                        carved |= this.carveBlock(context, configuration, worldX, worldY, worldZ, hasGrass);
                    }
                }
            }
        }

        return carved;
    }

    protected boolean carveBlock(CarvingContext context, C configuration, int blockX, int blockY, int blockZ,
            MutableBoolean hasGrass) {
        var block = context.getBlock(blockX, blockY, blockZ);
        if (block.compare(Block.GRASS_BLOCK) || block.compare(Block.MYCELIUM)) {
            hasGrass.value = true;
        }

        var base = this.baseConfig(configuration);
        if (!base.canReplace(block)) {
            return false;
        }

        var state = this.getCarveState(context, base, blockX, blockY, blockZ);
        if (state == null) {
            return false;
        }

        context.setCarved(blockX, blockY, blockZ, state);
        if (context.aquifer().shouldScheduleFluidUpdate() && state.compare(Block.WATER)) {
            context.recordFluidTick(blockX, blockY, blockZ);
        }
        if (hasGrass.value) {
            var below = context.getBlock(blockX, blockY - 1, blockZ);
            if (below.compare(Block.DIRT)) {
                var topMaterial = context.topMaterial(blockX, blockY - 1, blockZ, !state.isAir());
                if (topMaterial != null) {
                    context.setSolid(blockX, blockY - 1, blockZ, topMaterial);
                }
            }
        }

        return true;
    }

    @Nullable
    private Block getCarveState(CarvingContext context, CarverConfiguration base, int blockX, int blockY, int blockZ) {
        if (blockY <= base.lavaLevel().resolveY(context.minGenY(), context.maxGenYInclusive())) {
            return Block.LAVA;
        }
        // Density 0.0: the aquifer decides air vs water vs lava; null is a
        // pressure barrier and the block stays solid.
        return context.aquifer().computeSubstance(
                new DensityFunction.SinglePointContext(blockX, blockY, blockZ), 0.0);
    }

    protected static boolean canReach(int chunkX, int chunkZ, double x, double z, int currentStep, int totalSteps,
            float thickness) {
        double xMid = chunkX * 16 + 8;
        double zMid = chunkZ * 16 + 8;
        var xd = x - xMid;
        var zd = z - zMid;
        double remaining = totalSteps - currentStep;
        double rr = thickness + 2.0F + 16.0F;
        return xd * xd + zd * zd - remaining * remaining <= rr * rr;
    }

    public interface CarveSkipChecker {
        boolean shouldSkip(CarvingContext context, double xd, double yd, double zd, int blockY);
    }

    protected static final class MutableBoolean {
        boolean value;
    }
}
