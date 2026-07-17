package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.feature.trunkplacers.TrunkPlacer;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public final class TreeFeature implements Feature<TreeConfiguration> {
    /** Debug trace of tree placement decisions, for parity investigations. */
    public static final boolean TRACE = !System.getProperty("worldgen.treeTrace", "").isEmpty();

    /** Heavy per-tree dumps (rng state, world snapshot) for the replay harnesses. */
    public static final boolean TRACE_FULL = "full".equals(System.getProperty("worldgen.treeTrace"));

    /** Optional x1,z1,x2,z2 box limiting the heavy dumps. */
    private static final int[] TRACE_BOX = parseTraceBox();

    private static int[] parseTraceBox() {
        var value = System.getProperty("worldgen.treeTraceBox", "");
        if (value.isEmpty()) {
            return null;
        }
        var parts = value.split(",");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
    }

    private static boolean inTraceBox(BlockVec origin) {
        return TRACE_BOX == null
                || origin.blockX() >= TRACE_BOX[0] && origin.blockX() <= TRACE_BOX[2]
                && origin.blockZ() >= TRACE_BOX[1] && origin.blockZ() <= TRACE_BOX[3];
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<TreeConfiguration, T> context) {
        var level = context.accessor();
        var random = context.random();
        var config = context.config();

        // Vanilla hashing so set iteration (decorator ordering, distance BFS)
        // matches vanilla exactly
        var logs = new HashSet<VanillaPos>();
        var leaves = new HashSet<VanillaPos>();
        var decorations = new HashSet<VanillaPos>();

        BiConsumer<BlockVec, Block> logSetter = (position, block) -> {
            logs.add(VanillaPos.of(position));
            level.setBlock(position, block);
        };

        var leafSetter = new FoliagePlacer.FoliageSetter() {
            @Override
            public void set(BlockVec position, Block block) {
                leaves.add(VanillaPos.of(position));
                level.setBlock(position, block);
            }

            @Override
            public boolean isSet(BlockVec position) {
                return leaves.contains(VanillaPos.of(position));
            }
        };

        BiConsumer<BlockVec, Block> decorationSetter = (position, block) -> {
            decorations.add(VanillaPos.of(position));
            level.setBlock(position, block);
        };

        if (TRACE_FULL && inTraceBox(context.origin())) {
            dumpDecoratorInputs(level, context.origin(), "pre", random, Set.of(), Set.of());
        }

        var placed = this.doPlace(context, logSetter, leafSetter);
        if (TRACE) {
            System.out.println("TRACE tree " + context.origin() + " placed=" + placed
                    + " logs=" + logs.size() + " leaves=" + leaves.size());
        }
        if (!placed || logs.isEmpty() && leaves.isEmpty()) {
            return false;
        }

        if (TRACE_FULL && inTraceBox(context.origin())) {
            dumpDecoratorInputs(level, context.origin(), "dec", random, logs, leaves);
        }

        if (!config.decorators().isEmpty()) {
            var decoratorContext = new TreeDecorator.Context(
                    level,
                    decorationSetter,
                    random,
                    toBlockVecs(logs),
                    toBlockVecs(leaves),
                    List.of());
            for (var decorator : config.decorators()) {
                decorator.place(decoratorContext);
            }
        }

        return updateLeaves(level, logs, leaves, decorations);
    }

    private <T extends Block.Getter & Block.Setter> boolean doPlace(
            FeaturePlaceContext<TreeConfiguration, T> context,
            BiConsumer<BlockVec, Block> logSetter,
            FoliagePlacer.FoliageSetter leafSetter
    ) {
        var level = context.accessor();
        var random = context.random();
        var pos = context.origin();
        var config = context.config();

        var treeHeight = config.trunkPlacer().getTreeHeight(random);
        var foliageHeight = config.foliagePlacer().foliageHeight(random, treeHeight, config);
        var trunkHeight = treeHeight - foliageHeight;
        var foliageRadius = config.foliagePlacer().foliageRadius(random, trunkHeight);

        if (pos.blockY() < context.minY() + 1 || pos.blockY() + treeHeight + 1 > context.maxY() + 1) {
            return false;
        }

        var maxFreeTreeHeight = this.getMaxFreeTreeHeight(level, treeHeight, pos, config);
        if (maxFreeTreeHeight < treeHeight) {
            var minClipped = config.minimumSize().minClippedHeight();
            if (minClipped.isEmpty() || maxFreeTreeHeight < minClipped.getAsInt()) {
                return false;
            }
        }

        var foliageAttachments = config.trunkPlacer().placeTrunk(level, logSetter, random, maxFreeTreeHeight, pos, config);
        for (var attachment : foliageAttachments) {
            config.foliagePlacer().createFoliage(level, leafSetter, random, config, maxFreeTreeHeight, attachment, foliageHeight, foliageRadius);
        }

        return true;
    }

    private int getMaxFreeTreeHeight(Block.Getter getter, int treeHeight, BlockVec pos, TreeConfiguration config) {
        for (var height = 0; height <= treeHeight + 1; height++) {
            var size = config.minimumSize().getSizeAtHeight(treeHeight, height);

            for (var x = -size; x <= size; x++) {
                for (var z = -size; z <= size; z++) {
                    var checkPos = pos.add(x, height, z);
                    if (!config.trunkPlacer().isFree(getter, checkPos)
                            || !config.ignoreVines() && getter.getBlock(checkPos).compare(Block.VINE)) {
                        return height - 2;
                    }
                }
            }
        }

        return treeHeight;
    }

    /**
     * Debug dump of everything needed to replay the decorators offline: the
     * random state, the log/leaf sets in vanilla iteration order, and the
     * nearby world state.
     */
    private static void dumpDecoratorInputs(
            Block.Getter level,
            BlockVec origin,
            String stage,
            rocks.minestom.worldgen.random.RandomSource random,
            Set<VanillaPos> logs,
            Set<VanillaPos> leaves
    ) {
        try {
            var inner = random;
            var sourceField = inner.getClass().getDeclaredField("randomSource");
            sourceField.setAccessible(true);
            var source = sourceField.get(inner);
            var generatorField = source.getClass().getDeclaredField("randomNumberGenerator");
            generatorField.setAccessible(true);
            var generator = generatorField.get(source);
            var loField = generator.getClass().getDeclaredField("seedLo");
            var hiField = generator.getClass().getDeclaredField("seedHi");
            loField.setAccessible(true);
            hiField.setAccessible(true);
            System.out.println("TRACE rng " + stage + ":" + key(origin) + " " + loField.getLong(generator) + " " + hiField.getLong(generator));
        } catch (ReflectiveOperationException exception) {
            System.out.println("TRACE rng " + stage + ":" + key(origin) + " unavailable " + exception);
        }

        var logDump = new StringBuilder();
        for (var log : logs) {
            logDump.append(log.x()).append(',').append(log.y()).append(',').append(log.z()).append(';');
        }
        System.out.println("TRACE logset " + stage + ":" + key(origin) + " " + logDump);
        var leafDump = new StringBuilder();
        for (var leaf : leaves) {
            leafDump.append(leaf.x()).append(',').append(leaf.y()).append(',').append(leaf.z()).append(';');
        }
        System.out.println("TRACE leafset " + stage + ":" + key(origin) + " " + leafDump);

        var reach = Integer.getInteger("worldgen.treeTraceReach", 8);
        for (var y = origin.blockY() - 14; y <= origin.blockY() + 22; y++) {
            for (var x = origin.blockX() - reach; x <= origin.blockX() + reach; x++) {
                for (var z = origin.blockZ() - reach; z <= origin.blockZ() + reach; z++) {
                    var block = level.getBlock(x, y, z);
                    if (!block.isAir()) {
                        System.out.println("TRACE world " + stage + ":" + key(origin) + " " + x + " " + y + " " + z + " " + serialize(block));
                    }
                }
            }
        }
    }

    private static String key(BlockVec origin) {
        return origin.blockX() + ":" + origin.blockY() + ":" + origin.blockZ();
    }

    /** Debug: print the xoroshiro state of the shared worldgen random. */
    public static void dumpRngState(String tag, rocks.minestom.worldgen.random.RandomSource random) {
        try {
            var sourceField = random.getClass().getDeclaredField("randomSource");
            sourceField.setAccessible(true);
            var source = sourceField.get(random);
            var generatorField = source.getClass().getDeclaredField("randomNumberGenerator");
            generatorField.setAccessible(true);
            var generator = generatorField.get(source);
            var loField = generator.getClass().getDeclaredField("seedLo");
            var hiField = generator.getClass().getDeclaredField("seedHi");
            loField.setAccessible(true);
            hiField.setAccessible(true);
            System.out.println("RNGSTATE " + tag + " " + loField.getLong(generator) + " " + hiField.getLong(generator));
        } catch (ReflectiveOperationException exception) {
            System.out.println("RNGSTATE " + tag + " unavailable " + exception);
        }
    }

    private static String serialize(Block block) {
        var properties = block.properties();
        if (properties.isEmpty()) {
            return block.name();
        }
        var builder = new StringBuilder(block.name()).append('[');
        var first = true;
        for (var entry : new java.util.TreeMap<>(properties).entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }

    private static List<BlockVec> toBlockVecs(Set<VanillaPos> positions) {
        var result = new ArrayList<BlockVec>(positions.size());
        for (var position : positions) {
            result.add(position.toBlockVec());
        }
        return result;
    }

    /**
     * Vanilla's post-placement leaf distance update: a BFS from the logs sets
     * the {@code distance} property (1-6) of every reachable block carrying it,
     * exactly like {@code TreeFeature.updateLeaves}. Decorations are marked as
     * visited first so they are never distance-updated.
     */
    private static <T extends Block.Getter & Block.Setter> boolean updateLeaves(
            T level,
            Set<VanillaPos> logs,
            Set<VanillaPos> leaves,
            Set<VanillaPos> decorations
    ) {
        // Vanilla BoundingBox.encapsulatingPositions over roots+logs+leaves+decorations
        var minX = Integer.MAX_VALUE;
        var minY = Integer.MAX_VALUE;
        var minZ = Integer.MAX_VALUE;
        var maxX = Integer.MIN_VALUE;
        var maxY = Integer.MIN_VALUE;
        var maxZ = Integer.MIN_VALUE;
        var any = false;
        for (var set : List.of(logs, leaves, decorations)) {
            for (var pos : set) {
                any = true;
                minX = Math.min(minX, pos.x());
                minY = Math.min(minY, pos.y());
                minZ = Math.min(minZ, pos.z());
                maxX = Math.max(maxX, pos.x());
                maxY = Math.max(maxY, pos.y());
                maxZ = Math.max(maxZ, pos.z());
            }
        }
        if (!any) {
            return false;
        }

        var sizeX = maxX - minX + 1;
        var sizeY = maxY - minY + 1;
        var sizeZ = maxZ - minZ + 1;
        var shape = new BitSet(sizeX * sizeY * sizeZ);
        var boundsMinX = minX;
        var boundsMinY = minY;
        var boundsMinZ = minZ;
        var boundsMaxX = maxX;
        var boundsMaxY = maxY;
        var boundsMaxZ = maxZ;

        for (var pos : decorations) {
            if (inside(pos, boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ)) {
                shape.set(shapeIndex(pos, boundsMinX, boundsMinY, boundsMinZ, sizeY, sizeZ));
            }
        }

        var toCheck = new ArrayList<HashSet<VanillaPos>>(7);
        for (var index = 0; index < 7; index++) {
            toCheck.add(new HashSet<>());
        }
        toCheck.get(0).addAll(logs);

        var smallestDistance = 0;
        while (true) {
            while (smallestDistance >= 7 || !toCheck.get(smallestDistance).isEmpty()) {
                if (smallestDistance >= 7) {
                    return true;
                }

                var iterator = toCheck.get(smallestDistance).iterator();
                var current = iterator.next();
                iterator.remove();
                if (!inside(current, boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ)) {
                    continue;
                }

                if (smallestDistance != 0) {
                    var position = current.toBlockVec();
                    var state = level.getBlock(position);
                    if (state.getProperty("distance") != null) {
                        level.setBlock(position, state.withProperty("distance", Integer.toString(smallestDistance)));
                    }
                }

                shape.set(shapeIndex(current, boundsMinX, boundsMinY, boundsMinZ, sizeY, sizeZ));

                for (var direction : Direction.values()) {
                    var neighbor = new VanillaPos(
                            current.x() + direction.stepX(),
                            current.y() + direction.stepY(),
                            current.z() + direction.stepZ());
                    if (!inside(neighbor, boundsMinX, boundsMinY, boundsMinZ, boundsMaxX, boundsMaxY, boundsMaxZ)) {
                        continue;
                    }
                    if (shape.get(shapeIndex(neighbor, boundsMinX, boundsMinY, boundsMinZ, sizeY, sizeZ))) {
                        continue;
                    }

                    var distance = distanceAt(level.getBlock(neighbor.toBlockVec()));
                    if (distance < 0) {
                        continue;
                    }

                    var newDistance = Math.min(distance, smallestDistance + 1);
                    if (newDistance < 7) {
                        toCheck.get(newDistance).add(neighbor);
                        smallestDistance = Math.min(smallestDistance, newDistance);
                    }
                }
            }

            smallestDistance++;
        }
    }

    /**
     * Vanilla {@code LeavesBlock.getOptionalDistanceAt}: logs count as distance
     * 0, blocks carrying the {@code distance} property report it, everything
     * else has none (returned as -1 here).
     */
    private static int distanceAt(Block block) {
        if (TrunkPlacer.isLog(block)) {
            return 0;
        }
        var property = block.getProperty("distance");
        return property != null ? Integer.parseInt(property) : -1;
    }

    private static boolean inside(VanillaPos pos, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return pos.x() >= minX && pos.x() <= maxX
                && pos.y() >= minY && pos.y() <= maxY
                && pos.z() >= minZ && pos.z() <= maxZ;
    }

    private static int shapeIndex(VanillaPos pos, int minX, int minY, int minZ, int sizeY, int sizeZ) {
        return ((pos.x() - minX) * sizeY + (pos.y() - minY)) * sizeZ + (pos.z() - minZ);
    }
}
