package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.NoiseParameters;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;

public final class NoiseThresholdProvider extends NoiseProvider {
    public static final Codec<NoiseThresholdProvider> CODEC = StructCodec.struct(
            "seed", Codec.LONG, NoiseThresholdProvider::seed,
            "noise", NoiseParameters.CODEC, NoiseThresholdProvider::noiseParameters,
            "scale", Codec.FLOAT, NoiseThresholdProvider::scale,
            "threshold", Codec.FLOAT, NoiseThresholdProvider::threshold,
            "high_chance", Codec.FLOAT, NoiseThresholdProvider::highChance,
            "default_state", BlockCodec.CODEC, NoiseThresholdProvider::defaultState,
            "low_states", BlockCodec.CODEC.list(), NoiseThresholdProvider::lowStates,
            "high_states", BlockCodec.CODEC.list(), NoiseThresholdProvider::highStates,
            NoiseThresholdProvider::new);

    private final float threshold;
    private final float highChance;
    private final Block defaultState;
    private final List<Block> lowStates;
    private final List<Block> highStates;

    public NoiseThresholdProvider(
            long seed,
            NoiseParameters noiseParameters,
            float scale,
            float threshold,
            float highChance,
            Block defaultState,
            List<Block> lowStates,
            List<Block> highStates
    ) {
        super(seed, noiseParameters, scale, List.of(defaultState));
        this.threshold = threshold;
        this.highChance = highChance;
        this.defaultState = defaultState;
        this.lowStates = List.copyOf(lowStates);
        this.highStates = List.copyOf(highStates);
    }

    public float threshold() {
        return this.threshold;
    }

    public float highChance() {
        return this.highChance;
    }

    public Block defaultState() {
        return this.defaultState;
    }

    public List<Block> lowStates() {
        return this.lowStates;
    }

    public List<Block> highStates() {
        return this.highStates;
    }

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        var noiseValue = this.getNoiseValue(position, this.scale);
        if (noiseValue < this.threshold) {
            return this.lowStates.get(random.nextInt(this.lowStates.size()));
        }

        if (random.nextFloat() < this.highChance) {
            return this.highStates.get(random.nextInt(this.highStates.size()));
        }

        return this.defaultState;
    }
}
