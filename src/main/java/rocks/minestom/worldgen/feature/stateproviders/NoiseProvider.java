package rocks.minestom.worldgen.feature.stateproviders;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.NoiseParameters;
import rocks.minestom.worldgen.noise.NormalNoise;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;

import java.util.List;

public class NoiseProvider implements BlockStateProvider {
    public static final Codec<NoiseProvider> CODEC = StructCodec.struct(
            "seed", Codec.LONG, NoiseProvider::seed,
            "noise", NoiseParameters.CODEC, NoiseProvider::noiseParameters,
            "scale", Codec.FLOAT, NoiseProvider::scale,
            "states", BlockCodec.CODEC.list(), NoiseProvider::states,
            NoiseProvider::new);

    protected final long seed;
    protected final NoiseParameters noiseParameters;
    protected final float scale;
    protected final List<Block> states;
    protected final NormalNoise noise;

    public NoiseProvider(long seed, NoiseParameters noiseParameters, float scale, List<Block> states) {
        this.seed = seed;
        this.noiseParameters = noiseParameters;
        this.scale = scale;
        this.states = List.copyOf(states);
        this.noise = NormalNoise.create(new XoroshiroRandomSource(seed),
                new NormalNoise.NoiseParameters(
                        noiseParameters.firstOctave(),
                        noiseParameters.amplitudes().stream().mapToDouble(Double::doubleValue).toArray()));
    }

    public long seed() {
        return this.seed;
    }

    public NoiseParameters noiseParameters() {
        return this.noiseParameters;
    }

    public float scale() {
        return this.scale;
    }

    public List<Block> states() {
        return this.states;
    }

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        return this.getRandomState(this.states, position, this.scale);
    }

    protected Block getRandomState(List<Block> choices, BlockVec position, float scaleValue) {
        var noiseValue = this.getNoiseValue(position, scaleValue);
        var clamped = Math.max(0.0D, Math.min(0.9999D, (1.0D + noiseValue) / 2.0D));
        return choices.get((int) (clamped * (double) choices.size()));
    }

    protected double getNoiseValue(BlockVec position, float scaleValue) {
        return this.noise.getValue(position.blockX() * scaleValue, position.blockY() * scaleValue, position.blockZ() * scaleValue);
    }
}
