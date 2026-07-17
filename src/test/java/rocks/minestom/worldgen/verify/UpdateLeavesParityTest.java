package rocks.minestom.worldgen.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.TreeFeature;
import rocks.minestom.worldgen.feature.VanillaPos;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs vanilla's private {@code TreeFeature.updateLeaves} (via reflection with
 * a proxy level) against this library's port on randomized synthetic trees,
 * asserting the resulting leaf {@code distance} properties match block for
 * block, including overlap scenarios with pre-existing trees.
 */
final class UpdateLeavesParityTest {

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        // Bootstrap does not load datapack tags; bind the one tag vanilla's
        // updateLeaves depends on (logs anchor the distance BFS at 0)
        try {
            var registriesClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            var registry = registriesClass.getField("BLOCK").get(null);
            var listElements = registry.getClass().getMethod("listElements");
            var logs = new java.util.ArrayList<>();
            ((java.util.stream.Stream<?>) listElements.invoke(registry)).forEach(holder -> {
                try {
                    var key = holder.getClass().getMethod("key").invoke(holder);
                    var location = key.getClass().getMethod("identifier").invoke(key);
                    var path = (String) location.getClass().getMethod("getPath").invoke(location);
                    if (path.endsWith("_log") || path.endsWith("_wood")
                            || path.contains("bamboo_block")
                            || path.equals("crimson_stem") || path.equals("stripped_crimson_stem")
                            || path.equals("warped_stem") || path.equals("stripped_warped_stem")) {
                        logs.add(holder);
                    }
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            // state.is(tag) consults the per-holder tag set; bind it directly
            var holderBindTags = Class.forName("net.minecraft.core.Holder$Reference")
                    .getDeclaredMethod("bindTags", java.util.Collection.class);
            holderBindTags.setAccessible(true);
            for (var holder : logs) {
                holderBindTags.invoke(holder,
                        java.util.List.of(net.minecraft.tags.BlockTags.PREVENTS_NEARBY_LEAF_DECAY));
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void randomizedScenarios() throws Exception {
        var seedSource = new Random(20260716L);
        for (var scenario = 0; scenario < 200; scenario++) {
            runScenario(seedSource.nextLong());
        }
    }

    private static void runScenario(long seed) throws Exception {
        var random = new Random(seed);

        // Pre-existing world: an earlier tree with settled distances
        var vanillaWorld = new HashMap<BlockPos, BlockState>();
        var ourWorld = new HashMap<BlockVec, Block>();

        var oldTrunkX = random.nextInt(7) - 3;
        var oldTrunkZ = random.nextInt(7) - 3;
        if (oldTrunkX != 0 || oldTrunkZ != 0) {
            var oldHeight = 4 + random.nextInt(3);
            for (var y = 0; y < oldHeight; y++) {
                put(vanillaWorld, ourWorld, oldTrunkX, y, oldTrunkZ, 0, true);
            }
            for (var dx = -2; dx <= 2; dx++) {
                for (var dz = -2; dz <= 2; dz++) {
                    for (var dy = oldHeight - 3; dy <= oldHeight + 1; dy++) {
                        if (random.nextInt(4) == 0) {
                            continue;
                        }
                        var distance = Math.min(7, Math.abs(dx) + Math.abs(dz) + Math.max(0, dy - oldHeight) + 1);
                        put(vanillaWorld, ourWorld, oldTrunkX + dx, dy, oldTrunkZ + dz, distance, false);
                    }
                }
            }
        }

        // New tree: trunk at origin, blob-ish canopy with fresh distance=7 leaves
        var logs = new HashSet<BlockPos>();
        var ourLogs = new HashSet<VanillaPos>();
        var leaves = new HashSet<BlockPos>();
        var ourLeaves = new HashSet<VanillaPos>();
        var decorations = new HashSet<BlockPos>();
        var ourDecorations = new HashSet<VanillaPos>();

        var height = 4 + random.nextInt(3);
        for (var y = 0; y < height; y++) {
            put(vanillaWorld, ourWorld, 0, y, 0, 0, true);
            logs.add(new BlockPos(0, y, 0));
            ourLogs.add(new VanillaPos(0, y, 0));
        }
        for (var dx = -2; dx <= 2; dx++) {
            for (var dz = -2; dz <= 2; dz++) {
                for (var dy = height - 3; dy <= height + 1; dy++) {
                    if (random.nextInt(3) == 0) {
                        continue;
                    }
                    var x = dx;
                    var z = dz;
                    var pos = new BlockPos(x, dy, z);
                    if (logs.contains(pos)) {
                        continue;
                    }
                    put(vanillaWorld, ourWorld, x, dy, z, 7, false);
                    leaves.add(pos);
                    ourLeaves.add(new VanillaPos(x, dy, z));
                }
            }
        }

        // Some decorations on the ground (litter stand-ins, marked visited)
        for (var attempt = 0; attempt < 4; attempt++) {
            if (random.nextBoolean()) {
                var pos = new BlockPos(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
                if (!logs.contains(pos) && !leaves.contains(pos)) {
                    decorations.add(pos);
                    ourDecorations.add(new VanillaPos(pos.getX(), pos.getY(), pos.getZ()));
                }
            }
        }

        // Vanilla side
        var boundsClass = Class.forName("net.minecraft.world.level.levelgen.structure.BoundingBox");
        var encapsulate = boundsClass.getMethod("encapsulatingPositions", Iterable.class);
        var all = new HashSet<BlockPos>();
        all.addAll(logs);
        all.addAll(leaves);
        all.addAll(decorations);
        var boundsOptional = (java.util.Optional<?>) encapsulate.invoke(null, all);
        var bounds = boundsOptional.orElseThrow();

        var vanillaTrace = new java.util.ArrayList<String>();
        var level = (LevelAccessor) Proxy.newProxyInstance(
                UpdateLeavesParityTest.class.getClassLoader(),
                new Class<?>[]{LevelAccessor.class},
                new VanillaLevelHandler(vanillaWorld, vanillaTrace));

        var updateLeaves = net.minecraft.world.level.levelgen.feature.TreeFeature.class
                .getDeclaredMethod("updateLeaves", LevelAccessor.class, boundsClass, Set.class, Set.class, Set.class);
        updateLeaves.setAccessible(true);
        updateLeaves.invoke(null, level, bounds, logs, decorations, Set.<BlockPos>of());

        // Our side
        var accessor = new MapAccessor(ourWorld);
        invokeOurs(accessor, ourLogs, ourLeaves, ourDecorations);

        // Compare every position with a distance property
        var mismatches = new StringBuilder();
        for (var entry : vanillaWorld.entrySet()) {
            var pos = entry.getKey();
            var state = entry.getValue();
            if (!state.hasProperty(BlockStateProperties.DISTANCE)) {
                continue;
            }
            var expected = state.getValue(BlockStateProperties.DISTANCE);
            var ourBlock = ourWorld.get(new BlockVec(pos.getX(), pos.getY(), pos.getZ()));
            var actual = Integer.parseInt(ourBlock.getProperty("distance"));
            if (expected != actual) {
                mismatches.append("distance at %s vanilla=%d ours=%d%n".formatted(pos, expected, actual));
            }
        }
        if (!mismatches.isEmpty()) {
            System.out.println("=== seed " + seed);
            System.out.println("logs: " + logs);
            System.out.println("decorations: " + decorations);
            System.out.println(mismatches);
            var max = Math.max(vanillaTrace.size(), accessor.trace.size());
            for (var index = 0; index < max; index++) {
                var vanillaOp = index < vanillaTrace.size() ? vanillaTrace.get(index) : "<none>";
                var ourOp = index < accessor.trace.size() ? accessor.trace.get(index) : "<none>";
                var marker = vanillaOp.equals(ourOp) ? "  " : "!!";
                System.out.printf("%s %-20s | %s%n", marker, vanillaOp, ourOp);
                if (!vanillaOp.equals(ourOp) && index + 5 < max) {
                    max = Math.min(max, index + 5);
                }
            }
        }
        assertEquals("", mismatches.toString(), "seed=" + seed);
    }

    private static void invokeOurs(MapAccessor accessor, Set<VanillaPos> logs, Set<VanillaPos> leaves, Set<VanillaPos> decorations) throws Exception {
        for (var method : TreeFeature.class.getDeclaredMethods()) {
            if (method.getName().equals("updateLeaves")) {
                method.setAccessible(true);
                method.invoke(null, accessor, logs, leaves, decorations);
                return;
            }
        }
        throw new IllegalStateException("updateLeaves not found");
    }

    private static void put(Map<BlockPos, BlockState> vanillaWorld, Map<BlockVec, Block> ourWorld,
            int x, int y, int z, int distance, boolean log) {
        if (log) {
            vanillaWorld.put(new BlockPos(x, y, z), Blocks.OAK_LOG.defaultBlockState());
            ourWorld.put(new BlockVec(x, y, z), Block.OAK_LOG);
        } else {
            vanillaWorld.put(new BlockPos(x, y, z),
                    Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE, distance));
            ourWorld.put(new BlockVec(x, y, z),
                    Block.OAK_LEAVES.withProperty("distance", Integer.toString(distance)));
        }
    }

    /** Minimal level backing vanilla's updateLeaves reads/writes. */
    private record VanillaLevelHandler(Map<BlockPos, BlockState> world, java.util.List<String> trace) implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    var pos = ((BlockPos) args[0]).immutable();
                    var state = this.world.get(pos);
                    this.trace.add("get " + pos.getX() + " " + pos.getY() + " " + pos.getZ());
                    return state != null ? state : Blocks.AIR.defaultBlockState();
                }
                case "setBlock" -> {
                    var pos = ((BlockPos) args[0]).immutable();
                    var state = (BlockState) args[1];
                    this.trace.add("set " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " "
                            + state.getOptionalValue(BlockStateProperties.DISTANCE).map(String::valueOf).orElse("-"));
                    this.world.put(pos, state);
                    return true;
                }
                case "toString" -> {
                    return "proxy";
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> throw new UnsupportedOperationException("unexpected call: " + method.getName());
            }
        }
    }

    /** Minimal accessor for our updateLeaves. */
    static final class MapAccessor implements Block.Getter, Block.Setter {
        private final Map<BlockVec, Block> world;
        final java.util.List<String> trace = new java.util.ArrayList<>();

        MapAccessor(Map<BlockVec, Block> world) {
            this.world = world;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var block = this.world.get(new BlockVec(x, y, z));
            this.trace.add("get " + x + " " + y + " " + z);
            return block != null ? block : Block.AIR;
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            var distance = block.getProperty("distance");
            this.trace.add("set " + x + " " + y + " " + z + " " + (distance != null ? distance : "-"));
            this.world.put(new BlockVec(x, y, z), block);
        }
    }
}
