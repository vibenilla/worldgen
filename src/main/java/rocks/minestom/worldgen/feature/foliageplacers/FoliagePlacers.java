package rocks.minestom.worldgen.feature.foliageplacers;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;

public final class FoliagePlacers {
    private FoliagePlacers() {
    }

    public static final Codec<FoliagePlacer> CODEC = new Codec<>() {
        @Override
        public <D> Result<D> encode(Transcoder<D> coder, FoliagePlacer value) {
            return new Result.Error<>("Encoding is not supported");
        }

        @Override
        public <D> Result<FoliagePlacer> decode(Transcoder<D> coder, D value) {
            var mapResult = coder.getMap(value);
            if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>>(var map))) {
                return new Result.Error<>("FoliagePlacer must be a map/object");
            }

            if (!map.hasValue("type")) {
                return new Result.Error<>("FoliagePlacer missing type");
            }

            var type = Codec.STRING.decode(coder, map.getValue("type").orElseThrow()).orElseThrow();
            return switch (type) {
                case "minecraft:blob_foliage_placer" -> BlobFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:spruce_foliage_placer" -> SpruceFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:pine_foliage_placer" -> PineFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:acacia_foliage_placer" -> AcaciaFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:bush_foliage_placer" -> BushFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:fancy_foliage_placer" -> FancyFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:dark_oak_foliage_placer" -> DarkOakFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:jungle_foliage_placer" -> MegaJungleFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:mega_pine_foliage_placer" -> MegaPineFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:random_spread_foliage_placer" -> RandomSpreadFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                case "minecraft:cherry_foliage_placer" -> CherryFoliagePlacer.CODEC.decode(coder, value).mapResult(placer -> (FoliagePlacer) placer);
                default -> new Result.Error<>("Unknown foliage placer type: " + type);
            };
        }
    };

    public static FoliagePlacer fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
