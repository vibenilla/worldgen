package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Parses vanilla {@code HolderSet<Block>} JSON shapes: a single block id, a
 * {@code #tag} reference or a list of block ids, resolved to a set of block keys.
 */
final class BlockSets {
    private BlockSets() {
    }

    static Set<Key> parse(JsonElement json, BlockTagManager blockTags) {
        if (json.isJsonArray()) {
            var blocks = new LinkedHashSet<Key>();
            for (var element : json.getAsJsonArray()) {
                blocks.add(Key.key(element.getAsString()));
            }
            return Set.copyOf(blocks);
        }

        var value = json.getAsString();
        if (value.startsWith("#")) {
            return resolveTag(Key.key(value.substring(1)), blockTags);
        }

        return Set.of(Key.key(value));
    }

    static Set<Key> resolveTag(Key tag, BlockTagManager blockTags) {
        if (blockTags == null) {
            return Set.of();
        }

        synchronized (blockTags) {
            return blockTags.blocks(tag);
        }
    }
}
