package rocks.minestom.worldgen.feature.valueproviders;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.random.RandomSource;

public interface FloatProvider {
    Codec<FloatProvider> CODEC = new Codec<>() {
        @Override
        public <D> Result<D> encode(Transcoder<D> coder, FloatProvider value) {
            return new Result.Error<>("Encoding is not supported");
        }

        @Override
        public <D> Result<FloatProvider> decode(Transcoder<D> coder, D value) {
            var constantResult = coder.getFloat(value);
            if (constantResult instanceof Result.Ok<Float>(var constantValue)) {
                return new Result.Ok<>(new ConstantFloatProvider(constantValue));
            }

            var mapResult = coder.getMap(value);
            if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>>(var map))) {
                return new Result.Error<>("Not a float provider: " + value);
            }

            if (map.hasValue("type")) {
                var typeKey = Codec.KEY.decode(coder, map.getValue("type").orElseThrow()).orElseThrow();
                return switch (typeKey.asString()) {
                    case "minecraft:constant" -> ConstantFloatProvider.CODEC.decode(coder, value).mapResult(provider -> (FloatProvider) provider);
                    case "minecraft:uniform" -> UniformFloatProvider.CODEC.decode(coder, value).mapResult(provider -> (FloatProvider) provider);
                    case "minecraft:trapezoid" -> TrapezoidFloatProvider.CODEC.decode(coder, value).mapResult(provider -> (FloatProvider) provider);
                    case "minecraft:clamped_normal" -> ClampedNormalFloatProvider.CODEC.decode(coder, value).mapResult(provider -> (FloatProvider) provider);
                    default -> new Result.Error<>("Unknown float provider type: " + typeKey.asString());
                };
            }

            if (map.hasValue("min_inclusive") && map.hasValue("max_exclusive")) {
                return UniformFloatProvider.CODEC.decode(coder, value).mapResult(provider -> (FloatProvider) provider);
            }

            return new Result.Error<>("Unknown float provider: " + value);
        }
    };

    Key type();

    float sample(RandomSource random);

    static FloatProvider fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
