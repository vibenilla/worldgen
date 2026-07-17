package rocks.minestom.worldgen.structure.pool;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Vanilla {@code PoolAliasBinding}: resolves pool alias keys to concrete
 * pools, drawing from a shared positional random (trial chamber spawner
 * variants). Draw order matches vanilla: bindings resolve in list order;
 * weighted picks draw one {@code nextInt(totalWeight)}.
 */
public sealed interface PoolAliasBinding {
    void forEachResolved(RandomSource random, BiConsumer<Key, Key> consumer);

    record Direct(Key alias, Key target) implements PoolAliasBinding {
        @Override
        public void forEachResolved(RandomSource random, BiConsumer<Key, Key> consumer) {
            consumer.accept(this.alias, this.target);
        }
    }

    record Random(Key alias, List<Weighted<Key>> targets, int totalWeight) implements PoolAliasBinding {
        public Random(Key alias, List<Weighted<Key>> targets) {
            this(alias, targets, sumWeights(targets));
        }

        @Override
        public void forEachResolved(RandomSource random, BiConsumer<Key, Key> consumer) {
            consumer.accept(this.alias, pick(this.targets, this.totalWeight, random));
        }
    }

    record RandomGroup(List<Weighted<List<PoolAliasBinding>>> groups, int totalWeight) implements PoolAliasBinding {
        public RandomGroup(List<Weighted<List<PoolAliasBinding>>> groups) {
            this(groups, sumWeights(groups));
        }

        @Override
        public void forEachResolved(RandomSource random, BiConsumer<Key, Key> consumer) {
            for (var binding : pick(this.groups, this.totalWeight, random)) {
                binding.forEachResolved(random, consumer);
            }
        }
    }

    record Weighted<T>(T value, int weight) {
    }

    private static int sumWeights(List<? extends Weighted<?>> entries) {
        var total = 0;
        for (var entry : entries) {
            total += entry.weight();
        }
        return total;
    }

    /** Vanilla {@code WeightedList.getRandomOrThrow}: one draw, cumulative pick. */
    private static <T> T pick(List<Weighted<T>> entries, int totalWeight, RandomSource random) {
        var selected = random.nextInt(totalWeight);
        for (var entry : entries) {
            selected -= entry.weight();
            if (selected < 0) {
                return entry.value();
            }
        }
        return entries.getLast().value();
    }

    static List<PoolAliasBinding> parseList(JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return List.of();
        }

        var bindings = new ArrayList<PoolAliasBinding>();
        for (var entry : json.getAsJsonArray()) {
            var binding = parse(entry);
            if (binding != null) {
                bindings.add(binding);
            }
        }
        return List.copyOf(bindings);
    }

    private static PoolAliasBinding parse(JsonElement json) {
        if (!json.isJsonObject()) {
            return null;
        }

        var obj = json.getAsJsonObject();
        var type = obj.get("type").getAsString();
        return switch (type) {
            case "minecraft:direct" -> new Direct(
                    Key.key(obj.get("alias").getAsString()),
                    Key.key(obj.get("target").getAsString()));
            case "minecraft:random" -> {
                var targets = new ArrayList<Weighted<Key>>();
                for (var target : obj.getAsJsonArray("targets")) {
                    var targetObj = target.getAsJsonObject();
                    targets.add(new Weighted<>(
                            Key.key(targetObj.get("data").getAsString()),
                            targetObj.get("weight").getAsInt()));
                }
                yield new Random(Key.key(obj.get("alias").getAsString()), List.copyOf(targets));
            }
            case "minecraft:random_group" -> {
                var groups = new ArrayList<Weighted<List<PoolAliasBinding>>>();
                for (var group : obj.getAsJsonArray("groups")) {
                    var groupObj = group.getAsJsonObject();
                    groups.add(new Weighted<>(
                            parseList(groupObj.get("data")),
                            groupObj.get("weight").getAsInt()));
                }
                yield new RandomGroup(List.copyOf(groups));
            }
            default -> null;
        };
    }
}
