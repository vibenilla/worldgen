package rocks.minestom.worldgen.carver;

import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Exact port of vanilla {@code CaveWorldCarver}. All random draws happen in the
 * vanilla order; tunnels get their own legacy-LCG random seeded from the carve
 * random so their shapes are stable regardless of the target chunk.
 */
public class CaveWorldCarver extends WorldCarver<CaveCarverConfiguration> {

    @Override
    public boolean isStartChunk(CaveCarverConfiguration configuration, RandomSource random) {
        return random.nextFloat() <= configuration.base().probability();
    }

    @Override
    protected CarverConfiguration baseConfig(CaveCarverConfiguration configuration) {
        return configuration.base();
    }

    @Override
    public boolean carve(CarvingContext context, CaveCarverConfiguration configuration, RandomSource random,
            int sourceChunkX, int sourceChunkZ) {
        var maxDistance = (this.getRange() * 2 - 1) * 16;
        var caveCount = random.nextInt(random.nextInt(random.nextInt(this.getCaveBound()) + 1) + 1);

        for (var cave = 0; cave < caveCount; cave++) {
            double x = sourceChunkX * 16 + random.nextInt(16);
            double y = configuration.base().y().sample(random, context.minGenY(), context.maxGenYInclusive());
            double z = sourceChunkZ * 16 + random.nextInt(16);
            double horizontalRadiusMultiplier = configuration.horizontalRadiusMultiplier().sample(random);
            double verticalRadiusMultiplier = configuration.verticalRadiusMultiplier().sample(random);
            double floorLevel = configuration.floorLevel().sample(random);
            CarveSkipChecker skipChecker = (skipContext, xd, yd, zd, blockY) -> shouldSkip(xd, yd, zd, floorLevel);
            var tunnels = 1;
            if (random.nextInt(4) == 0) {
                double yScale = configuration.base().yScale().sample(random);
                var thickness = 1.0F + random.nextFloat() * 6.0F;
                this.createRoom(context, configuration, x, y, z, thickness, yScale, skipChecker);
                tunnels += random.nextInt(4);
            }

            for (var tunnel = 0; tunnel < tunnels; tunnel++) {
                var horizontalRotation = random.nextFloat() * (float) (Math.PI * 2);
                var verticalRotation = (random.nextFloat() - 0.5F) / 4.0F;
                var thickness = this.getThickness(random);
                var distance = maxDistance - random.nextInt(maxDistance / 4);
                this.createTunnel(
                        context,
                        configuration,
                        random.nextLong(),
                        x,
                        y,
                        z,
                        horizontalRadiusMultiplier,
                        verticalRadiusMultiplier,
                        thickness,
                        horizontalRotation,
                        verticalRotation,
                        0,
                        distance,
                        this.getYScale(),
                        skipChecker);
            }
        }

        return true;
    }

    protected int getCaveBound() {
        return 15;
    }

    protected float getThickness(RandomSource random) {
        var thickness = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            thickness *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return thickness;
    }

    protected double getYScale() {
        return 1.0;
    }

    protected void createRoom(CarvingContext context, CaveCarverConfiguration configuration, double x, double y,
            double z, float thickness, double yScale, CarveSkipChecker skipChecker) {
        var horizontalRadius = 1.5 + sin((float) (Math.PI / 2)) * thickness;
        var verticalRadius = horizontalRadius * yScale;
        this.carveEllipsoid(context, configuration, x + 1.0, y, z, horizontalRadius, verticalRadius, skipChecker);
    }

    protected void createTunnel(
            CarvingContext context,
            CaveCarverConfiguration configuration,
            long tunnelSeed,
            double x,
            double y,
            double z,
            double horizontalRadiusMultiplier,
            double verticalRadiusMultiplier,
            float thickness,
            float horizontalRotation,
            float verticalRotation,
            int step,
            int dist,
            double yScale,
            CarveSkipChecker skipChecker) {
        var random = new LegacyRandomSource(tunnelSeed);
        var splitPoint = random.nextInt(dist / 2) + dist / 4;
        var steep = random.nextInt(6) == 0;
        var yRota = 0.0F;
        var xRota = 0.0F;

        for (var currentStep = step; currentStep < dist; currentStep++) {
            var horizontalRadius = 1.5 + sin((float) Math.PI * currentStep / dist) * thickness;
            var verticalRadius = horizontalRadius * yScale;
            var cosX = cos(verticalRotation);
            x += cos(horizontalRotation) * cosX;
            y += sin(verticalRotation);
            z += sin(horizontalRotation) * cosX;
            verticalRotation *= steep ? 0.92F : 0.7F;
            verticalRotation += xRota * 0.1F;
            horizontalRotation += yRota * 0.1F;
            xRota *= 0.9F;
            yRota *= 0.75F;
            xRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yRota += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (currentStep == splitPoint && thickness > 1.0F) {
                this.createTunnel(
                        context,
                        configuration,
                        random.nextLong(),
                        x,
                        y,
                        z,
                        horizontalRadiusMultiplier,
                        verticalRadiusMultiplier,
                        random.nextFloat() * 0.5F + 0.5F,
                        horizontalRotation - (float) (Math.PI / 2),
                        verticalRotation / 3.0F,
                        currentStep,
                        dist,
                        1.0,
                        skipChecker);
                this.createTunnel(
                        context,
                        configuration,
                        random.nextLong(),
                        x,
                        y,
                        z,
                        horizontalRadiusMultiplier,
                        verticalRadiusMultiplier,
                        random.nextFloat() * 0.5F + 0.5F,
                        horizontalRotation + (float) (Math.PI / 2),
                        verticalRotation / 3.0F,
                        currentStep,
                        dist,
                        1.0,
                        skipChecker);
                return;
            }

            if (random.nextInt(4) != 0) {
                if (!canReach(context.chunkX(), context.chunkZ(), x, z, currentStep, dist, thickness)) {
                    return;
                }

                this.carveEllipsoid(
                        context,
                        configuration,
                        x,
                        y,
                        z,
                        horizontalRadius * horizontalRadiusMultiplier,
                        verticalRadius * verticalRadiusMultiplier,
                        skipChecker);
            }
        }
    }

    private static boolean shouldSkip(double xd, double yd, double zd, double floorLevel) {
        return yd <= floorLevel || xd * xd + yd * yd + zd * zd >= 1.0;
    }
}
