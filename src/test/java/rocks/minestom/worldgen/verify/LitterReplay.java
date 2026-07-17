package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Replays vanilla's ACTUAL tree decorators (from the datapack JSON, decoded
 * with vanilla codecs) against a world snapshot + random state captured by the
 * worldgen.treeTrace instrumentation, printing every read and placement.
 * Usage: args = trace file, configured feature json, tree key (x:y:z).
 */
public final class LitterReplay {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        var traceFile = Path.of(args[0]);
        var featureJson = Path.of(args[1]);
        var treeKey = args[2];

        long seedLo = 0;
        long seedHi = 0;
        var world = new HashMap<BlockPos, BlockState>();
        var logs = new LinkedHashSet<BlockPos>();
        var leaves = new LinkedHashSet<BlockPos>();

        var blockLookup = BuiltInRegistries.BLOCK;
        for (var line : Files.readAllLines(traceFile)) {
            if (!line.startsWith("TRACE ")) {
                continue;
            }
            var parts = line.split(" ");
            if (!parts[2].equals(treeKey)) {
                continue;
            }
            switch (parts[1]) {
                case "rng" -> {
                    seedLo = Long.parseLong(parts[3]);
                    seedHi = Long.parseLong(parts[4]);
                }
                case "logset" -> {
                    for (var pos : parts[3].split(";")) {
                        var xyz = pos.split(",");
                        logs.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
                    }
                }
                case "leafset" -> {
                    for (var pos : parts[3].split(";")) {
                        var xyz = pos.split(",");
                        leaves.add(new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2])));
                    }
                }
                case "world" -> {
                    var pos = new BlockPos(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
                    var state = BlockStateParser.parseForBlock(blockLookup, parts[6], false).blockState();
                    world.put(pos, state);
                }
                default -> {
                }
            }
        }

        System.out.println("world=" + world.size() + " logs=" + logs.size() + " leaves=" + leaves.size()
                + " rng=" + seedLo + "," + seedHi);

        // Decode the decorators with vanilla codecs
        var json = JsonParser.parseString(Files.readString(featureJson)).getAsJsonObject();
        var decoratorsJson = json.getAsJsonObject("config").getAsJsonArray("decorators");
        var decorators = new ArrayList<TreeDecorator>();
        for (var element : decoratorsJson) {
            decorators.add(TreeDecorator.CODEC.parse(JsonOps.INSTANCE, element).getOrThrow());
        }
        System.out.println("decorators=" + decorators);

        var minY = world.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        var maxY = world.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0) + 4;

        var handler = new LevelHandler(world, minY, maxY);
        var level = (WorldGenLevel) Proxy.newProxyInstance(
                LitterReplay.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class},
                handler);

        // Sanity: vanilla and our random must produce identical sequences from
        // the transported state
        var vanillaProbe = new XoroshiroRandomSource(seedLo, seedHi);
        var oursProbe = new rocks.minestom.worldgen.random.XoroshiroRandomSource(seedLo, seedHi);
        var probe = new StringBuilder("PROBE draws:");
        for (var index = 0; index < 8; index++) {
            probe.append(' ').append(vanillaProbe.nextInt(100)).append('/').append(oursProbe.nextInt(100));
        }
        System.out.println(probe);

        var offsets = System.getProperty("replay.offsets");
        if (offsets != null) {
            // Score rng offsets against a target litter map from VLITTER lines
            var target = new HashMap<BlockPos, String>();
            for (var line : Files.readAllLines(Path.of(System.getProperty("replay.vlitter")))) {
                if (!line.startsWith("VLITTER")) {
                    continue;
                }
                var parts = line.split(" ");
                target.put(new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])), parts[4]);
            }
            for (var offset = -6; offset <= 12; offset++) {
                var trialWorld = new HashMap<>(world);
                var trialLevel = (WorldGenLevel) Proxy.newProxyInstance(
                        LitterReplay.class.getClassLoader(),
                        new Class<?>[]{WorldGenLevel.class},
                        new LevelHandler(trialWorld, minY, maxY, false));
                var trialRandom = new net.minecraft.world.level.levelgen.WorldgenRandom(new XoroshiroRandomSource(seedLo, seedHi));
                for (var index = 0; index < Math.abs(offset); index++) {
                    if (offset > 0) {
                        trialRandom.nextLong();
                    } else {
                        trialRandom.nextFloat();
                    }
                }
                var placements = new HashMap<BlockPos, BlockState>();
                BiConsumer<BlockPos, BlockState> trialSetter = (pos, state) -> {
                    placements.put(pos.immutable(), state);
                    trialWorld.put(pos.immutable(), state);
                };
                var trialContext = new TreeDecorator.Context(trialLevel, trialSetter, trialRandom, logs, leaves, Set.of());
                for (var decorator : decorators) {
                    decorator.place(trialContext);
                }
                var good = 0;
                var bad = 0;
                for (var entry : placements.entrySet()) {
                    var expected = target.get(entry.getKey());
                    var actual = entry.getValue().toString()
                            .replace("Block{", "").replace("}", "");
                    if (expected != null && expected.replace("minecraft:leaf_litter", "").equals(actual.replace("minecraft:leaf_litter", ""))) {
                        good++;
                    } else {
                        bad++;
                    }
                }
                System.out.println("OFFSET " + offset + " good=" + good + " bad=" + bad + " placements=" + placements.size());
            }
            return;
        }

        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(new XoroshiroRandomSource(seedLo, seedHi));
        BiConsumer<BlockPos, BlockState> setter = (pos, state) -> {
            System.out.println("VSET " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state);
            world.put(pos.immutable(), state);
        };

        var context = new TreeDecorator.Context(level, setter, random, logs, leaves, Set.of());
        for (var decorator : decorators) {
            System.out.println("RUN " + decorator);
            decorator.place(context);
        }
    }

    private static final class LevelHandler implements InvocationHandler {
        private final Map<BlockPos, BlockState> world;
        private final int minY;
        private final int maxY;
        private final boolean verbose;

        LevelHandler(Map<BlockPos, BlockState> world, int minY, int maxY) {
            this(world, minY, maxY, true);
        }

        LevelHandler(Map<BlockPos, BlockState> world, int minY, int maxY, boolean verbose) {
            this.world = world;
            this.minY = minY;
            this.maxY = maxY;
            this.verbose = verbose;
        }

        private BlockState state(BlockPos pos) {
            var state = this.world.get(pos.immutable());
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "isStateAtPosition" -> {
                    var pos = (BlockPos) args[0];
                    if (this.verbose) {
                        System.out.println("VQ " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + this.state(pos));
                    }
                    return ((java.util.function.Predicate<BlockState>) args[1]).test(this.state(pos));
                }
                case "isFluidAtPosition" -> {
                    return ((java.util.function.Predicate<net.minecraft.world.level.material.FluidState>) args[1])
                            .test(this.state((BlockPos) args[0]).getFluidState());
                }
                case "getHeightmapPos" -> {
                    var type = (Heightmap.Types) args[0];
                    var pos = (BlockPos) args[1];
                    var predicate = type.isOpaque();
                    for (var y = this.maxY; y >= this.minY; y--) {
                        if (predicate.test(this.state(new BlockPos(pos.getX(), y, pos.getZ())))) {
                            if (this.verbose) {
                                System.out.println("VHEIGHT " + type + " " + pos.getX() + " " + pos.getZ() + " -> " + (y + 1));
                            }
                            return new BlockPos(pos.getX(), y + 1, pos.getZ());
                        }
                    }
                    if (this.verbose) {
                        System.out.println("VHEIGHT " + type + " " + pos.getX() + " " + pos.getZ() + " -> min");
                    }
                    return new BlockPos(pos.getX(), this.minY, pos.getZ());
                }
                case "getHeight" -> {
                    if (args == null || args.length == 0) {
                        return this.maxY - this.minY;
                    }
                    var type = (Heightmap.Types) args[0];
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    var predicate = type.isOpaque();
                    for (var y = this.maxY; y >= this.minY; y--) {
                        if (predicate.test(this.state(new BlockPos(x, y, z)))) {
                            return y + 1;
                        }
                    }
                    return this.minY;
                }
                case "setBlock" -> {
                    var pos = ((BlockPos) args[0]).immutable();
                    this.world.put(pos, (BlockState) args[1]);
                    if (this.verbose) {
                        System.out.println("VSETDIRECT " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + args[1]);
                    }
                    return true;
                }
                case "getBlockEntity" -> {
                    return args.length > 1 ? Optional.empty() : null;
                }
                case "getMinY" -> {
                    return this.minY;
                }
                case "getMaxY" -> {
                    return this.maxY;
                }
                case "toString" -> {
                    return "replay-level";
                }
                case "hashCode" -> {
                    return 0;
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> throw new UnsupportedOperationException("unexpected: " + method.getName());
            }
        }
    }
}
