package rocks.minestom.worldgen.feature.treedecorators;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;

public final class TreeDecorators {
    private TreeDecorators() {
    }

    public static final Codec<TreeDecorator> CODEC = new Codec<>() {
        @Override
        public <D> Result<D> encode(Transcoder<D> coder, TreeDecorator value) {
            return new Result.Error<>("Encoding is not supported");
        }

        @Override
        public <D> Result<TreeDecorator> decode(Transcoder<D> coder, D value) {
            var mapResult = coder.getMap(value);
            if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>>(var map))) {
                return new Result.Error<>("TreeDecorator must be a map/object");
            }

            if (!map.hasValue("type")) {
                return new Result.Error<>("TreeDecorator missing type");
            }

            var type = Codec.STRING.decode(coder, map.getValue("type").orElseThrow()).orElseThrow();

            return switch (type) {
                case "minecraft:place_on_ground" -> PlaceOnGroundDecorator.CODEC.decode(coder, value).mapResult(decorator -> (TreeDecorator) decorator);
                default -> {
                    // consume the unknown decorator so parsing succeeds, but return a no-op
                    // We need to fully decode/consume it to advance the stream if needed (though map access is random access usually)
                    // If we just return NoOpDecorator without decoding the structure, it might leave unconsumed data in some formats,
                    // but for JSON it's fine. However, to be safe and consistent, we could decode it as RAW_VALUE.
                    // But here we can just return the NoOp decorator directly as we're in a map context.
                    yield new Result.Ok<>(NoOpDecorator.INSTANCE);
                }
            };
        }
    };

    public static TreeDecorator fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
