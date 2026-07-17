package rocks.minestom.worldgen.carver;

import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Exact port of vanilla {@code CanyonWorldCarver} (ravines), including the
 * per-height width factors and per-step radius jitter.
 */
public final class CanyonWorldCarver extends WorldCarver<CanyonCarverConfiguration> {

    @Override
    public boolean isStartChunk(CanyonCarverConfiguration configuration, RandomSource random) {
        return random.nextFloat() <= configuration.base().probability();
    }

    @Override
    protected CarverConfiguration baseConfig(CanyonCarverConfiguration configuration) {
        return configuration.base();
    }

    @Override
    public boolean carve(CarvingContext context, CanyonCarverConfiguration configuration, RandomSource random,
            int sourceChunkX, int sourceChunkZ) {
        var maxDistance = (this.getRange() * 2 - 1) * 16;
        double x = sourceChunkX * 16 + random.nextInt(16);
        var y = configuration.base().y().sample(random, context.minGenY(), context.maxGenYInclusive());
        double z = sourceChunkZ * 16 + random.nextInt(16);
        var horizontalRotation = random.nextFloat() * (float) (Math.PI * 2);
        var verticalRotation = configuration.verticalRotation().sample(random);
        double yScale = configuration.base().yScale().sample(random);
        var thickness = configuration.shape().thickness().sample(random);
        var distance = (int) (maxDistance * configuration.shape().distanceFactor().sample(random));
        this.doCarve(
                context,
                configuration,
                random.nextLong(),
                x,
                y,
                z,
                thickness,
                horizontalRotation,
                verticalRotation,
                0,
                distance,
                yScale);
        return true;
    }

    private void doCarve(
            CarvingContext context,
            CanyonCarverConfiguration configuration,
            long tunnelSeed,
            double x,
            double y,
            double z,
            float thickness,
            float horizontalRotation,
            float verticalRotation,
            int step,
            int distance,
            double yScale) {
        var random = new LegacyRandomSource(tunnelSeed);
        var widthFactorPerHeight = this.initWidthFactors(context, configuration, random);
        var yRota = 0.0F;
        var xRota = 0.0F;

        for (var currentStep = step; currentStep < distance; currentStep++) {
            var horizontalRadius = 1.5 + sin(currentStep * (float) Math.PI / distance) * thickness;
            var verticalRadius = horizontalRadius * yScale;
            horizontalRadius *= configuration.shape().horizontalRadiusFactor().sample(random);
            verticalRadius = this.updateVerticalRadius(configuration, random, verticalRadius, distance, currentStep);
            var xc = cos(verticalRotation);
            var xs = sin(verticalRotation);
            x += cos(horizontalRotation) * xc;
            y += xs;
            z += sin(horizontalRotation) * xc;
            verticalRotation *= 0.7F;
            verticalRotation += xRota * 0.05F;
            horizontalRotation += yRota * 0.05F;
            xRota *= 0.8F;
            yRota *= 0.5F;
            xRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (random.nextInt(4) != 0) {
                if (!canReach(context.chunkX(), context.chunkZ(), x, z, currentStep, distance, thickness)) {
                    return;
                }

                this.carveEllipsoid(
                        context,
                        configuration,
                        x,
                        y,
                        z,
                        horizontalRadius,
                        verticalRadius,
                        (skipContext, xd, yd, zd, blockY) -> this.shouldSkip(skipContext, widthFactorPerHeight, xd, yd,
                                zd, blockY));
            }
        }
    }

    private float[] initWidthFactors(CarvingContext context, CanyonCarverConfiguration configuration,
            RandomSource random) {
        var depth = context.genDepth();
        var widthFactorPerHeight = new float[depth];
        var widthFactor = 1.0F;

        for (var yIndex = 0; yIndex < depth; yIndex++) {
            if (yIndex == 0 || random.nextInt(configuration.shape().widthSmoothness()) == 0) {
                widthFactor = 1.0F + random.nextFloat() * random.nextFloat();
            }

            widthFactorPerHeight[yIndex] = widthFactor * widthFactor;
        }

        return widthFactorPerHeight;
    }

    private double updateVerticalRadius(CanyonCarverConfiguration configuration, RandomSource random,
            double verticalRadius, float distance, float currentStep) {
        var verticalMultiplier = 1.0F - Math.abs(0.5F - currentStep / distance) * 2.0F;
        var factor = configuration.shape().verticalRadiusDefaultFactor()
                + configuration.shape().verticalRadiusCenterFactor() * verticalMultiplier;
        return factor * verticalRadius * (random.nextFloat() * (1.0F - 0.75F) + 0.75F);
    }

    private boolean shouldSkip(CarvingContext context, float[] widthFactorPerHeight, double xd, double yd, double zd,
            int blockY) {
        var yIndex = blockY - context.minGenY();
        return (xd * xd + zd * zd) * widthFactorPerHeight[yIndex - 1] + yd * yd / 6.0 >= 1.0;
    }
}
