package rocks.minestom.worldgen.carver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.surface.VerticalAnchor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads configured carvers and per-biome carver lists from the datapack,
 * mirroring {@link rocks.minestom.worldgen.feature.FeatureLoader}'s caching.
 */
public final class CarverLoader {
    private static final CaveWorldCarver CAVE = new CaveWorldCarver();
    private static final NetherWorldCarver NETHER_CAVE = new NetherWorldCarver();
    private static final CanyonWorldCarver CANYON = new CanyonWorldCarver();

    private final DataPack dataPack;
    private final BlockTagManager blockTags;
    private final Map<Key, List<Key>> biomeCarverCache;
    private final Map<Key, Optional<ConfiguredCarver<?>>> carverCache;

    public CarverLoader(DataPack dataPack, BlockTagManager blockTags) {
        this.dataPack = dataPack;
        this.blockTags = blockTags;
        this.biomeCarverCache = new ConcurrentHashMap<>();
        this.carverCache = new ConcurrentHashMap<>();
    }

    /**
     * The biome's configured carver ids, in datapack order (order defines the
     * carver seed index).
     */
    public List<Key> biomeCarvers(Key biomeId) {
        return this.biomeCarverCache.computeIfAbsent(biomeId, this::loadBiomeCarvers);
    }

    public ConfiguredCarver<?> configuredCarver(Key id) {
        return this.carverCache.computeIfAbsent(id, this::loadConfiguredCarver).orElse(null);
    }

    private List<Key> loadBiomeCarvers(Key biomeId) {
        try {
            var json = this.dataPack.readBiome(biomeId).getAsJsonObject();
            var carvers = json.get("carvers");
            if (carvers == null) {
                return List.of();
            }
            if (carvers.isJsonPrimitive()) {
                return List.of(Key.key(carvers.getAsString()));
            }
            if (carvers.isJsonArray()) {
                return keyList(carvers.getAsJsonArray());
            }
            // Legacy pre-1.21.2 format: {"air": [...], "liquid": [...]}
            var result = new ArrayList<Key>();
            for (var entry : carvers.getAsJsonObject().entrySet()) {
                var value = entry.getValue();
                if (value.isJsonArray()) {
                    result.addAll(keyList(value.getAsJsonArray()));
                } else {
                    result.add(Key.key(value.getAsString()));
                }
            }
            return List.copyOf(result);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private static List<Key> keyList(JsonArray array) {
        var keys = new ArrayList<Key>(array.size());
        for (var element : array) {
            keys.add(Key.key(element.getAsString()));
        }
        return List.copyOf(keys);
    }

    private Optional<ConfiguredCarver<?>> loadConfiguredCarver(Key id) {
        try {
            var json = this.dataPack.readConfiguredCarver(id).getAsJsonObject();
            var type = Key.key(json.get("type").getAsString()).asString();
            var config = json.getAsJsonObject("config");
            var base = this.parseBase(config);
            return Optional.ofNullable(switch (type) {
                case "minecraft:cave" -> new ConfiguredCarver<>(CAVE, parseCaveConfig(base, config));
                case "minecraft:nether_cave" -> new ConfiguredCarver<>(NETHER_CAVE, parseCaveConfig(base, config));
                case "minecraft:canyon" -> new ConfiguredCarver<>(CANYON, parseCanyonConfig(base, config));
                default -> null;
            });
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private CarverConfiguration parseBase(com.google.gson.JsonObject config) {
        return new CarverConfiguration(
                config.get("probability").getAsFloat(),
                CarverHeightProvider.fromJson(config.get("y")),
                FloatProvider.fromJson(config.get("yScale")),
                VerticalAnchor.CODEC.decode(Transcoder.JSON, config.get("lava_level")).orElseThrow(),
                this.parseReplaceable(config.get("replaceable")));
    }

    private static CaveCarverConfiguration parseCaveConfig(CarverConfiguration base,
            com.google.gson.JsonObject config) {
        return new CaveCarverConfiguration(
                base,
                FloatProvider.fromJson(config.get("horizontal_radius_multiplier")),
                FloatProvider.fromJson(config.get("vertical_radius_multiplier")),
                FloatProvider.fromJson(config.get("floor_level")));
    }

    private static CanyonCarverConfiguration parseCanyonConfig(CarverConfiguration base,
            com.google.gson.JsonObject config) {
        var shape = config.getAsJsonObject("shape");
        return new CanyonCarverConfiguration(
                base,
                FloatProvider.fromJson(config.get("vertical_rotation")),
                new CanyonCarverConfiguration.Shape(
                        FloatProvider.fromJson(shape.get("distance_factor")),
                        FloatProvider.fromJson(shape.get("thickness")),
                        shape.get("width_smoothness").getAsInt(),
                        FloatProvider.fromJson(shape.get("horizontal_radius_factor")),
                        shape.get("vertical_radius_default_factor").getAsFloat(),
                        shape.get("vertical_radius_center_factor").getAsFloat()));
    }

    private Set<Key> parseReplaceable(JsonElement element) {
        var blocks = new HashSet<Key>();
        if (element.isJsonArray()) {
            for (var entry : element.getAsJsonArray()) {
                this.addReplaceable(blocks, entry.getAsString());
            }
        } else {
            this.addReplaceable(blocks, element.getAsString());
        }
        return Set.copyOf(blocks);
    }

    private void addReplaceable(Set<Key> blocks, String value) {
        if (value.startsWith("#")) {
            blocks.addAll(this.blockTags.blocks(Key.key(value.substring(1))));
        } else {
            blocks.add(Key.key(value));
        }
    }
}
