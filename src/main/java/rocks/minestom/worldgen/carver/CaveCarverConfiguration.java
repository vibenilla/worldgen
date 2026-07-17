package rocks.minestom.worldgen.carver;

import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;

public record CaveCarverConfiguration(
        CarverConfiguration base,
        FloatProvider horizontalRadiusMultiplier,
        FloatProvider verticalRadiusMultiplier,
        FloatProvider floorLevel) {
}
