package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.Set;

/**
 * Port of vanilla {@code SpringConfiguration}. The {@code state} field is a
 * {@code FluidState} in vanilla; springs place its legacy block
 * ({@code FlowingFluid.createLegacyBlock}), so the fluid is parsed straight to
 * that block state here.
 *
 * @param state legacy block of the configured fluid state
 */
public record SpringConfiguration(
        Block state,
        boolean requiresBlockBelow,
        int rockCount,
        int holeCount,
        Set<Key> validBlocks
) implements FeatureConfiguration {

    public static SpringConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var obj = json.getAsJsonObject();
        return new SpringConfiguration(
                parseFluidState(obj.get("state")),
                !obj.has("requires_block_below") || obj.get("requires_block_below").getAsBoolean(),
                obj.has("rock_count") ? obj.get("rock_count").getAsInt() : 4,
                obj.has("hole_count") ? obj.get("hole_count").getAsInt() : 1,
                BlockSets.parse(obj.get("valid_blocks"), blockTags));
    }

    /**
     * Parses a serialized {@code FluidState} to its legacy block. Source fluids
     * map to level 0 regardless of the {@code falling} property; flowing fluids
     * map their amount to {@code 8 - min(amount, 8) + (falling ? 8 : 0)}
     * (vanilla {@code FlowingFluid.getLegacyLevel}).
     */
    private static Block parseFluidState(JsonElement json) {
        var obj = json.getAsJsonObject();
        var name = obj.get("Name").getAsString();
        var properties = obj.has("Properties") ? obj.getAsJsonObject("Properties") : null;
        var falling = properties != null && properties.has("falling")
                && Boolean.parseBoolean(properties.get("falling").getAsString());
        var amount = properties != null && properties.has("level")
                ? Integer.parseInt(properties.get("level").getAsString())
                : 8;
        var legacyLevel = 8 - Math.min(amount, 8) + (falling ? 8 : 0);

        return switch (name) {
            case "minecraft:empty" -> Block.AIR;
            case "minecraft:water" -> Block.WATER;
            case "minecraft:lava" -> Block.LAVA;
            case "minecraft:flowing_water" -> Block.WATER.withProperty("level", String.valueOf(legacyLevel));
            case "minecraft:flowing_lava" -> Block.LAVA.withProperty("level", String.valueOf(legacyLevel));
            default -> throw new IllegalArgumentException("Unknown fluid: " + name);
        };
    }
}
