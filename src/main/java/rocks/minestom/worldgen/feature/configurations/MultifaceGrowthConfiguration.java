package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Port of vanilla's {@code MultifaceGrowthConfiguration} (glow lichen, sculk
 * vein growth).
 */
public record MultifaceGrowthConfiguration(
        Block placeBlock,
        int searchRange,
        boolean canPlaceOnFloor,
        boolean canPlaceOnCeiling,
        boolean canPlaceOnWall,
        float chanceOfSpreading,
        Set<String> canBePlacedOn,
        List<Direction> validDirections
) implements FeatureConfiguration {

    public static MultifaceGrowthConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();

        var block = Block.fromKey(object.get("block").getAsString());
        var searchRange = object.has("search_range") ? object.get("search_range").getAsInt() : 10;
        var canPlaceOnFloor = object.has("can_place_on_floor") && object.get("can_place_on_floor").getAsBoolean();
        var canPlaceOnCeiling = object.has("can_place_on_ceiling") && object.get("can_place_on_ceiling").getAsBoolean();
        var canPlaceOnWall = object.has("can_place_on_wall") && object.get("can_place_on_wall").getAsBoolean();
        var chanceOfSpreading = object.has("chance_of_spreading") ? object.get("chance_of_spreading").getAsFloat() : 0.5F;

        var canBePlacedOn = new HashSet<String>();
        var placedOnElement = object.get("can_be_placed_on");
        var entries = placedOnElement.isJsonArray() ? placedOnElement.getAsJsonArray() : null;
        if (entries != null) {
            for (var entry : entries) {
                addBlockOrTag(canBePlacedOn, entry.getAsString(), blockTags);
            }
        } else {
            addBlockOrTag(canBePlacedOn, placedOnElement.getAsString(), blockTags);
        }

        // Vanilla builds the valid direction list as ceiling, floor, then the
        // horizontal plane; the shuffles draw from it in this order
        var validDirections = new ArrayList<Direction>(6);
        if (canPlaceOnCeiling) {
            validDirections.add(Direction.UP);
        }
        if (canPlaceOnFloor) {
            validDirections.add(Direction.DOWN);
        }
        if (canPlaceOnWall) {
            validDirections.addAll(Direction.HORIZONTAL);
        }

        return new MultifaceGrowthConfiguration(block, searchRange, canPlaceOnFloor, canPlaceOnCeiling,
                canPlaceOnWall, chanceOfSpreading, Set.copyOf(canBePlacedOn), List.copyOf(validDirections));
    }

    private static void addBlockOrTag(Set<String> target, String value, BlockTagManager blockTags) {
        if (value.startsWith("#")) {
            if (blockTags != null) {
                for (var key : blockTags.blocks(Key.key(value.substring(1)))) {
                    target.add(key.asString());
                }
            }
            return;
        }
        target.add(Key.key(value).asString());
    }

    public List<Direction> shuffledDirections(RandomSource random) {
        var directions = new ArrayList<>(this.validDirections);
        TreeDecorator.shuffle(directions, random);
        return directions;
    }

    public List<Direction> shuffledDirectionsExcept(RandomSource random, Direction excludeDirection) {
        var directions = new ArrayList<Direction>(this.validDirections.size());
        for (var direction : this.validDirections) {
            if (direction != excludeDirection) {
                directions.add(direction);
            }
        }
        TreeDecorator.shuffle(directions, random);
        return directions;
    }
}
