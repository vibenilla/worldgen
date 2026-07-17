package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;

/** Port of vanilla's {@code SculkPatchConfiguration}. */
public record SculkPatchConfiguration(
        int chargeCount,
        int amountPerCharge,
        int spreadAttempts,
        int growthRounds,
        int spreadRounds,
        IntProvider extraRareGrowths,
        float catalystChance
) implements FeatureConfiguration {

    public static SculkPatchConfiguration fromJson(JsonElement json) {
        var object = json.getAsJsonObject();
        return new SculkPatchConfiguration(
                object.get("charge_count").getAsInt(),
                object.get("amount_per_charge").getAsInt(),
                object.get("spread_attempts").getAsInt(),
                object.get("growth_rounds").getAsInt(),
                object.get("spread_rounds").getAsInt(),
                IntProvider.fromJson(object.get("extra_rare_growths")),
                object.get("catalyst_chance").getAsFloat());
    }
}
