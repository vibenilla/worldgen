package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.biome.BiomeClimate;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.surface.DataPackBiomeResolver;

import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares the ported biome climate queries (height-adjusted temperature,
 * frozen temperature modifier, cold-enough-to-snow) against the real vanilla
 * Biome implementations across many positions and biomes.
 */
final class ClimateParityTest {
    @Test
    void coldEnoughToSnowMatchesVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var biomes = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
        var resolver = new DataPackBiomeResolver(new DataPack(Path.of("data/mc/datapack")));
        var seaLevel = 63;

        var names = new String[]{
                "plains", "desert", "snowy_taiga", "snowy_plains", "frozen_ocean",
                "deep_frozen_ocean", "windswept_hills", "jagged_peaks", "grove", "taiga"};
        var random = new Random(99);

        for (var name : names) {
            var vanillaBiome = biomes.getOrThrow(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.BIOME,
                    net.minecraft.resources.Identifier.parse("minecraft:" + name))).value();
            var biomeKey = Key.key("minecraft:" + name);

            for (var index = 0; index < 2000; index++) {
                var x = random.nextInt(-100000, 100000);
                var y = random.nextInt(-64, 320);
                var z = random.nextInt(-100000, 100000);
                var position = new net.minecraft.core.BlockPos(x, y, z);

                assertEquals(vanillaBiome.coldEnoughToSnow(position, seaLevel),
                        BiomeClimate.coldEnoughToSnow(resolver, biomeKey, x, y, z, seaLevel),
                        name + " at " + x + "," + y + "," + z);
            }
        }
    }
}
