package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonObject;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.featuresize.FeatureSize;
import rocks.minestom.worldgen.feature.featuresize.FeatureSizes;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacers;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.feature.trunkplacers.TrunkPlacer;
import rocks.minestom.worldgen.feature.trunkplacers.TrunkPlacers;

public record TreeConfiguration(
        BlockStateProvider trunkProvider,
        TrunkPlacer trunkPlacer,
        BlockStateProvider foliageProvider,
        FoliagePlacer foliagePlacer,
        BlockStateProvider dirtProvider,
        FeatureSize minimumSize,
        boolean ignoreVines,
        boolean forceDirt
) implements FeatureConfiguration {
    public static final Codec<TreeConfiguration> CODEC = StructCodec.struct(
            "trunk_provider", BlockStateProviders.CODEC, TreeConfiguration::trunkProvider,
            "trunk_placer", TrunkPlacers.CODEC, TreeConfiguration::trunkPlacer,
            "foliage_provider", BlockStateProviders.CODEC, TreeConfiguration::foliageProvider,
            "foliage_placer", FoliagePlacers.CODEC, TreeConfiguration::foliagePlacer,
            "dirt_provider", BlockStateProviders.CODEC, TreeConfiguration::dirtProvider,
            "minimum_size", FeatureSizes.CODEC, TreeConfiguration::minimumSize,
            "ignore_vines", Codec.BOOLEAN.optional(false), TreeConfiguration::ignoreVines,
            "force_dirt", Codec.BOOLEAN.optional(false), TreeConfiguration::forceDirt,
            TreeConfiguration::new
    );

    public static TreeConfiguration decode(JsonObject json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
