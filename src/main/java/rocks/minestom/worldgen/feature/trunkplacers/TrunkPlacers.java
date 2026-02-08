package rocks.minestom.worldgen.feature.trunkplacers;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;

public final class TrunkPlacers {
    private TrunkPlacers() {
    }

    public static final Codec<TrunkPlacer> CODEC = new Codec<>() {
        @Override
        public <D> Result<D> encode(Transcoder<D> coder, TrunkPlacer value) {
            return new Result.Error<>("Encoding is not supported");
        }

        @Override
        public <D> Result<TrunkPlacer> decode(Transcoder<D> coder, D value) {
            var mapResult = coder.getMap(value);
            if (!(mapResult instanceof Result.Ok<Transcoder.MapLike<D>>(Transcoder.MapLike<D> map))) {
                return new Result.Error<>("TrunkPlacer must be a map/object");
            }

            if (!map.hasValue("type")) {
                return new Result.Error<>("TrunkPlacer missing type");
            }

            var type = Codec.STRING.decode(coder, map.getValue("type").orElseThrow()).orElseThrow();

            return switch (type) {
                case "minecraft:straight_trunk_placer" -> StraightTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:giant_trunk_placer" -> GiantTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:mega_jungle_trunk_placer" -> MegaJungleTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:forking_trunk_placer" -> ForkingTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:dark_oak_trunk_placer" -> DarkOakTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:bending_trunk_placer" -> BendingTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:upwards_branching_trunk_placer" -> UpwardsBranchingTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:fancy_trunk_placer" -> FancyTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                case "minecraft:cherry_trunk_placer" -> CherryTrunkPlacer.CODEC.decode(coder, value).mapResult(placer -> (TrunkPlacer) placer);
                default -> new Result.Error<>("Unknown trunk placer type: " + type);
            };
        }
    };

    public static TrunkPlacer fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
