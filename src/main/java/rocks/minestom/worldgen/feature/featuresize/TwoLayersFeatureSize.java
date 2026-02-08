package rocks.minestom.worldgen.feature.featuresize;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

import java.util.OptionalInt;

public record TwoLayersFeatureSize(int limit, int lowerSize, int upperSize, OptionalInt minClippedHeight) implements FeatureSize {

    public static final Codec<TwoLayersFeatureSize> CODEC = StructCodec.struct(
            "limit", Codec.INT.optional(1), TwoLayersFeatureSize::limit,
            "lower_size", Codec.INT.optional(0), TwoLayersFeatureSize::lowerSize,
            "upper_size", Codec.INT.optional(1), TwoLayersFeatureSize::upperSize,
            (limit, lowerSize, upperSize) -> new TwoLayersFeatureSize(limit, lowerSize, upperSize, OptionalInt.empty())
    );

    public TwoLayersFeatureSize(int limit, int lowerSize, int upperSize) {
        this(limit, lowerSize, upperSize, OptionalInt.empty());
    }

    @Override
    public int getSizeAtHeight(int treeHeight, int height) {
        return height < this.limit ? this.lowerSize : this.upperSize;
    }
}
