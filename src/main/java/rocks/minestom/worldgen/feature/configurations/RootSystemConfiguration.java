package rocks.minestom.worldgen.feature.configurations;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.FeatureConfiguration;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.feature.PlacedFeature;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProvider;
import rocks.minestom.worldgen.feature.stateproviders.BlockStateProviders;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.util.List;
import java.util.Set;

/** Port of vanilla's {@code RootSystemConfiguration}. */
public record RootSystemConfiguration(
        PlacedFeature treeFeature,
        int requiredVerticalSpaceForTree,
        int levelTestDistance,
        int maxLevelDeviation,
        int rootRadius,
        Set<Key> rootReplaceable,
        BlockStateProvider rootStateProvider,
        int rootPlacementAttempts,
        int rootColumnMaxHeight,
        int hangingRootRadius,
        int hangingRootsVerticalSpan,
        BlockStateProvider hangingRootStateProvider,
        int hangingRootPlacementAttempts,
        int allowedVerticalWaterForTree,
        AllowedTreePositionPredicate allowedTreePosition,
        FeatureLoader loader
) implements FeatureConfiguration {

    public static RootSystemConfiguration fromJson(JsonElement json, BlockTagManager blockTags) {
        var object = json.getAsJsonObject();

        var rootReplaceableValue = object.get("root_replaceable").getAsString();
        var rootReplaceable = rootReplaceableValue.startsWith("#") && blockTags != null
                ? blockTags.blocks(Key.key(rootReplaceableValue.substring(1)))
                : Set.of(Key.key(rootReplaceableValue));

        return new RootSystemConfiguration(
                PlacedFeature.fromJson(object.get("feature")),
                object.get("required_vertical_space_for_tree").getAsInt(),
                object.get("level_test_distance").getAsInt(),
                object.get("max_level_deviation").getAsInt(),
                object.get("root_radius").getAsInt(),
                rootReplaceable,
                BlockStateProviders.fromJson(object.get("root_state_provider")),
                object.get("root_placement_attempts").getAsInt(),
                object.get("root_column_max_height").getAsInt(),
                object.get("hanging_root_radius").getAsInt(),
                object.get("hanging_roots_vertical_span").getAsInt(),
                BlockStateProviders.fromJson(object.get("hanging_root_state_provider")),
                object.get("hanging_root_placement_attempts").getAsInt(),
                object.get("allowed_vertical_water_for_tree").getAsInt(),
                AllowedTreePositionPredicate.fromJson(object.get("allowed_tree_position"), blockTags),
                Features.currentLoader());
    }

    /**
     * Minimal port of the vanilla {@code BlockPredicate} tree needed by
     * {@code rooted_azalea_tree}: {@code all_of}/{@code any_of} combinators
     * over {@code matching_block_tag} leaves with an optional position offset.
     */
    public sealed interface AllowedTreePositionPredicate {
        boolean test(Block.Getter level, BlockVec position);

        static AllowedTreePositionPredicate fromJson(JsonElement json, BlockTagManager blockTags) {
            var object = json.getAsJsonObject();
            var type = object.get("type").getAsString();
            return switch (type) {
                case "minecraft:all_of" -> new AllOf(parsePredicateList(object, blockTags));
                case "minecraft:any_of" -> new AnyOf(parsePredicateList(object, blockTags));
                case "minecraft:matching_block_tag" -> new MatchingBlockTag(
                        blockTags != null ? blockTags.blocks(Key.key(object.get("tag").getAsString())) : Set.of(),
                        parseOffset(object));
                default -> throw new IllegalArgumentException("Unsupported allowed_tree_position predicate: " + type);
            };
        }

        private static List<AllowedTreePositionPredicate> parsePredicateList(com.google.gson.JsonObject object, BlockTagManager blockTags) {
            var predicates = new java.util.ArrayList<AllowedTreePositionPredicate>();
            for (var element : object.getAsJsonArray("predicates")) {
                predicates.add(fromJson(element, blockTags));
            }
            return List.copyOf(predicates);
        }

        private static int[] parseOffset(com.google.gson.JsonObject object) {
            if (!object.has("offset")) {
                return new int[]{0, 0, 0};
            }

            var offsetArray = object.getAsJsonArray("offset");
            return new int[]{offsetArray.get(0).getAsInt(), offsetArray.get(1).getAsInt(), offsetArray.get(2).getAsInt()};
        }

        record AllOf(List<AllowedTreePositionPredicate> predicates) implements AllowedTreePositionPredicate {
            @Override
            public boolean test(Block.Getter level, BlockVec position) {
                for (var predicate : this.predicates) {
                    if (!predicate.test(level, position)) {
                        return false;
                    }
                }
                return true;
            }
        }

        record AnyOf(List<AllowedTreePositionPredicate> predicates) implements AllowedTreePositionPredicate {
            @Override
            public boolean test(Block.Getter level, BlockVec position) {
                for (var predicate : this.predicates) {
                    if (predicate.test(level, position)) {
                        return true;
                    }
                }
                return false;
            }
        }

        record MatchingBlockTag(Set<Key> blocks, int[] offset) implements AllowedTreePositionPredicate {
            @Override
            public boolean test(Block.Getter level, BlockVec position) {
                var offsetPosition = position.add(this.offset[0], this.offset[1], this.offset[2]);
                return this.blocks.contains(level.getBlock(offsetPosition).key());
            }
        }
    }
}
