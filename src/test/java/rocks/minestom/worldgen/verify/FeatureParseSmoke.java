package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeMap;

/**
 * Parses every vanilla configured feature JSON and reports which types parse,
 * which are unhandled and which throw.
 */
public final class FeatureParseSmoke {
    public static void main(String[] args) throws Exception {
        var root = Path.of("data/mc/datapack");
        var featuresDir = root.resolve("data/minecraft/worldgen/configured_feature");
        var blockTags = new BlockTagManager(root);

        var parsed = new TreeMap<String, Integer>();
        var unhandled = new TreeMap<String, Integer>();
        var failed = new TreeMap<String, String>();

        try (var stream = Files.list(featuresDir)) {
            for (var file : stream.sorted().toList()) {
                if (!file.toString().endsWith(".json")) {
                    continue;
                }

                var json = JsonParser.parseString(Files.readString(file));
                var type = json.getAsJsonObject().get("type").getAsString();
                try {
                    var feature = Features.parseConfiguredFeature(json, blockTags);
                    if (feature == null) {
                        unhandled.merge(type, 1, Integer::sum);
                    } else {
                        parsed.merge(type, 1, Integer::sum);
                    }
                } catch (Exception exception) {
                    failed.put(file.getFileName().toString() + " (" + type + ")", String.valueOf(exception));
                }
            }
        }

        System.out.println("=== PARSED ===");
        parsed.forEach((type, count) -> System.out.println(count + "\t" + type));
        System.out.println("=== UNHANDLED (null) ===");
        unhandled.forEach((type, count) -> System.out.println(count + "\t" + type));
        System.out.println("=== FAILED ===");
        failed.forEach((file, error) -> System.out.println(file + " -> " + error));
    }
}
