package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.processor.StructureProcessors;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code FossilFeatureConfiguration}: a parallel list of base
 * (skeleton) and overlay (ore) structure templates, each processed through its
 * own processor list, plus the empty-corner rejection threshold.
 */
public record FossilFeatureConfiguration(
        List<StructureTemplate> fossilStructures,
        List<StructureTemplate> overlayStructures,
        StructureProcessorList fossilProcessors,
        StructureProcessorList overlayProcessors,
        int maxEmptyCornersAllowed,
        BlockTagManager blockTags
) implements FeatureConfiguration {

    public FossilFeatureConfiguration {
        if (fossilStructures.isEmpty()) {
            throw new IllegalArgumentException("Fossil structure lists need at least one entry");
        }
        if (fossilStructures.size() != overlayStructures.size()) {
            throw new IllegalArgumentException("Fossil structure lists must be equal lengths");
        }
    }

    public static FossilFeatureConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("FossilFeatureConfiguration must be a JSON object");
        }

        var object = json.getAsJsonObject();
        var loader = Features.currentLoader();
        var dataPack = loader != null ? loader.dataPack() : null;

        var fossilStructures = parseTemplates(object.getAsJsonArray("fossil_structures"), dataPack);
        var overlayStructures = parseTemplates(object.getAsJsonArray("overlay_structures"), dataPack);
        var fossilProcessors = parseProcessorList(object.get("fossil_processors"), dataPack);
        var overlayProcessors = parseProcessorList(object.get("overlay_processors"), dataPack);
        var maxEmptyCornersAllowed = object.get("max_empty_corners_allowed").getAsInt();

        return new FossilFeatureConfiguration(fossilStructures, overlayStructures, fossilProcessors,
                overlayProcessors, maxEmptyCornersAllowed, blockTags);
    }

    private static List<StructureTemplate> parseTemplates(JsonArray array, DataPack dataPack) {
        var templates = new ArrayList<StructureTemplate>(array.size());
        for (var element : array) {
            templates.add(loadTemplate(dataPack, Key.key(element.getAsString())));
        }
        return List.copyOf(templates);
    }

    private static StructureTemplate loadTemplate(DataPack dataPack, Key key) {
        var path = dataPack.rootPath()
                .resolve("data")
                .resolve(key.namespace())
                .resolve("structure")
                .resolve(key.value() + ".nbt");
        return StructureTemplate.load(path);
    }

    private static StructureProcessorList parseProcessorList(JsonElement json, DataPack dataPack) {
        if (json == null) {
            return StructureProcessorList.EMPTY;
        }

        if (json.isJsonPrimitive()) {
            return StructureProcessors.parseProcessorList(dataPack.readProcessorList(Key.key(json.getAsString())));
        }

        if (json.isJsonObject()) {
            return StructureProcessors.parseProcessorList(json);
        }

        return StructureProcessorList.EMPTY;
    }
}
