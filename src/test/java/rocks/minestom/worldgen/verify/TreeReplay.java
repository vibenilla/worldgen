package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Replays a full vanilla configured feature (real vanilla classes) against a
 * world snapshot + rng state captured with worldgen.treeTrace, to compare a
 * single tree attempt bit for bit.
 * Args: trace file, configured feature json, tree key (pre:x:y:z), originX,Y,Z.
 */
public final class TreeReplay {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        bindDatapackTags();

        var traceFile = Path.of(args[0]);
        var featureJson = Path.of(args[1]);
        var treeKey = args[2];
        var origin = new BlockPos(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));

        long seedLo = 0;
        long seedHi = 0;
        var world = new HashMap<BlockPos, BlockState>();
        for (var line : Files.readAllLines(traceFile)) {
            if (!line.startsWith("TRACE ")) {
                continue;
            }
            var parts = line.split(" ");
            if (parts.length < 3 || !parts[2].equals(treeKey)) {
                continue;
            }
            switch (parts[1]) {
                case "rng" -> {
                    seedLo = Long.parseLong(parts[3]);
                    seedHi = Long.parseLong(parts[4]);
                }
                case "world" -> {
                    var pos = new BlockPos(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
                    world.put(pos, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, parts[6], false).blockState());
                }
                default -> {
                }
            }
        }
        System.out.println("world=" + world.size() + " rng=" + seedLo + "," + seedHi);

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var ops = RegistryOps.create(JsonOps.INSTANCE, lookup);
        var json = JsonParser.parseString(Files.readString(featureJson));
        var feature = ConfiguredFeature.DIRECT_CODEC.parse(ops, json).getOrThrow();
        System.out.println("feature=" + feature.feature());

        var minY = world.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        var maxY = world.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0) + 6;
        var level = (WorldGenLevel) Proxy.newProxyInstance(
                TreeReplay.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class},
                new Handler(world, minY, maxY));

        // Probe the real heightmap predicates on tree blocks
        var leafState = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,
                "minecraft:dark_oak_leaves[distance=7,persistent=false,waterlogged=false]", false).blockState();
        var litterState = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK,
                "minecraft:leaf_litter[facing=north,segment_amount=2]", false).blockState();
        for (var type : new Heightmap.Types[]{Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR,
                Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES}) {
            System.out.println("PREDICATE " + type + " leaves=" + type.isOpaque().test(leafState)
                    + " litter=" + type.isOpaque().test(litterState));
        }
        System.out.println("HEIGHTPROBE OF=" + new Handler(new HashMap<>(world), minY, maxY)
                .height(Heightmap.Types.OCEAN_FLOOR, origin.getX(), origin.getZ())
                + " WS=" + new Handler(new HashMap<>(world), minY, maxY)
                .height(Heightmap.Types.WORLD_SURFACE, origin.getX(), origin.getZ()));

        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(new XoroshiroRandomSource(seedLo, seedHi));
        var result = feature.place(level, null, random, origin);
        System.out.println("RESULT " + result);
    }

    /** Binds the block tags tree placement depends on, from the datapack JSONs. */
    private static void bindDatapackTags() throws Exception {
        var tagsNeeded = List.of("replaceable_by_trees", "logs", "prevents_nearby_leaf_decay",
                "cannot_replace_below_tree_trunk", "leaves", "dirt", "supports_vegetation",
                "huge_red_mushroom_can_place_on", "huge_brown_mushroom_can_place_on",
                "replaceable_by_mushrooms", "air", "moss_replaceable", "lush_ground_replaceable",
                "cave_vines", "climbable");
        var blockToTags = new HashMap<String, List<TagKey<net.minecraft.world.level.block.Block>>>();
        for (var tagName : tagsNeeded) {
            var key = TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                    net.minecraft.resources.Identifier.parse("minecraft:" + tagName));
            for (var block : resolveTag(tagName)) {
                blockToTags.computeIfAbsent(block, unused -> new ArrayList<>()).add(key);
            }
        }

        var holderBindTags = Class.forName("net.minecraft.core.Holder$Reference")
                .getDeclaredMethod("bindTags", java.util.Collection.class);
        holderBindTags.setAccessible(true);
        var listElements = BuiltInRegistries.BLOCK.getClass().getMethod("listElements");
        for (var holderObject : ((java.util.stream.Stream<?>) listElements.invoke(BuiltInRegistries.BLOCK)).toList()) {
            var key = holderObject.getClass().getMethod("key").invoke(holderObject);
            var location = key.getClass().getMethod("identifier").invoke(key);
            var tags = blockToTags.get(location.toString());
            if (tags != null) {
                holderBindTags.invoke(holderObject, tags);
            }
        }
    }

    private static java.util.Set<String> resolveTag(String tagName) throws Exception {
        var result = new java.util.HashSet<String>();
        var path = Path.of("data/mc/datapack/data/minecraft/tags/block/" + tagName + ".json");
        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        for (var value : json.getAsJsonArray("values")) {
            var name = value.getAsString();
            if (name.startsWith("#")) {
                result.addAll(resolveTag(name.substring(name.indexOf(':') + 1)));
            } else {
                result.add(name);
            }
        }
        return result;
    }

    static final class Handler implements InvocationHandler {
        static Object biomeHolder;
        static net.minecraft.world.level.biome.BiomeManager biomeManager;
        static java.util.concurrent.atomic.AtomicLong drawCounter;
        private final Map<BlockPos, BlockState> world;
        private final int minY;
        private final int maxY;
        private final Map<Long, Integer> frozenHeights;
        private Object randomHolder;

        Handler(Map<BlockPos, BlockState> world, int minY, int maxY) {
            this.world = world;
            this.minY = minY;
            this.maxY = maxY;
            this.frozenHeights = Boolean.getBoolean("replay.frozenHeightmap") ? new HashMap<>() : null;
            this.frozenWorld = this.frozenHeights != null ? new HashMap<>(world) : null;
        }

        private final Map<BlockPos, BlockState> frozenWorld;

        private BlockState state(BlockPos pos) {
            var state = this.world.get(pos.immutable());
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            if (Boolean.getBoolean("replay.traceReads") && !method.getName().equals("getHeight")) {
                System.out.println("VCALL " + method.getName() + " " + (args != null ? java.util.Arrays.toString(args) : "[]"));
            }
            switch (method.getName()) {
                case "getBlockState" -> {
                    var result = this.state((BlockPos) args[0]);
                    if (Boolean.getBoolean("replay.traceReads")) {
                        System.out.println("VGET " + args[0] + " -> " + result);
                    }
                    return result;
                }
                case "getFluidState" -> {
                    return this.state((BlockPos) args[0]).getFluidState();
                }
                case "isStateAtPosition" -> {
                    var result = ((java.util.function.Predicate<BlockState>) args[1]).test(this.state((BlockPos) args[0]));
                    if (Boolean.getBoolean("replay.traceReads")) {
                        System.out.println("VISAT " + args[0] + " -> " + result);
                    }
                    return result;
                }
                case "isFluidAtPosition" -> {
                    return ((java.util.function.Predicate<net.minecraft.world.level.material.FluidState>) args[1])
                            .test(this.state((BlockPos) args[0]).getFluidState());
                }
                case "isWaterAt" -> {
                    return this.state((BlockPos) args[0]).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
                }
                case "setBlock" -> {
                    var pos = ((BlockPos) args[0]).immutable();
                    this.world.put(pos, (BlockState) args[1]);
                    var at = drawCounter != null ? " @" + drawCounter.get() : "";
                    System.out.println("VSET " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + args[1] + at);
                    return true;
                }
                case "getHeightmapPos" -> {
                    var type = (Heightmap.Types) args[0];
                    var pos = (BlockPos) args[1];
                    return new BlockPos(pos.getX(), this.height(type, pos.getX(), pos.getZ()), pos.getZ());
                }
                case "getHeight" -> {
                    if (args == null || args.length == 0) {
                        return this.maxY - this.minY;
                    }
                    var result = this.height((Heightmap.Types) args[0], (Integer) args[1], (Integer) args[2]);
                    if (Boolean.getBoolean("replay.logHeights")) {
                        var at = drawCounter != null ? " @" + drawCounter.get() : "";
                        System.out.println("HQ " + args[0] + " " + args[1] + " " + args[2] + " -> " + result + at);
                    }
                    return result;
                }
                case "getMinY" -> {
                    return this.minY - 64;
                }
                case "getMaxY" -> {
                    return this.maxY + 200;
                }
                case "isOutsideBuildHeight" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    return y < -64 || y > 319;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getChunkGenerator" -> {
                    return null;
                }
                case "levelEvent", "playSound" -> {
                    return null;
                }
                case "getCurrentDifficultyAt" -> {
                    return null;
                }
                case "setCurrentlyGenerating" -> {
                    return null;
                }
                case "getSeed" -> {
                    return 0L;
                }
                case "getLevel" -> {
                    return null;
                }
                case "getBlockEntity" -> {
                    return args.length > 1 ? Optional.empty() : null;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "ensureCanWrite" -> {
                    return true;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "removeBlock", "destroyBlock" -> {
                    var pos = ((BlockPos) args[0]).immutable();
                    this.world.put(pos, Blocks.AIR.defaultBlockState());
                    System.out.println("VSET " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " removed");
                    return true;
                }
                case "getRandom" -> {
                    return this.randomHolder != null ? this.randomHolder
                            : (this.randomHolder = net.minecraft.util.RandomSource.create(0L));
                }
                case "getChunk" -> {
                    return null;
                }
                case "getBiome" -> {
                    return biomeManager != null ? biomeManager.getBiome((BlockPos) args[0]) : biomeHolder;
                }
                case "toString" -> {
                    return "tree-replay-level";
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

        int height(Heightmap.Types type, int x, int z) {
            if (this.frozenHeights != null) {
                var key = (long) type.ordinal() << 60 | ((long) (x & 0xFFFFFF) << 24) | (z & 0xFFFFFF);
                var cached = this.frozenHeights.get(key);
                if (cached != null) {
                    return cached;
                }
                var value = this.computeHeight(type, x, z, this.frozenWorld);
                this.frozenHeights.put(key, value);
                return value;
            }
            return this.computeHeight(type, x, z, this.world);
        }

        private int computeHeight(Heightmap.Types type, int x, int z, Map<BlockPos, BlockState> blocks) {
            var predicate = type.isOpaque();
            for (var y = this.maxY; y >= this.minY; y--) {
                var state = blocks.get(new BlockPos(x, y, z));
                if (state != null && predicate.test(state)) {
                    return y + 1;
                }
            }
            return this.minY;
        }
    }
}
