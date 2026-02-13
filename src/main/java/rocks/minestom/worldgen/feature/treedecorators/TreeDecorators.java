package rocks.minestom.worldgen.feature.treedecorators;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;

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
                default -> new Result.Error<>("Unknown tree decorator type: " + type);
            };
        }
    };

    private static final Codec<PlaceOnGroundDecorator> PLACE_ON_GROUND_CODEC = StructCodec.struct(
            "tries", Codec.INT.optional(128), PlaceOnGroundDecorator::tries,
            "radius", Codec.INT.optional(2), PlaceOnGroundDecorator::radius,
            "height", Codec.INT.optional(1), PlaceOnGroundDecorator::height,
            "block_state_provider", BlockStateProviders.CODEC, PlaceOnGroundDecorator::blockStateProvider,
            PlaceOnGroundDecorator::new
    );

    public static TreeDecorator fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
