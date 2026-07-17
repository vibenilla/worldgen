package rocks.minestom.worldgen.feature.featuresize;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

import java.util.OptionalInt;

public record ThreeLayersFeatureSize(
        int limit,
        int upperLimit,
        int lowerSize,
        int middleSize,
        int upperSize,
        OptionalInt minClippedHeight
) implements FeatureSize {
    public static final Codec<ThreeLayersFeatureSize> CODEC = StructCodec.struct(
            "limit", Codec.INT.optional(1), ThreeLayersFeatureSize::limit,
            "upper_limit", Codec.INT.optional(1), ThreeLayersFeatureSize::upperLimit,
            "lower_size", Codec.INT.optional(0), ThreeLayersFeatureSize::lowerSize,
            "middle_size", Codec.INT.optional(1), ThreeLayersFeatureSize::middleSize,
            "upper_size", Codec.INT.optional(1), ThreeLayersFeatureSize::upperSize,
            "min_clipped_height", Codec.INT.optional(),
            size -> size.minClippedHeight().isPresent() ? size.minClippedHeight().getAsInt() : null,
            (limit, upperLimit, lowerSize, middleSize, upperSize, minClipped) -> new ThreeLayersFeatureSize(
                    limit, upperLimit, lowerSize, middleSize, upperSize,
                    minClipped != null ? OptionalInt.of(minClipped) : OptionalInt.empty()));

    public ThreeLayersFeatureSize(int limit, int upperLimit, int lowerSize, int middleSize, int upperSize) {
        this(limit, upperLimit, lowerSize, middleSize, upperSize, OptionalInt.empty());
    }

    @Override
    public int getSizeAtHeight(int treeHeight, int height) {
        if (height < this.limit) {
            return this.lowerSize;
        }
        // Vanilla measures the upper layer from the TOP of the tree
        return height >= treeHeight - this.upperLimit ? this.upperSize : this.middleSize;
    }
}
