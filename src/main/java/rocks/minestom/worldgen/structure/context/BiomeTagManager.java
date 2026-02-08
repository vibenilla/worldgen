package rocks.minestom.worldgen.structure.context;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class BiomeTagManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(BiomeTagManager.class);

    private final Map<Key, TagDefinition> definitions;
    private final Map<Key, Set<Key>> resolved;

    public BiomeTagManager(Path rootPath) {
        this.definitions = new LinkedHashMap<>();
        this.resolved = new IdentityHashMap<>();
        this.loadDefinitions(rootPath);
    }

    private void loadDefinitions(Path rootPath) {
        var tagsRoot = rootPath.resolve("data");
        if (!Files.exists(tagsRoot)) {
            LOGGER.warn("Biome tag root {} is missing, structure biome tags will be empty.", tagsRoot);
            return;
        }

        try {
            Files.walk(tagsRoot)
                    .filter(Files::isRegularFile)
                    .filter(filePath -> filePath.toString().contains("/tags/worldgen/biome/") || filePath.toString().contains("\\tags\\worldgen\\biome\\"))
                    .filter(filePath -> filePath.toString().endsWith(".json"))
                    .sorted()
                    .forEach(this::readDefinition);
        } catch (IOException exception) {
            LOGGER.error("Failed to enumerate biome tag directory {}", tagsRoot, exception);
        }
    }

    private void readDefinition(Path tagFilePath) {
        try (var reader = Files.newBufferedReader(tagFilePath)) {
            var tagObject = JsonParser.parseReader(reader).getAsJsonObject();
            var valuesArray = tagObject.getAsJsonArray("values");
            if (valuesArray == null) {
                return;
            }

            var rawValues = getRawValues(valuesArray);
            var tagKey = toTagKey(tagFilePath);
            this.definitions.put(tagKey, new TagDefinition(tagKey, rawValues));
        } catch (Exception exception) {
            LOGGER.error("Failed to parse biome tag {}", tagFilePath, exception);
        }
    }

    private static Key toTagKey(Path tagFilePath) {
        var relativePath = tagFilePath;

        for (var segmentIndex = tagFilePath.getNameCount() - 1; segmentIndex >= 0; segmentIndex--) {
            if (tagFilePath.getName(segmentIndex).toString().equals("biome")) {
                relativePath = tagFilePath.subpath(segmentIndex + 1, tagFilePath.getNameCount());
                break;
            }
        }

        var tagName = relativePath.toString()
                .replace('\\', '/')
                .replace(".json", "");

        return Key.key("minecraft", tagName);
    }

    private static LinkedHashSet<String> getRawValues(JsonArray valuesArray) {
        var rawValues = new LinkedHashSet<String>();

        for (var valueElement : valuesArray) {
            if (valueElement.isJsonPrimitive()) {
                rawValues.add(valueElement.getAsString());
            } else if (valueElement.isJsonObject()) {
                var valueObject = valueElement.getAsJsonObject();
                if (valueObject.has("id")) {
                    rawValues.add(valueObject.get("id").getAsString());
                }
            }
        }

        return rawValues;
    }

    public Set<Key> biomes(Key tagKey) {
        return this.resolved.computeIfAbsent(tagKey, this::resolve);
    }

    private Set<Key> resolve(Key tagKey) {
        var tagDefinition = this.definitions.get(tagKey);
        if (tagDefinition == null) {
            return Collections.emptySet();
        }

        var resolvedBiomes = new LinkedHashSet<Key>();
        for (var entry : tagDefinition.values()) {
            if (entry.startsWith("#")) {
                var childKey = Key.key(entry.substring(1));
                resolvedBiomes.addAll(this.resolve(childKey));
                continue;
            }

            resolvedBiomes.add(Key.key(entry));
        }

        return Collections.unmodifiableSet(resolvedBiomes);
    }

    private record TagDefinition(Key tagKey, Set<String> values) {
        TagDefinition(Key tagKey, Set<String> values) {
            this.tagKey = tagKey;
            this.values = Set.copyOf(values);
        }
    }
}
