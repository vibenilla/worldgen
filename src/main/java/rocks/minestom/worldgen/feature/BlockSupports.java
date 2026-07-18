package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.Set;

/**
 * Runtime access to the 26.x per-block {@code #minecraft:supports_<name>}
 * block tags that carry most plant survival rules. The tag manager is
 * installed by {@link FeatureLoader} when the data pack loads.
 */
public final class BlockSupports {
    private static volatile BlockTagManager blockTags;

    private BlockSupports() {
    }

    public static void install(BlockTagManager manager) {
        blockTags = manager;
    }

    /**
     * The support set for the given block state, or null when no
     * {@code supports_<name>} tag exists for it.
     */
    public static Set<Key> supportsOf(Block block) {
        var manager = blockTags;
        if (manager == null) {
            return null;
        }
        Set<Key> supported;
        synchronized (manager) {
            supported = manager.blocks(Key.key("minecraft:supports_" + block.key().value()));
        }
        return supported == null || supported.isEmpty() ? null : supported;
    }
}
