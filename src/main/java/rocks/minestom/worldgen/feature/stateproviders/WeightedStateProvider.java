package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;

public final class WeightedStateProvider implements BlockStateProvider {
    public static final Codec<WeightedStateProvider> CODEC = StructCodec.struct(
            "entries", Entry.CODEC.list(), provider -> provider.entries,
            WeightedStateProvider::new
    );

    private final List<Entry> entries;
    private final int totalWeight;

    public WeightedStateProvider(List<Entry> entries) {
        this.entries = entries;

        var totalWeight = 0;
        for (var entry : entries) {
            totalWeight += Math.max(0, entry.weight());
        }
        this.totalWeight = totalWeight;
    }

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        if (this.entries.isEmpty() || this.totalWeight <= 0) {
            return Block.AIR;
        }

        var selectedWeight = random.nextInt(this.totalWeight);
        for (var entry : this.entries) {
            selectedWeight -= entry.weight();
            if (selectedWeight < 0) {
                return entry.data();
            }
        }

        return this.entries.getLast().data();
    }

    public record Entry(Block data, int weight) {
        public static final Codec<Entry> CODEC = StructCodec.struct(
                "data", BlockCodec.CODEC, Entry::data,
                "weight", Codec.INT, Entry::weight,
                Entry::new
        );
    }
}
