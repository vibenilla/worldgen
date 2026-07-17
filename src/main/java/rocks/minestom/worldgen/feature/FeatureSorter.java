package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds the deterministic global ordering of placed features per decoration step.
 * Vanilla assigns each feature an index within its step by topologically sorting
 * the per-biome feature lists; that index seeds the feature's random, so this
 * ordering must match vanilla exactly for placement parity.
 */
public final class FeatureSorter {
    private FeatureSorter() {
    }

    public record StepFeatureData(List<Key> features, Map<Key, Integer> indexMapping) {
        static StepFeatureData of(List<Key> features) {
            Map<Key, Integer> indexMapping = new HashMap<>();
            for (var index = 0; index < features.size(); index++) {
                indexMapping.put(features.get(index), index);
            }
            return new StepFeatureData(features, indexMapping);
        }
    }

    private record FeatureData(int featureIndex, int step, Key feature) {
    }

    public static List<StepFeatureData> buildFeaturesPerStep(List<Key> biomes, Function<Key, List<List<Key>>> featureGetter) {
        var featureIndexes = new HashMap<Key, Integer>();
        var comparator = Comparator.comparingInt(FeatureData::step).thenComparingInt(FeatureData::featureIndex);
        var edges = new TreeMap<FeatureData, Set<FeatureData>>(comparator);
        var maxStep = 0;

        for (var biome : biomes) {
            var featureList = new ArrayList<FeatureData>();
            var featuresPerStep = featureGetter.apply(biome);
            maxStep = Math.max(maxStep, featuresPerStep.size());

            for (var step = 0; step < featuresPerStep.size(); step++) {
                for (var feature : featuresPerStep.get(step)) {
                    var featureIndex = featureIndexes.computeIfAbsent(feature, key -> featureIndexes.size());
                    featureList.add(new FeatureData(featureIndex, step, feature));
                }
            }

            for (var index = 0; index < featureList.size(); index++) {
                var successors = edges.computeIfAbsent(featureList.get(index), key -> new TreeSet<>(comparator));
                if (index < featureList.size() - 1) {
                    successors.add(featureList.get(index + 1));
                }
            }
        }

        var discovered = new TreeSet<FeatureData>(comparator);
        var visiting = new TreeSet<FeatureData>(comparator);
        var sorted = new ArrayList<FeatureData>();

        for (var feature : edges.keySet()) {
            if (!discovered.contains(feature) && depthFirstSearch(edges, discovered, visiting, sorted::add, feature)) {
                throw new IllegalStateException("Feature order cycle found");
            }
        }

        var reversed = sorted.reversed();
        var result = new ArrayList<StepFeatureData>(maxStep);
        for (var step = 0; step < maxStep; step++) {
            var stepIndex = step;
            var featuresInStep = reversed.stream()
                    .filter(data -> data.step() == stepIndex)
                    .map(FeatureData::feature)
                    .toList();
            result.add(StepFeatureData.of(featuresInStep));
        }
        return result;
    }

    private static boolean depthFirstSearch(Map<FeatureData, Set<FeatureData>> graph, Set<FeatureData> discovered,
            Set<FeatureData> visiting, Consumer<FeatureData> onFinished, FeatureData start) {
        if (discovered.contains(start)) {
            return false;
        }

        if (visiting.contains(start)) {
            return true;
        }

        visiting.add(start);
        for (var neighbor : graph.getOrDefault(start, Set.of())) {
            if (depthFirstSearch(graph, discovered, visiting, onFinished, neighbor)) {
                return true;
            }
        }

        visiting.remove(start);
        discovered.add(start);
        onFinished.accept(start);
        return false;
    }
}
