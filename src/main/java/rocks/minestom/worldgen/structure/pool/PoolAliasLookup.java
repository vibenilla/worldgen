package rocks.minestom.worldgen.structure.pool;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.structure.StructureRng;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vanilla {@code PoolAliasLookup}: resolves aliases with a positional random
 * forked from the world seed at the structure start position.
 */
@FunctionalInterface
public interface PoolAliasLookup {
    PoolAliasLookup EMPTY = key -> key;

    Key lookup(Key alias);

    static PoolAliasLookup create(List<PoolAliasBinding> bindings, BlockVec pos, long seed) {
        if (bindings.isEmpty()) {
            return EMPTY;
        }

        // RandomSource.create(seed).forkPositional().at(pos), with vanilla's
        // int-overflow position hash (VMath.getSeed widens too early).
        var positionalSeed = new LegacyRandomSource(seed).nextLong();
        var random = new LegacyRandomSource(
                StructureRng.getSeed(pos.blockX(), pos.blockY(), pos.blockZ()) ^ positionalSeed);
        Map<Key, Key> mappings = new HashMap<>();
        for (var binding : bindings) {
            binding.forEachResolved(random, mappings::put);
        }

        return key -> mappings.getOrDefault(key, key);
    }
}
