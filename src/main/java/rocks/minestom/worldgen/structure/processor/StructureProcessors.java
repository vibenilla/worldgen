package rocks.minestom.worldgen.structure.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public final class StructureProcessors {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructureProcessors.class);

    private StructureProcessors() {
    }

    public static StructureProcessorList parseProcessorList(JsonElement json) {
        if (!json.isJsonObject()) {
            return StructureProcessorList.EMPTY;
        }

        var processorsTag = json.getAsJsonObject().get("processors");
        if (processorsTag == null || !processorsTag.isJsonArray()) {
            return StructureProcessorList.EMPTY;
        }

        var processors = new ArrayList<StructureProcessor>();
        for (var entry : processorsTag.getAsJsonArray()) {
            var processor = parseProcessor(entry);
            if (processor != null) {
                processors.add(processor);
            }
        }

        if (processors.isEmpty()) {
            return StructureProcessorList.EMPTY;
        }

        return new StructureProcessorList(List.copyOf(processors));
    }

    private static StructureProcessor parseProcessor(JsonElement json) {
        if (!json.isJsonObject()) {
            return null;
        }

        var obj = json.getAsJsonObject();
        var type = obj.get("processor_type").getAsString();
        return switch (type) {
            case "minecraft:rule" -> parseRuleProcessor(obj);
            case "minecraft:block_rot" -> {
                var rottable = obj.has("rottable_blocks") ? parseTag(obj.get("rottable_blocks")) : null;
                yield new BlockRotProcessor(rottable, obj.get("integrity").getAsFloat());
            }
            case "minecraft:protected_blocks" -> new ProtectedBlockProcessor(parseTag(obj.get("value")));
            case "minecraft:capped" -> {
                var delegate = parseProcessor(obj.get("delegate"));
                if (delegate == null) {
                    yield null;
                }
                yield new CappedProcessor(delegate, parseIntLimit(obj.get("limit")));
            }
            case "minecraft:nop" -> null;
            default -> {
                LOGGER.warn("Unsupported processor type: {}", type);
                yield null;
            }
        };
    }

    private static RuleStructureProcessor parseRuleProcessor(JsonObject obj) {
        var rules = new ArrayList<ProcessorRule>();
        var rulesTag = obj.get("rules");
        if (rulesTag != null && rulesTag.isJsonArray()) {
            for (var ruleEntry : rulesTag.getAsJsonArray()) {
                var rule = ruleEntry.getAsJsonObject();
                var input = parseRuleTest(rule.get("input_predicate"));
                var location = parseRuleTest(rule.get("location_predicate"));
                var position = rule.has("position_predicate")
                        ? parsePosRuleTest(rule.get("position_predicate"))
                        : PosRuleTest.PosAlwaysTrueTest.INSTANCE;
                var output = parseBlockState(rule.get("output_state"));
                rules.add(new ProcessorRule(input, location, position, output));
            }
        }

        return new RuleStructureProcessor(List.copyOf(rules));
    }

    private static RuleTest parseRuleTest(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return RuleTest.AlwaysTrueTest.INSTANCE;
        }

        var obj = json.getAsJsonObject();
        var type = obj.get("predicate_type").getAsString();
        return switch (type) {
            case "minecraft:always_true" -> RuleTest.AlwaysTrueTest.INSTANCE;
            case "minecraft:block_match" -> new RuleTest.BlockMatchTest(Key.key(obj.get("block").getAsString()));
            case "minecraft:blockstate_match" -> new RuleTest.BlockStateMatchTest(parseBlockState(obj.get("block_state")));
            case "minecraft:random_block_match" -> new RuleTest.RandomBlockMatchTest(
                    Key.key(obj.get("block").getAsString()), obj.get("probability").getAsFloat());
            case "minecraft:random_blockstate_match" -> new RuleTest.RandomBlockStateMatchTest(
                    parseBlockState(obj.get("block_state")), obj.get("probability").getAsFloat());
            case "minecraft:tag_match" -> new RuleTest.TagMatchTest(Key.key(obj.get("tag").getAsString()));
            default -> throw new IllegalArgumentException("Unsupported rule test: " + type);
        };
    }

    private static PosRuleTest parsePosRuleTest(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return PosRuleTest.PosAlwaysTrueTest.INSTANCE;
        }

        var obj = json.getAsJsonObject();
        var type = obj.get("predicate_type").getAsString();
        return switch (type) {
            case "minecraft:always_true" -> PosRuleTest.PosAlwaysTrueTest.INSTANCE;
            case "minecraft:linear_pos" -> new PosRuleTest.LinearPosTest(
                    getFloat(obj, "min_chance", 0.0F),
                    getFloat(obj, "max_chance", 0.0F),
                    getInt(obj, "min_dist", 0),
                    getInt(obj, "max_dist", 0));
            case "minecraft:axis_aligned_linear_pos" -> new PosRuleTest.AxisAlignedLinearPosTest(
                    getFloat(obj, "min_chance", 0.0F),
                    getFloat(obj, "max_chance", 0.0F),
                    getInt(obj, "min_dist", 0),
                    getInt(obj, "max_dist", 0),
                    obj.has("axis") ? obj.get("axis").getAsString() : "y");
            default -> PosRuleTest.PosAlwaysTrueTest.INSTANCE;
        };
    }

    /**
     * Parses the vanilla block state JSON form {@code {"Name": ..., "Properties": {...}}}.
     */
    private static Block parseBlockState(JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return Block.AIR;
        }

        var obj = json.getAsJsonObject();
        var block = Block.fromKey(obj.get("Name").getAsString());
        if (block == null) {
            throw new IllegalArgumentException("Unknown block in output_state: " + obj.get("Name"));
        }

        var propertiesTag = obj.get("Properties");
        if (propertiesTag != null && propertiesTag.isJsonObject()) {
            var properties = new HashMap<String, String>();
            for (var entry : propertiesTag.getAsJsonObject().entrySet()) {
                properties.put(entry.getKey(), entry.getValue().getAsString());
            }
            if (!properties.isEmpty()) {
                block = block.withProperties(properties);
            }
        }

        return block;
    }

    /**
     * The datapack tags used here are plain {@code #tag} or {@code tag}
     * strings referencing block tags.
     */
    private static Key parseTag(JsonElement json) {
        var value = json.getAsString();
        return Key.key(value.startsWith("#") ? value.substring(1) : value);
    }

    /**
     * Capped limits in the vanilla datapack are constant ints (an int provider
     * in general; constants never draw from the random).
     */
    private static int parseIntLimit(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return json.getAsInt();
        }
        if (json.isJsonObject() && json.getAsJsonObject().has("value")) {
            return json.getAsJsonObject().get("value").getAsInt();
        }
        LOGGER.warn("Unsupported capped limit provider: {}", json);
        return 0;
    }

    private static float getFloat(JsonObject obj, String key, float defaultValue) {
        return obj.has(key) ? obj.get(key).getAsFloat() : defaultValue;
    }

    private static int getInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }
}
