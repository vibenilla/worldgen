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
                case "minecraft:beehive" -> BeehiveDecorator.CODEC.decode(coder, value).mapResult(decorator -> (TreeDecorator) decorator);
                case "minecraft:trunk_vine" -> new Result.Ok<>(TrunkVineDecorator.INSTANCE);
                case "minecraft:leave_vine" -> LeaveVineDecorator.CODEC.decode(coder, value).mapResult(decorator -> (TreeDecorator) decorator);
                case "minecraft:attached_to_logs" -> AttachedToLogsDecorator.CODEC.decode(coder, value).mapResult(decorator -> (TreeDecorator) decorator);
                case "minecraft:alter_ground" -> AlterGroundDecorator.CODEC.decode(coder, value).mapResult(decorator -> (TreeDecorator) decorator);
                case "minecraft:cocoa" -> CocoaDecorator.CODEC.decode(coder, value).mapResult(decorator -> (TreeDecorator) decorator);
                // pale_moss, creaking_heart, attached_to_leaves are not
                // implemented yet; parse them as no-ops
                default -> new Result.Ok<>(NoOpDecorator.INSTANCE);
            };
        }
    };

    public static TreeDecorator fromJson(JsonElement json) {
        return CODEC.decode(Transcoder.JSON, json).orElseThrow();
    }
}
