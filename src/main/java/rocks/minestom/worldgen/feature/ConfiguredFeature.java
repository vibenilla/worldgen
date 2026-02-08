package rocks.minestom.worldgen.feature;

public record ConfiguredFeature<C extends FeatureConfiguration>(Feature<C> feature, C config) {

}
