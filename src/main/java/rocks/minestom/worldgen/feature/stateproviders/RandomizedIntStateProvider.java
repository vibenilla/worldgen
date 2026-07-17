package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

public record RandomizedIntStateProvider(String property, BlockStateProvider source, IntProvider values) implements BlockStateProvider {
    public static final Codec<RandomizedIntStateProvider> CODEC = StructCodec.struct(
            "property", Codec.STRING, RandomizedIntStateProvider::property,
            "source", BlockStateProviders.CODEC, RandomizedIntStateProvider::source,
            "values", IntProvider.CODEC, RandomizedIntStateProvider::values,
            RandomizedIntStateProvider::new
    );

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        var base = this.source.getState(random, position);
        // Vanilla only samples the values provider when the resolved state
        // actually has the target property; if the property is absent it
        // returns the unmodified state without consuming a random draw.
        if (base.getProperty(this.property) == null) {
            return base;
        }

        var sampled = this.values.sample(random);
        return base.withProperty(this.property, Integer.toString(sampled));
    }
}

