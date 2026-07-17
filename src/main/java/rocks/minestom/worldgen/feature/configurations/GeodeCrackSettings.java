package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;

/**
 * Port of vanilla {@code GeodeCrackSettings}: chance, base size and point
 * offset for the optional crack carved through a geode.
 */
public record GeodeCrackSettings(double generateCrackChance, double baseCrackSize, int crackPointOffset) {
    public static final GeodeCrackSettings DEFAULT = new GeodeCrackSettings(1.0, 2.0, 2);

    public static GeodeCrackSettings fromJson(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("GeodeCrackSettings must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        return new GeodeCrackSettings(
                obj.has("generate_crack_chance") ? obj.get("generate_crack_chance").getAsDouble() : DEFAULT.generateCrackChance(),
                obj.has("base_crack_size") ? obj.get("base_crack_size").getAsDouble() : DEFAULT.baseCrackSize(),
                obj.has("crack_point_offset") ? obj.get("crack_point_offset").getAsInt() : DEFAULT.crackPointOffset());
    }
}
