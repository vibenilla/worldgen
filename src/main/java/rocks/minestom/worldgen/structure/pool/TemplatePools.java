package rocks.minestom.worldgen.structure.pool;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.LiquidSettings;

import java.util.ArrayList;
import java.util.List;

public final class TemplatePools {
    private TemplatePools() {
    }

    public static TemplatePool parseTemplatePool(JsonElement json, StructureLoader loader) {
        var obj = json.getAsJsonObject();
        var fallback = Key.key(obj.get("fallback").getAsString());
        var elements = new ArrayList<TemplatePool.PoolElementEntry>();

        var elementsTag = obj.get("elements");
        if (elementsTag != null && elementsTag.isJsonArray()) {
            for (var entryTag : elementsTag.getAsJsonArray()) {
                var entry = entryTag.getAsJsonObject();
                var element = parseElement(entry.get("element"), loader);
                if (element == null) {
                    continue;
                }
                elements.add(new TemplatePool.PoolElementEntry(element, entry.get("weight").getAsInt()));
            }
        }

        return new TemplatePool(List.copyOf(elements), fallback);
    }

    private static PoolElement parseElement(JsonElement json, StructureLoader loader) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }

        var obj = json.getAsJsonObject();
        var type = obj.get("element_type").getAsString();
        var projection = Projection.fromName(obj.has("projection") ? obj.get("projection").getAsString() : "rigid");

        return switch (type) {
            case "minecraft:legacy_single_pool_element", "minecraft:single_pool_element" -> {
                var location = Key.key(obj.get("location").getAsString());
                var processors = loader.resolveProcessors(obj.get("processors"));
                var overrideLiquidSettings = obj.has("override_liquid_settings")
                        ? LiquidSettings.fromName(obj.get("override_liquid_settings").getAsString())
                        : null;
                yield new SinglePoolElement(location, processors, projection, overrideLiquidSettings,
                        type.equals("minecraft:legacy_single_pool_element"));
            }
            case "minecraft:feature_pool_element" ->
                    new FeaturePoolElement(Key.key(obj.get("feature").getAsString()), projection);
            case "minecraft:list_pool_element" -> {
                var children = new ArrayList<PoolElement>();
                for (var childTag : obj.getAsJsonArray("elements")) {
                    var child = parseElement(childTag, loader);
                    if (child != null) {
                        children.add(child);
                    }
                }
                yield new ListPoolElement(List.copyOf(children), projection);
            }
            case "minecraft:empty_pool_element" -> EmptyPoolElement.INSTANCE;
            default -> null;
        };
    }

    /** Kept for callers that resolve processors from raw JSON. */
    public static StructureProcessorList resolveProcessors(JsonElement json, StructureLoader loader) {
        return loader.resolveProcessors(json);
    }
}
