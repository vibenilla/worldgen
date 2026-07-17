package rocks.minestom.worldgen.carver;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.surface.VerticalAnchor;

/**
 * Height providers as used by carver configs, sampling against the generation
 * range (vanilla {@code HeightProvider.sample(random, WorldGenerationContext)}).
 */
public interface CarverHeightProvider {
    int sample(RandomSource random, int minY, int maxYInclusive);

    static CarverHeightProvider fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        if (!object.has("type")) {
            // Bare vertical anchor, e.g. {"absolute": 62}
            return new Constant(VerticalAnchor.CODEC.decode(Transcoder.JSON, object).orElseThrow());
        }

        var type = Key.key(object.get("type").getAsString()).asString();
        return switch (type) {
            case "minecraft:constant" -> new Constant(
                    VerticalAnchor.CODEC.decode(Transcoder.JSON, object.get("value")).orElseThrow());
            case "minecraft:uniform" -> new Uniform(
                    VerticalAnchor.CODEC.decode(Transcoder.JSON, object.get("min_inclusive")).orElseThrow(),
                    VerticalAnchor.CODEC.decode(Transcoder.JSON, object.get("max_inclusive")).orElseThrow());
            default -> throw new IllegalArgumentException("Unsupported carver height provider: " + type);
        };
    }

    record Constant(VerticalAnchor anchor) implements CarverHeightProvider {
        @Override
        public int sample(RandomSource random, int minY, int maxYInclusive) {
            return this.anchor.resolveY(minY, maxYInclusive);
        }
    }

    record Uniform(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) implements CarverHeightProvider {
        @Override
        public int sample(RandomSource random, int minY, int maxYInclusive) {
            var min = this.minInclusive.resolveY(minY, maxYInclusive);
            var max = this.maxInclusive.resolveY(minY, maxYInclusive);
            if (min > max) {
                return min;
            }
            return random.nextInt(max - min + 1) + min;
        }
    }
}
