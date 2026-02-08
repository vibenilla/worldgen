package rocks.minestom.worldgen.feature.valueproviders;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;

public final class WeightedListIntProvider implements IntProvider {
    public static final Codec<WeightedListIntProvider> CODEC = StructCodec.struct(
            "distribution", Entry.CODEC.list(), provider -> provider.distribution,
            WeightedListIntProvider::new);

    private final List<Entry> distribution;
    private final int totalWeight;

    public WeightedListIntProvider(List<Entry> distribution) {
        this.distribution = distribution;
        var totalWeight = 0;

        for (var entry : distribution) {
            totalWeight += Math.max(0, entry.weight());
        }

        this.totalWeight = totalWeight;
    }

    @Override
    public Key type() {
        return Key.key("minecraft:weighted_list");
    }

    @Override
    public int sample(RandomSource random) {
        if (this.distribution.isEmpty() || this.totalWeight <= 0) {
            return 0;
        }

        var selectedWeight = random.nextInt(this.totalWeight);

        for (var entry : this.distribution) {
            selectedWeight -= entry.weight();

            if (selectedWeight < 0) {
                return entry.data().sample(random);
            }
        }

        return this.distribution.getLast().data().sample(random);
    }

    public record Entry(IntProvider data, int weight) {
        public static final Codec<Entry> CODEC = StructCodec.struct(
                "data", IntProvider.CODEC, Entry::data,
                "weight", Codec.INT, Entry::weight,
                Entry::new);
    }
}
