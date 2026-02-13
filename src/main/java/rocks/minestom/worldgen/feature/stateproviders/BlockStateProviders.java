package rocks.minestom.worldgen.feature.stateproviders;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;

public final class BlockStateProviders {
    private BlockStateProviders() {
    }

    public static final Codec<BlockStateProvider> CODEC = new Codec<>() {
        @Override
        public <D> Result<D> encode(Transcoder<D> coder, BlockStateProvider value) {
            return new Result.Error<>("Encoding is not supported");
        }

        @Override
        public <D> Result<BlockStateProvider> decode(Transcoder<D> coder, D value) {
            var mapResult = coder.getMap(value);
            if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>>(var map))) {
                return new Result.Error<>("BlockStateProvider must be a map/object");
            }

            if (!map.hasValue("type")) {
                return new Result.Error<>("BlockStateProvider missing type");
            }

            var type = Codec.STRING.decode(coder, map.getValue("type").orElseThrow()).orElseThrow();
            return switch (type) {
                case "minecraft:simple_state_provider" ->
                    SimpleStateProvider.CODEC.decode(coder, value).mapResult(provider -> (BlockStateProvider) provider);
                case "minecraft:weighted_state_provider" -> WeightedStateProvider.CODEC.decode(coder, value)
                        .mapResult(provider -> (BlockStateProvider) provider);
                case "minecraft:randomized_int_state_provider" -> RandomizedIntStateProvider.CODEC.decode(coder, value)
                        .mapResult(provider -> (BlockStateProvider) provider);
                case "minecraft:rotated_block_provider" -> RotatedBlockProvider.CODEC.decode(coder, value)
                        .mapResult(provider -> (BlockStateProvider) provider);
                case "minecraft:noise_provider" -> NoiseProvider.CODEC.decode(coder, value)
                        .mapResult(provider -> (BlockStateProvider) provider);
                case "minecraft:noise_threshold_provider" -> NoiseThresholdProvider.CODEC.decode(coder, value)
                        .mapResult(provider -> (BlockStateProvider) provider);
                case "minecraft:dual_noise_provider" -> DualNoiseProvider.CODEC.decode(coder, value)
                        .mapResult(provider -> (BlockStateProvider) provider);
                default -> new Result.Error<>("Unknown block state provider type: " + type);
            };
        }
    };

    public static BlockStateProvider fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
