package rocks.minestom.worldgen.datapack;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DataPack {
    private final Path rootPath;
    private final Map<Path, JsonElement> jsonCache;

    public DataPack(Path rootPath) {
        this.rootPath = rootPath;
        this.jsonCache = new ConcurrentHashMap<>();
    }

    public Path rootPath() {
        return this.rootPath;
    }

    public JsonElement readNoiseSettings(Key id) {
        return this.readJson(this.resolve("worldgen/noise_settings", id));
    }

    public JsonElement readDensityFunction(Key id) {
        return this.readJson(this.resolve("worldgen/density_function", id));
    }

    public JsonElement readNoiseParameters(Key id) {
        return this.readJson(this.resolve("worldgen/noise", id));
    }

    public JsonElement readBiome(Key id) {
        return this.readJson(this.resolve("worldgen/biome", id));
    }

    public JsonElement readWorldPreset(Key id) {
        return this.readJson(this.resolve("worldgen/world_preset", id));
    }

    public JsonElement readMultiNoiseBiomeSourceParameterList(Key id) {
        return this.readJson(this.resolve("worldgen/multi_noise_biome_source_parameter_list", id));
    }

    public JsonElement readConfiguredFeature(Key id) {
        return this.readJson(this.resolve("worldgen/configured_feature", id));
    }

    public JsonElement readPlacedFeature(Key id) {
        return this.readJson(this.resolve("worldgen/placed_feature", id));
    }

    public JsonElement readStructureSet(Key id) {
        return this.readJson(this.resolve("worldgen/structure_set", id));
    }

    public JsonElement readStructure(Key id) {
        return this.readJson(this.resolve("worldgen/structure", id));
    }

    public JsonElement readTemplatePool(Key id) {
        return this.readJson(this.resolve("worldgen/template_pool", id));
    }

    public JsonElement readProcessorList(Key id) {
        return this.readJson(this.resolve("worldgen/processor_list", id));
    }

    private Path resolve(String directory, Key id) {
        return this.rootPath.resolve("data")
                .resolve(id.namespace())
                .resolve(directory)
                .resolve(id.value() + ".json");
    }

    private JsonElement readJson(Path path) {
        return this.jsonCache.computeIfAbsent(path, this::readJsonUncached);
    }

    private JsonElement readJsonUncached(Path path) {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read datapack JSON: " + path, exception);
        }
    }
}
