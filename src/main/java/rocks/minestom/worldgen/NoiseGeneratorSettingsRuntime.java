package rocks.minestom.worldgen;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.biome.ClimateSampler;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.surface.SurfaceRules;
import rocks.minestom.worldgen.surface.SurfaceSystem;

public record NoiseGeneratorSettingsRuntime(
        int minY,
        int height,
        int cellWidth,
        int cellHeight,
        int seaLevel,
        Block defaultBlock,
        Block defaultFluid,
        DensityFunction finalDensity,
        ClimateSampler climateSampler,
        RandomState randomState,
        SurfaceSystem surfaceSystem,
        SurfaceRules.RuleSource surfaceRule
) {
    public int maxYInclusive() {
        return this.minY + this.height - 1;
    }
}
