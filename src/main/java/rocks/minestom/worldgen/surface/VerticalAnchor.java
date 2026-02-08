package rocks.minestom.worldgen.surface;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.Transcoder;

public record VerticalAnchor(Kind kind, int value) {
    public static final Codec<VerticalAnchor> CODEC = new Codec<>() {
        @Override
        public <D> Result<VerticalAnchor> decode(Transcoder<D> coder, D value) {
            var mapResult = coder.getMap(value);

            if (!(mapResult instanceof Result.Ok(var map))) {
                return mapResult.cast();
            }

            if (map.size() != 1) {
                return new Result.Error<>("VerticalAnchor must contain exactly 1 entry: " + value);
            }

            var key = map.keys().iterator().next();
            var rawValue = map.getValue(key).orElseThrow();
            var intResult = coder.getInt(rawValue);

            if (!(intResult instanceof Result.Ok(var intValue))) {
                return intResult.cast();
            }

            var kind = Kind.fromKey(key);

            if (kind == null) {
                return new Result.Error<>("Unsupported VerticalAnchor key: " + key);
            }

            return new Result.Ok<>(new VerticalAnchor(kind, intValue));
        }

        @Override
        public <D> Result<D> encode(Transcoder<D> coder, VerticalAnchor value) {
            throw new UnsupportedOperationException("Encoding is not supported");
        }
    };

    public int resolveY(int minY, int maxYInclusive) {
        return switch (this.kind) {
            case ABSOLUTE -> this.value;
            case ABOVE_BOTTOM -> minY + this.value;
            case BELOW_TOP -> maxYInclusive - this.value;
        };
    }

    public enum Kind {
        ABSOLUTE("absolute"),
        ABOVE_BOTTOM("above_bottom"),
        BELOW_TOP("below_top");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        private static Kind fromKey(String key) {
            for (var kind : Kind.values()) {
                if (kind.key.equals(key)) {
                    return kind;
                }
            }

            return null;
        }
    }
}

