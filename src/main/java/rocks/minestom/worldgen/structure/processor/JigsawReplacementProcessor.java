package rocks.minestom.worldgen.structure.processor;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla {@code JigsawReplacementProcessor}: jigsaw blocks are replaced by
 * their {@code final_state}; {@code structure_void} final states drop the
 * block entirely.
 */
public final class JigsawReplacementProcessor implements StructureProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(JigsawReplacementProcessor.class);
    public static final JigsawReplacementProcessor INSTANCE = new JigsawReplacementProcessor();

    private JigsawReplacementProcessor() {
    }

    @Override
    public StructureBlockInfo processBlock(
            StructureProcessorContext context,
            BlockVec templateRelativePos,
            StructureBlockInfo processedBlockInfo) {
        if (!"minecraft:jigsaw".equals(processedBlockInfo.state().key().asString())) {
            return processedBlockInfo;
        }

        var nbt = processedBlockInfo.nbt();
        if (nbt == null) {
            LOGGER.warn("Jigsaw block at {} is missing nbt, will not replace", processedBlockInfo.pos());
            return processedBlockInfo;
        }

        var stateString = nbt.getString("final_state", "minecraft:air");
        var state = parseBlockState(stateString);
        if (state == null) {
            LOGGER.error("Failed to parse jigsaw replacement state '{}' at {}", stateString, processedBlockInfo.pos());
            return null;
        }

        return "minecraft:structure_void".equals(state.key().asString())
                ? null
                : new StructureBlockInfo(processedBlockInfo.pos(), state, null);
    }

    /**
     * Parses vanilla's block state string form {@code name[key=value,...]}.
     */
    public static Block parseBlockState(String value) {
        var bracket = value.indexOf('[');
        var name = bracket < 0 ? value : value.substring(0, bracket);
        var block = Block.fromKey(name.trim());
        if (block == null) {
            return null;
        }

        if (bracket < 0) {
            return block;
        }

        var end = value.indexOf(']', bracket);
        if (end < 0) {
            return null;
        }

        var propertyPart = value.substring(bracket + 1, end).trim();
        if (propertyPart.isEmpty()) {
            return block;
        }

        Map<String, String> properties = new HashMap<>();
        for (var entry : propertyPart.split(",")) {
            var eq = entry.indexOf('=');
            if (eq < 0) {
                return null;
            }
            properties.put(entry.substring(0, eq).trim(), entry.substring(eq + 1).trim());
        }

        try {
            return block.withProperties(properties);
        } catch (Exception exception) {
            return null;
        }
    }
}
