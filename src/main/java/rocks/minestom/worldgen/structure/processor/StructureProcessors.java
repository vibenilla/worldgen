package rocks.minestom.worldgen.structure.processor;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;

import java.util.ArrayList;
import java.util.List;

public final class StructureProcessors {
    private static final Codec<ProcessorListData> PROCESSOR_LIST_CODEC = StructCodec.struct(
            "processors", Codec.RAW_VALUE.list().optional(List.of()), ProcessorListData::processors,
            ProcessorListData::new
    );

    private static final Codec<RuleProcessorData> RULE_PROCESSOR_CODEC = StructCodec.struct(
            "rules", Codec.RAW_VALUE.list().optional(List.of()), RuleProcessorData::rules,
            RuleProcessorData::new
    );

    private static final Codec<ProcessorRuleData> RULE_CODEC = StructCodec.struct(
            "input_predicate", Codec.RAW_VALUE, ProcessorRuleData::inputPredicate,
            "location_predicate", Codec.RAW_VALUE, ProcessorRuleData::locationPredicate,
            "output_state", BlockCodec.CODEC, ProcessorRuleData::outputState,
            ProcessorRuleData::new
    );

    private static final Codec<BlockStateMatchData> BLOCK_STATE_MATCH_CODEC = StructCodec.struct(
            "block_state", BlockCodec.CODEC, BlockStateMatchData::state,
            BlockStateMatchData::new
    );

    private static final Codec<BlockMatchData> BLOCK_MATCH_CODEC = StructCodec.struct(
            "block", Codec.KEY, BlockMatchData::block,
            BlockMatchData::new
    );

    private static final Codec<RandomBlockMatchData> RANDOM_BLOCK_MATCH_CODEC = StructCodec.struct(
            "block", Codec.KEY, RandomBlockMatchData::block,
            "probability", Codec.FLOAT, RandomBlockMatchData::probability,
            RandomBlockMatchData::new
    );

    private static final Codec<TagMatchData> TAG_MATCH_CODEC = StructCodec.struct(
            "tag", Codec.KEY, TagMatchData::tag,
            TagMatchData::new
    );

    private StructureProcessors() {
    }

    public static StructureProcessorList parseProcessorList(JsonElement json) {
        var processorList = PROCESSOR_LIST_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var processors = new ArrayList<StructureProcessor>();

        for (var rawProcessor : processorList.processors()) {
            var processorJson = rawProcessor.convertTo(Transcoder.JSON).orElseThrow();
            if (!processorJson.isJsonObject()) {
                continue;
            }

            var type = processorJson.getAsJsonObject().get("processor_type").getAsString();
            if (type.equals("minecraft:rule")) {
                var ruleData = RULE_PROCESSOR_CODEC.decode(Transcoder.JSON, processorJson).orElseThrow();
                processors.add(parseRuleProcessor(ruleData));
            }
        }

        if (processors.isEmpty()) {
            return StructureProcessorList.EMPTY;
        }

        return new StructureProcessorList(List.copyOf(processors));
    }

    private static RuleStructureProcessor parseRuleProcessor(RuleProcessorData data) {
        var rules = new ArrayList<ProcessorRule>();

        for (var rawRule : data.rules()) {
            var ruleJson = rawRule.convertTo(Transcoder.JSON).orElseThrow();
            var ruleData = RULE_CODEC.decode(Transcoder.JSON, ruleJson).orElseThrow();
            var input = parseRuleTest(ruleData.inputPredicate().convertTo(Transcoder.JSON).orElseThrow());
            var location = parsePosRuleTest(ruleData.locationPredicate().convertTo(Transcoder.JSON).orElseThrow());
            rules.add(new ProcessorRule(input, location, ruleData.outputState()));
        }

        return new RuleStructureProcessor(List.copyOf(rules));
    }

    private static RuleTest parseRuleTest(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Rule test must be a JSON object");
        }

        var type = json.getAsJsonObject().get("predicate_type").getAsString();
        return switch (type) {
            case "minecraft:block_match" -> {
                var decoded = BLOCK_MATCH_CODEC.decode(Transcoder.JSON, json).orElseThrow();
                yield new RuleTest.BlockMatchTest(decoded.block());
            }
            case "minecraft:random_block_match" -> {
                var decoded = RANDOM_BLOCK_MATCH_CODEC.decode(Transcoder.JSON, json).orElseThrow();
                yield new RuleTest.RandomBlockMatchTest(decoded.block(), decoded.probability());
            }
            case "minecraft:blockstate_match" -> {
                var decoded = BLOCK_STATE_MATCH_CODEC.decode(Transcoder.JSON, json).orElseThrow();
                yield new RuleTest.BlockStateMatchTest(decoded.state());
            }
            case "minecraft:tag_match" -> {
                var decoded = TAG_MATCH_CODEC.decode(Transcoder.JSON, json).orElseThrow();
                yield new RuleTest.TagMatchTest(decoded.tag());
            }
            default -> throw new IllegalArgumentException("Unsupported rule test: " + type);
        };
    }

    private static PosRuleTest parsePosRuleTest(JsonElement json) {
        if (!json.isJsonObject()) {
            return PosRuleTest.AlwaysTrueTest.INSTANCE;
        }

        var type = json.getAsJsonObject().get("predicate_type").getAsString();
        if (type.equals("minecraft:always_true")) {
            return PosRuleTest.AlwaysTrueTest.INSTANCE;
        }

        return PosRuleTest.AlwaysFalseTest.INSTANCE;
    }

    private record ProcessorListData(List<Codec.RawValue> processors) {
    }

    private record RuleProcessorData(List<Codec.RawValue> rules) {
    }

    private record ProcessorRuleData(Codec.RawValue inputPredicate, Codec.RawValue locationPredicate, Block outputState) {
    }

    private record BlockStateMatchData(Block state) {
    }

    private record BlockMatchData(Key block) {
    }

    private record RandomBlockMatchData(Key block, float probability) {
    }

    private record TagMatchData(Key tag) {
    }
}
