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

public final class StructureTagManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructureTagManager.class);

    private final Map<Key, TagDefinition> definitions;
    private final Map<Key, Set<Key>> resolved;

    public StructureTagManager(Path rootPath) {
        this.definitions = new LinkedHashMap<>();
        this.resolved = new IdentityHashMap<>();
        this.loadDefinitions(rootPath);
    }

    private void loadDefinitions(Path rootPath) {
        var tagsRoot = rootPath.resolve("data");
        if (!Files.exists(tagsRoot)) {
            LOGGER.warn("Structure tag root {} is missing, structure tags will be empty.", tagsRoot);
            return;
        }

        try {
            Files.walk(tagsRoot)
                    .filter(Files::isRegularFile)
                    .filter(filePath -> filePath.toString().contains("/tags/worldgen/structure/") || filePath.toString().contains("\\tags\\worldgen\\structure\\"))
                    .filter(filePath -> filePath.toString().endsWith(".json"))
                    .sorted()
                    .forEach(this::readDefinition);
        } catch (IOException exception) {
            LOGGER.error("Failed to enumerate structure tag directory {}", tagsRoot, exception);
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
            LOGGER.error("Failed to parse structure tag {}", tagFilePath, exception);
        }
    }

    private static Key toTagKey(Path tagFilePath) {
        var relativePath = tagFilePath;

        for (var segmentIndex = tagFilePath.getNameCount() - 1; segmentIndex >= 0; segmentIndex--) {
            if (tagFilePath.getName(segmentIndex).toString().equals("structure")) {
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

    public Set<Key> structures(Key tagKey) {
        return this.resolved.computeIfAbsent(tagKey, this::resolve);
    }

    public Set<Key> tagKeys() {
        return Set.copyOf(this.definitions.keySet());
    }

    private Set<Key> resolve(Key tagKey) {
        var tagDefinition = this.definitions.get(tagKey);
        if (tagDefinition == null) {
            return Collections.emptySet();
        }

        var resolvedStructures = new LinkedHashSet<Key>();
        for (var entry : tagDefinition.values()) {
            if (entry.startsWith("#")) {
                var childKey = Key.key(entry.substring(1));
                resolvedStructures.addAll(this.resolve(childKey));
                continue;
            }

            resolvedStructures.add(Key.key(entry));
        }

        return Collections.unmodifiableSet(resolvedStructures);
    }

    private record TagDefinition(Key tagKey, Set<String> values) {
        TagDefinition(Key tagKey, Set<String> values) {
            this.tagKey = tagKey;
            this.values = Set.copyOf(values);
        }
    }
}
