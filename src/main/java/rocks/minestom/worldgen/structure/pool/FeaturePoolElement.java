package rocks.minestom.worldgen.structure.pool;

import net.kyori.adventure.key.Key;

public record FeaturePoolElement(Key feature, String projection) implements PoolElement {
}
