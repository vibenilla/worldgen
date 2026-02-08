package rocks.minestom.worldgen.biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Holds the climate parameter space and lookup structures used to map noise samples to biomes.
 * This defines the multi-dimensional climate model and the search structure that
 * finds the closest biome target for a sampled climate point.
 */
public final class Climate {
    private static final float QUANTIZATION_FACTOR = 10000.0F;
    private static final int PARAMETER_COUNT = 7;

    private Climate() {
    }

    public static long quantizeCoord(float value) {
        return (long) (value * QUANTIZATION_FACTOR);
    }

    public static TargetPoint target(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness) {
        return new TargetPoint(
                quantizeCoord(temperature),
                quantizeCoord(humidity),
                quantizeCoord(continentalness),
                quantizeCoord(erosion),
                quantizeCoord(depth),
                quantizeCoord(weirdness)
        );
    }

    public static ParameterPoint parameters(
            Parameter temperature,
            Parameter humidity,
            Parameter continentalness,
            Parameter erosion,
            Parameter depth,
            Parameter weirdness,
            float offset
    ) {
        return new ParameterPoint(temperature, humidity, continentalness, erosion, depth, weirdness, quantizeCoord(offset));
    }

    public static ParameterPoint parameters(float temperature, float humidity, float continentalness, float erosion, float depth, float weirdness, float offset) {
        return parameters(
                Parameter.point(temperature),
                Parameter.point(humidity),
                Parameter.point(continentalness),
                Parameter.point(erosion),
                Parameter.point(depth),
                Parameter.point(weirdness),
                offset
        );
    }

    public record Parameter(long min, long max) {
        public static Parameter point(float value) {
            return span(value, value);
        }

        public static Parameter span(float min, float max) {
            if (min > max) {
                throw new IllegalArgumentException("min > max: " + min + " " + max);
            }

            return new Parameter(quantizeCoord(min), quantizeCoord(max));
        }

        public static Parameter span(Parameter first, Parameter second) {
            if (first.min > second.max) {
                throw new IllegalArgumentException("min > max: " + first + " " + second);
            }

            return new Parameter(first.min, second.max);
        }

        public Parameter span(Parameter parameter) {
            if (parameter == null) {
                return this;
            }

            return new Parameter(Math.min(this.min, parameter.min), Math.max(this.max, parameter.max));
        }

        public long distance(long value) {
            var above = value - this.max;
            var below = this.min - value;

            if (above > 0L) {
                return above;
            }

            return Math.max(below, 0L);
        }

        public long distance(Parameter parameter) {
            var above = parameter.min - this.max;
            var below = this.min - parameter.max;

            if (above > 0L) {
                return above;
            }

            return Math.max(below, 0L);
        }
    }

    public record TargetPoint(
            long temperature,
            long humidity,
            long continentalness,
            long erosion,
            long depth,
            long weirdness
    ) {
        private long[] toParameterArray() {
            return new long[]{this.temperature, this.humidity, this.continentalness, this.erosion, this.depth, this.weirdness, 0L};
        }
    }

    public record ParameterPoint(
            Parameter temperature,
            Parameter humidity,
            Parameter continentalness,
            Parameter erosion,
            Parameter depth,
            Parameter weirdness,
            long offset
    ) {
        public long fitness(TargetPoint target) {
            var sum = 0L;
            sum += square(this.temperature.distance(target.temperature));
            sum += square(this.humidity.distance(target.humidity));
            sum += square(this.continentalness.distance(target.continentalness));
            sum += square(this.erosion.distance(target.erosion));
            sum += square(this.depth.distance(target.depth));
            sum += square(this.weirdness.distance(target.weirdness));
            sum += square(this.offset);
            return sum;
        }

        private List<Parameter> parameterSpace() {
            return List.of(
                    this.temperature,
                    this.humidity,
                    this.continentalness,
                    this.erosion,
                    this.depth,
                    this.weirdness,
                    new Parameter(this.offset, this.offset)
            );
        }

        private static long square(long value) {
            return value * value;
        }
    }

