package rocks.minestom.worldgen.feature.featuresize;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;

public final class FeatureSizes {
    private FeatureSizes() {
    }

    public static final Codec<FeatureSize> CODEC = new Codec<>() {
        @Override
        public <D> Result<D> encode(Transcoder<D> coder, FeatureSize value) {
            return new Result.Error<>("Encoding is not supported");
        }

        @Override
        public <D> Result<FeatureSize> decode(Transcoder<D> coder, D value) {
            var mapResult = coder.getMap(value);
            if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>> okMap)) {
                return new Result.Error<>("FeatureSize must be a map/object");
            }

            var map = okMap.value();
            if (!map.hasValue("type")) {
                return new Result.Error<>("FeatureSize missing type");
            }

            var type = Codec.STRING.decode(coder, map.getValue("type").orElseThrow()).orElseThrow();
            return switch (type) {
                case "minecraft:two_layers_feature_size" -> TwoLayersFeatureSize.CODEC.decode(coder, value).mapResult(size -> (FeatureSize) size);
                case "minecraft:three_layers_feature_size" -> ThreeLayersFeatureSize.CODEC.decode(coder, value).mapResult(size -> (FeatureSize) size);
                default -> new Result.Error<>("Unknown feature size type: " + type);
            };
        }
    };

    public static FeatureSize fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
