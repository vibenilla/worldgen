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

import java.util.ArrayList;
import java.util.List;

public final class DualNoiseProvider extends NoiseProvider {
    public static final Codec<DualNoiseProvider> CODEC = StructCodec.struct(
            "variety", InclusiveRange.CODEC, DualNoiseProvider::variety,
            "slow_noise", NoiseParameters.CODEC, DualNoiseProvider::slowNoiseParameters,
            "slow_scale", Codec.FLOAT, DualNoiseProvider::slowScale,
            "seed", Codec.LONG, DualNoiseProvider::seed,
            "noise", NoiseParameters.CODEC, DualNoiseProvider::noiseParameters,
            "scale", Codec.FLOAT, DualNoiseProvider::scale,
            "states", BlockCodec.CODEC.list(), DualNoiseProvider::states,
            DualNoiseProvider::new);

    private final InclusiveRange variety;
    private final NoiseParameters slowNoiseParameters;
    private final float slowScale;
    private final NormalNoise slowNoise;

    public DualNoiseProvider(
            InclusiveRange variety,
            NoiseParameters slowNoiseParameters,
            float slowScale,
            long seed,
            NoiseParameters noiseParameters,
            float scale,
            List<Block> states
    ) {
        super(seed, noiseParameters, scale, states);
        this.variety = variety;
        this.slowNoiseParameters = slowNoiseParameters;
        this.slowScale = slowScale;
        this.slowNoise = NormalNoise.create(new XoroshiroRandomSource(seed),
                new NormalNoise.NoiseParameters(
                        slowNoiseParameters.firstOctave(),
                        slowNoiseParameters.amplitudes().stream().mapToDouble(Double::doubleValue).toArray()));
    }

    public InclusiveRange variety() {
        return this.variety;
    }

    public NoiseParameters slowNoiseParameters() {
        return this.slowNoiseParameters;
    }

    public float slowScale() {
        return this.slowScale;
    }

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        var slowNoiseValue = this.getSlowNoise(position);
        var varietyCount = (int) Math.max(this.variety.minInclusive(),
                Math.min(this.variety.maxInclusive() + 1.0D,
                        this.variety.minInclusive() + (slowNoiseValue + 1.0D) * 0.5D * (this.variety.maxInclusive() - this.variety.minInclusive() + 1.0D)));
        var variedStates = new ArrayList<Block>();

        for (var varietyIndex = 0; varietyIndex < varietyCount; varietyIndex++) {
            var sampledPosition = position.add(varietyIndex * 54545, 0, varietyIndex * 34234);
            variedStates.add(this.getRandomState(this.states, sampledPosition, this.slowScale));
        }

        if (variedStates.isEmpty()) {
            return this.states.getFirst();
        }

        return this.getRandomState(variedStates, position, this.scale);
    }

    private double getSlowNoise(BlockVec position) {
        return this.slowNoise.getValue(position.blockX() * this.slowScale, position.blockY() * this.slowScale,
                position.blockZ() * this.slowScale);
    }

    public record InclusiveRange(int minInclusive, int maxInclusive) {
        public static final Codec<InclusiveRange> CODEC = StructCodec.struct(
                "min_inclusive", Codec.INT, InclusiveRange::minInclusive,
                "max_inclusive", Codec.INT, InclusiveRange::maxInclusive,
                InclusiveRange::new);
    }
}
