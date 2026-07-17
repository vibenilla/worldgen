package rocks.minestom.worldgen.carver;

import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;
import rocks.minestom.worldgen.surface.VerticalAnchor;

import java.util.Set;

/**
 * Fields shared by every configured carver (vanilla {@code CarverConfiguration}).
 * Debug settings are intentionally not modeled.
 */
public record CarverConfiguration(
        float probability,
        CarverHeightProvider y,
        FloatProvider yScale,
        VerticalAnchor lavaLevel,
        Set<Key> replaceable) {

    public boolean canReplace(Block block) {
        return this.replaceable.contains(block.key());
    }
}
