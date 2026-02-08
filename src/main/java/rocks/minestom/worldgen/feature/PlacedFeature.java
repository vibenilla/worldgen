package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

import java.util.List;

public record PlacedFeature(Key feature, List<Codec.RawValue> placement) {
    public static final Codec<PlacedFeature> CODEC = StructCodec.struct(
            "feature", Codec.KEY, PlacedFeature::feature,
            "placement", Codec.RAW_VALUE.list().optional(List.of()), PlacedFeature::placement,
            PlacedFeature::new
    );
}

