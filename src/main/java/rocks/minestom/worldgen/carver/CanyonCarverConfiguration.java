package rocks.minestom.worldgen.carver;

import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;

public record CanyonCarverConfiguration(
        CarverConfiguration base,
        FloatProvider verticalRotation,
        Shape shape) {

    public record Shape(
            FloatProvider distanceFactor,
            FloatProvider thickness,
            int widthSmoothness,
            FloatProvider horizontalRadiusFactor,
            float verticalRadiusDefaultFactor,
            float verticalRadiusCenterFactor) {
    }
}