    private interface DistanceMetric<T> {
        long distance(RTree.Node<T> node, long[] parameters);
    }

    public static final class ParameterList<T> {
        private final List<Pair<ParameterPoint, T>> values;
        private final RTree<T> index;

        public ParameterList(List<Pair<ParameterPoint, T>> values) {
            this.values = values;
            this.index = RTree.create(values);
        }

        public List<Pair<ParameterPoint, T>> values() {
            return this.values;
        }

        public T findValue(TargetPoint targetPoint) {
            return this.findValueIndex(targetPoint);
        }

        private T findValueIndex(TargetPoint targetPoint) {
            return this.findValueIndex(targetPoint, RTree.Node::distance);
        }

        private T findValueIndex(TargetPoint targetPoint, DistanceMetric<T> metric) {
            return this.index.search(targetPoint, metric);
        }
    }

    private static final class RTree<T> {
        private static final int CHILDREN_PER_NODE = 6;

        private final Node<T> root;
        private final ThreadLocal<Leaf<T>> lastResult;

        private RTree(Node<T> root) {
            this.root = root;
            this.lastResult = new ThreadLocal<>();
        }

        private static <T> RTree<T> create(List<Pair<ParameterPoint, T>> values) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Need at least one value to build the search tree.");
            }

            var parameterCount = values.getFirst().first().parameterSpace().size();

            if (parameterCount != PARAMETER_COUNT) {
                throw new IllegalStateException("Expecting parameter space to be " + PARAMETER_COUNT + ", got " + parameterCount);
            }

            var leaves = new ArrayList<Node<T>>(values.size());

            for (var entry : values) {
                leaves.add(new Leaf<>(entry.first(), entry.second()));
            }

