package rocks.minestom.worldgen.feature.valueproviders;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.random.RandomSource;

public interface IntProvider {
    Codec<IntProvider> CODEC = new Codec<>() {
        @Override
        public <D> Result<D> encode(Transcoder<D> coder, IntProvider value) {
            return new Result.Error<>("Encoding is not supported");
        }

        @Override
        public <D> Result<IntProvider> decode(Transcoder<D> coder, D value) {
            var constantResult = coder.getInt(value);
            if (constantResult instanceof Result.Ok<Integer>(Integer value1)) {
                return new Result.Ok<>(new ConstantIntProvider(value1));
            }

            var mapResult = coder.getMap(value);
            if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>>(Transcoder.MapLike<D> map))) {
                return new Result.Error<>("Not an int provider: " + value);
            }

            if (map.hasValue("type")) {
                var typeKey = Codec.KEY.decode(coder, map.getValue("type").orElseThrow()).orElseThrow();
                return switch (typeKey.asString()) {
                    case "minecraft:uniform" -> UniformIntProvider.CODEC.decode(coder, value).mapResult(provider -> (IntProvider) provider);
                    case "minecraft:weighted_list" -> WeightedListIntProvider.CODEC.decode(coder, value).mapResult(provider -> (IntProvider) provider);
                    default -> new Result.Error<>("Unknown int provider type: " + typeKey.asString());
                };
            }

            if (map.hasValue("min_inclusive") && map.hasValue("max_inclusive")) {
                return UniformIntProvider.CODEC.decode(coder, value).mapResult(provider -> (IntProvider) provider);
            }

            return new Result.Error<>("Unknown int provider: " + value);
        }
    };

    Key type();

    int sample(RandomSource random);

    static IntProvider fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
