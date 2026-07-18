package rocks.minestom.worldgen.verify;

import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.density.DensityFunction;

import java.nio.file.Path;

/** Prints our and vanilla single-point finalDensity along a column. Args: seed dimension x z minY maxY */
public final class DensityPoint {
    public static void main(String[] args) throws Exception {
        var seed = Long.parseLong(args[0]);
        var dimension = args[1];
        int x = Integer.parseInt(args[2]), z = Integer.parseInt(args[3]);
        int minY = Integer.parseInt(args[4]), maxY = Integer.parseInt(args[5]);

        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var settingsKey = switch (dimension) {
            case "nether" -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.NETHER;
            case "end" -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.END;
            default -> net.minecraft.world.level.levelgen.NoiseGeneratorSettings.OVERWORLD;
        };
        var vanillaSettings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(settingsKey).value();
        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                vanillaSettings, lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE), seed);
        var vanillaRouter = randomState.router();

        net.minestom.server.MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), seed);
        var ours = switch (dimension) {
            case "nether" -> generators.netherSettings();
            case "end" -> generators.endSettings();
            default -> generators.overworldSettings();
        };

        for (var y = maxY; y >= minY; y--) {
            var vanilla = vanillaRouter.finalDensity()
                    .compute(new net.minecraft.world.level.levelgen.DensityFunction.SinglePointContext(x, y, z));
            var mine = ours.finalDensity().compute(new DensityFunction.SinglePointContext(x, y, z));
            System.out.println("POINT " + x + " " + y + " " + z + " vanilla=" + vanilla + " ours=" + mine
                    + (vanilla == mine ? "" : "  DIFF"));
        }
        System.exit(0);
    }
}
