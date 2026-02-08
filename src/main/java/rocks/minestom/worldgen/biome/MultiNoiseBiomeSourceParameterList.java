package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.List;

public final class MultiNoiseBiomeSourceParameterList {
    private MultiNoiseBiomeSourceParameterList() {
    }

    public static Climate.ParameterList<Key> preset(Key preset) {
        return switch (preset.asString()) {
            case "minecraft:nether" -> nether();
            case "minecraft:overworld" -> overworld();
            default -> throw new IllegalArgumentException("Unknown multi-noise preset: " + preset.asString());
        };
    }

    private static Climate.ParameterList<Key> nether() {
        return new Climate.ParameterList<>(List.of(
                new Pair<>(Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), Key.key("minecraft:nether_wastes")),
                new Pair<>(Climate.parameters(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), Key.key("minecraft:soul_sand_valley")),
                new Pair<>(Climate.parameters(0.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), Key.key("minecraft:crimson_forest")),
                new Pair<>(Climate.parameters(0.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.375F), Key.key("minecraft:warped_forest")),
                new Pair<>(Climate.parameters(-0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.175F), Key.key("minecraft:basalt_deltas"))
        ));
    }

    private static Climate.ParameterList<Key> overworld() {
        var parameters = new ArrayList<Pair<Climate.ParameterPoint, Key>>();
        var builder = new OverworldBiomeBuilder();
        builder.addBiomes(parameters::add);
        return new Climate.ParameterList<>(parameters);
    }
}