            return new RTree<>(build(parameterCount, leaves));
        }

        private static <T> Node<T> build(int parameterCount, List<? extends Node<T>> nodes) {
            if (nodes.isEmpty()) {
                throw new IllegalStateException("Need at least one child to build a node");
            }

            if (nodes.size() == 1) {
                return nodes.getFirst();
            }

            if (nodes.size() <= CHILDREN_PER_NODE) {
                nodes.sort(Comparator.comparingLong(node -> {
                    var sum = 0L;

                    for (var index = 0; index < parameterCount; index++) {
                        var parameter = node.parameterSpace[index];
                        sum += Math.abs((parameter.min + parameter.max) / 2L);
                    }

                    return sum;
                }));

                return new SubTree<>(nodes);
            }

            var bestCost = Long.MAX_VALUE;
            var bestAxis = -1;
            List<SubTree<T>> bestBuckets = null;

            for (var axis = 0; axis < parameterCount; axis++) {
                sort(nodes, parameterCount, axis, false);
                var buckets = bucketize(nodes);
                var cost = 0L;

                for (var bucket : buckets) {
                    cost += cost(bucket.parameterSpace);
                }

                if (cost < bestCost) {
                    bestCost = cost;
                    bestAxis = axis;
                    bestBuckets = buckets;
                }
            }

            sort(bestBuckets, parameterCount, bestAxis, true);
            var children = new ArrayList<Node<T>>(bestBuckets.size());
            for (var bucket : bestBuckets) {
                children.add(build(parameterCount, Arrays.asList(bucket.children)));
            }
            return new SubTree<>(children);
        }

        private static <T> void sort(List<? extends Node<T>> nodes, int parameterCount, int axis, boolean absolute) {
            var comparator = comparator(axis, absolute);

            for (var index = 1; index < parameterCount; index++) {
                comparator = comparator.thenComparing(comparator((axis + index) % parameterCount, absolute));
            }

            @SuppressWarnings({"rawtypes"})
            var raw = (Comparator) comparator;
            nodes.sort(raw);
        }

        private static <T> Comparator<Node<T>> comparator(int axis, boolean absolute) {
            return Comparator.comparingLong(node -> {
                var parameter = node.parameterSpace[axis];
                var midpoint = (parameter.min + parameter.max) / 2L;
                return absolute ? Math.abs(midpoint) : midpoint;
            });
        }

        private static <T> List<SubTree<T>> bucketize(List<? extends Node<T>> nodes) {
            var buckets = new ArrayList<SubTree<T>>();
            var bucket = new ArrayList<Node<T>>();
            var bucketSize = (int) Math.pow((double) CHILDREN_PER_NODE, Math.floor(Math.log((double) nodes.size() - 0.01D) / Math.log((double) CHILDREN_PER_NODE)));

            for (var node : nodes) {
                bucket.add(node);
                if (bucket.size() >= bucketSize) {
                    buckets.add(new SubTree<>(bucket));
                    bucket = new ArrayList<>();
                }
            }

            if (!bucket.isEmpty()) {
                buckets.add(new SubTree<>(bucket));
            }

            return buckets;
        }

        private static long cost(Parameter[] parameters) {
            var cost = 0L;
            for (var parameter : parameters) {
                cost += Math.abs(parameter.max - parameter.min);
            }
            return cost;
        }

        private static <T> List<Parameter> buildParameterSpace(List<? extends Node<T>> nodes) {
            if (nodes.isEmpty()) {
                throw new IllegalArgumentException("SubTree needs at least one child");
            }

            var parameters = new ArrayList<Parameter>(PARAMETER_COUNT);
            for (var index = 0; index < PARAMETER_COUNT; index++) {
                parameters.add(null);
            }

            for (var node : nodes) {
                for (var index = 0; index < PARAMETER_COUNT; index++) {
                    parameters.set(index, node.parameterSpace[index].span(parameters.get(index)));
                }
            }

            return parameters;
        }

        private T search(TargetPoint targetPoint, DistanceMetric<T> metric) {
            var target = targetPoint.toParameterArray();
            var leaf = this.root.search(target, this.lastResult.get(), metric);
            this.lastResult.set(leaf);
            return leaf.value;
        }

        private abstract static class Node<T> {
            protected final Parameter[] parameterSpace;

            protected Node(List<Parameter> parameterSpace) {
                this.parameterSpace = parameterSpace.toArray(new Parameter[0]);
            }

            protected abstract Leaf<T> search(long[] target, Leaf<T> previousBest, DistanceMetric<T> metric);

            protected long distance(long[] target) {
                var sum = 0L;
                for (var index = 0; index < PARAMETER_COUNT; index++) {
                    var distance = this.parameterSpace[index].distance(target[index]);
                    sum += distance * distance;
                }
                return sum;
            }
        }

        private static final class Leaf<T> extends Node<T> {
            private final T value;

            private Leaf(ParameterPoint parameters, T value) {
                super(parameters.parameterSpace());
                this.value = value;
            }

            @Override
            protected Leaf<T> search(long[] target, Leaf<T> previousBest, DistanceMetric<T> metric) {
                return this;
            }
        }

        private static final class SubTree<T> extends Node<T> {
            private final Node<T>[] children;

            private SubTree(List<? extends Node<T>> children) {
                this(buildParameterSpace(children), children);
            }

            private SubTree(List<Parameter> parameterSpace, List<? extends Node<T>> children) {
                super(parameterSpace);
                @SuppressWarnings("unchecked")
                var array = (Node<T>[]) children.toArray(new Node[0]);
                this.children = array;
            }

            @Override
            protected Leaf<T> search(long[] target, Leaf<T> previousBest, DistanceMetric<T> metric) {
                var bestDistance = previousBest == null ? Long.MAX_VALUE : metric.distance(previousBest, target);
                var bestLeaf = previousBest;

                for (var child : this.children) {
                    var childDistance = metric.distance(child, target);
                    if (childDistance >= bestDistance) {
                        continue;
                    }

                    var leaf = child.search(target, bestLeaf, metric);
                    var leafDistance = child == leaf ? childDistance : metric.distance(leaf, target);
                    if (leafDistance < bestDistance) {
                        bestDistance = leafDistance;
                        bestLeaf = leaf;
                    }
                }

                return bestLeaf;
            }
        }
    }
}
