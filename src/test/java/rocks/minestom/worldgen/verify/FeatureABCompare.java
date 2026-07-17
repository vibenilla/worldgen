package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.RandomSelectorFeature;
import rocks.minestom.worldgen.feature.placement.PlacementContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A/B compares vanilla placed features (real vanilla code, in process) against
 * our implementations on an identical procedural cave world with identical
 * feature randoms. Reports block output and draw-count divergences.
 *
 * Args (optional): placedFeatureName biomeName [runs]
 */
public final class FeatureABCompare {

    // --- shared synthetic world -------------------------------------------

    static long hash(long x, long z, long salt) {
        var h = x * 341873128712L + z * 132897987541L + salt * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 33)) * 0xFF51AFD7ED558CCDL;
        h = (h ^ (h >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return h ^ (h >>> 33);
    }

    static double latticeValue(int gx, int gz, long salt) {
        return (hash(gx, gz, salt) >>> 40) / (double) (1 << 24);
    }

    /** Smooth value noise in [0, range). */
    static int lattice(int x, int z, long salt, int range) {
        var gx = Math.floorDiv(x, 8);
        var gz = Math.floorDiv(z, 8);
        var fx = (x - gx * 8) / 8.0;
        var fz = (z - gz * 8) / 8.0;
        var v00 = latticeValue(gx, gz, salt);
        var v10 = latticeValue(gx + 1, gz, salt);
        var v01 = latticeValue(gx, gz + 1, salt);
        var v11 = latticeValue(gx + 1, gz + 1, salt);
        var v0 = v00 + (v10 - v00) * fx;
        var v1 = v01 + (v11 - v01) * fx;
        return (int) ((v0 + (v1 - v0) * fz) * range);
    }

    /** Canonical block id at a position, null for air. */
    static String baseState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319) {
            return null;
        }

        var floor = -45 + lattice(x, z, salt * 8 + 1, 20);       // top solid floor block
        var gap = 4 + lattice(x, z, salt * 8 + 2, 15);           // air gap height
        var ceil = floor + 1 + gap;                              // first solid ceiling block
        if (y <= floor || (y >= ceil && y <= ceil + 14)) {
            // occasional gravel/dirt floor caps (not base-stone replaceable)
            if (y == floor) {
                var cap = lattice(x, z, salt * 8 + 5, 14);
                if (cap >= 12) {
                    return "minecraft:gravel";
                }
                if (cap >= 10) {
                    return "minecraft:dirt";
                }
            }

            return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
        }

        // flooded caves: parts of the gap fill with water from the floor up
        var flood = lattice(x, z, salt * 8 + 6, 12);
        if (flood >= 8) {
            var floodLevel = Math.min(ceil - 1, floor + 1 + (flood - 7) * 2);
            if (y <= floodLevel) {
                return "minecraft:water";
            }
        }

        // shallow water / lava pools on parts of the floor
        if (y == floor + 1 && lattice(x, z, salt * 8 + 3, 10) >= 8) {
            return y < -30 && lattice(x, z, salt * 8 + 7, 10) >= 5 ? "minecraft:lava" : "minecraft:water";
        }

        // occasional one-block-thick hanging shelf below the ceiling
        if (y == ceil - 3 && lattice(x, z, salt * 8 + 4, 12) >= 10) {
            return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
        }

        return null;
    }

    // --- vanilla side ------------------------------------------------------

    static final Map<String, BlockState> VANILLA_STATES = new HashMap<>();

    static BlockState vanillaState(String id) {
        return VANILLA_STATES.computeIfAbsent(id, key -> {
            try {
                return net.minecraft.commands.arguments.blocks.BlockStateParser
                        .parseForBlock(BuiltInRegistries.BLOCK, key, false).blockState();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    static final boolean TRACE_SETS = Boolean.getBoolean("replay.traceSets");
    static ChunkVegetationReplay.CountingRandom drawCounter;
    static CountingOurRandom ourDrawCounter;

    static final class Handler implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;
        final Object biomeHolder;
        private Object randomHolder;

        Handler(long salt, Object biomeHolder) {
            this.salt = salt;
            this.biomeHolder = biomeHolder;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = baseState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : vanillaState(base);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "getFluidState" -> {
                    return this.state((BlockPos) args[0]).getFluidState();
                }
                case "isStateAtPosition" -> {
                    return ((java.util.function.Predicate<BlockState>) args[1]).test(this.state((BlockPos) args[0]));
                }
                case "isFluidAtPosition" -> {
                    return ((java.util.function.Predicate<net.minecraft.world.level.material.FluidState>) args[1])
                            .test(this.state((BlockPos) args[0]).getFluidState());
                }
                case "setBlock" -> {
                    var setPos = ((BlockPos) args[0]).immutable();
                    this.overlay.put(setPos, (BlockState) args[1]);
                    if (TRACE_SETS) {
                        System.out.println("VSET " + setPos.getX() + " " + setPos.getY() + " " + setPos.getZ()
                                + " " + canonical((BlockState) args[1]) + " @" + (drawCounter != null ? drawCounter.count : -1));
                    }
                    return true;
                }
                case "removeBlock", "destroyBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "isWaterAt" -> {
                    return this.state((BlockPos) args[0]).getFluidState()
                            .is(net.minecraft.tags.FluidTags.WATER);
                }
                case "isOutsideBuildHeight" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    return y < -64 || y > 319;
                }
                case "getMinY" -> {
                    return -64;
                }
                case "getMaxY" -> {
                    return 319;
                }
                case "getHeight" -> {
                    if (args == null || args.length == 0) {
                        return 384;
                    }
                    // Heightmap scan over the synthetic world
                    var worldSurface = String.valueOf(args[0]).startsWith("WORLD_SURFACE");
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    for (var y = 45; y >= -64; y--) {
                        var state = this.state(new BlockPos(x, y, z));
                        if (worldSurface ? !state.isAir() : state.isSolid()) {
                            return y + 1;
                        }
                    }
                    return -64;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "getBiome" -> {
                    return this.biomeHolder;
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? java.util.Optional.empty() : null;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return this.randomHolder != null ? this.randomHolder
                            : (this.randomHolder = net.minecraft.util.RandomSource.create(0L));
                }
                case "getChunk" -> {
                    return null;
                }
                case "toString" -> {
                    return "ab-compare-level";
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    static String canonical(BlockState state) {
        var name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        var props = new TreeMap<String, String>();
        for (var property : state.getProperties()) {
            props.put(property.getName(), ((net.minecraft.world.level.block.state.properties.Property) property)
                    .getName(state.getValue(property)));
        }
        return name + props;
    }

    static String canonical(Block block) {
        return block.key().asString() + new TreeMap<>(block.properties());
    }

    // --- our side ------------------------------------------------------------

    static final Map<String, Block> OUR_STATES = new HashMap<>();

    static Block ourState(String id) {
        return OUR_STATES.computeIfAbsent(id, key -> Block.fromKey(Key.key(key)));
    }

    static final class OurWorld implements Block.Getter, Block.Setter,
            rocks.minestom.worldgen.feature.LargeDripstoneFeature.WorldSurface {

        @Override
        public int worldSurfaceHeight(int x, int z) {
            for (var y = 45; y >= -64; y--) {
                if (!this.getBlock(x, y, z).isAir()) {
                    return y + 1;
                }
            }
            return -64;
        }

        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseState(x, y, z, this.salt);
            return base == null ? Block.AIR : ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
            if (TRACE_SETS) {
                System.out.println("OSET " + x + " " + y + " " + z + " " + canonical(block)
                        + " @" + (ourDrawCounter != null ? ourDrawCounter.count : -1));
            }
        }
    }

    /** Counts draws on our random source. */
    static final class CountingOurRandom implements rocks.minestom.worldgen.random.RandomSource {
        final rocks.minestom.worldgen.random.RandomSource delegate;
        long count;

        CountingOurRandom(rocks.minestom.worldgen.random.RandomSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public rocks.minestom.worldgen.random.RandomSource fork() {
            return this.delegate.fork();
        }

        @Override
        public rocks.minestom.worldgen.random.PositionalRandomFactory forkPositional() {
            return this.delegate.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            this.delegate.setSeed(seed);
        }

        @Override
        public int nextInt() {
            this.count++;
            return this.delegate.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            var value = this.delegate.nextInt(bound);
            this.count++;
            if (this.count <= DEBUG_DRAWS) System.out.println("ODRAW " + this.count + " nextInt(" + bound + ")=" + value);
            return value;
        }

        static final long DEBUG_DRAWS = Long.getLong("replay.debugDraws", 0);

        @Override
        public long nextLong() {
            var value = this.delegate.nextLong();
            this.count++;
            if (this.count <= DEBUG_DRAWS) System.out.println("ODRAW " + this.count + " nextLong()=" + value);
            return value;
        }

        @Override
        public boolean nextBoolean() {
            this.count++;
            return this.delegate.nextBoolean();
        }

        @Override
        public float nextFloat() {
            var value = this.delegate.nextFloat();
            this.count++;
            if (this.count <= DEBUG_DRAWS) System.out.println("ODRAW " + this.count + " nextFloat()=" + value);
            return value;
        }

        @Override
        public double nextDouble() {
            this.count++;
            return this.delegate.nextDouble();
        }
    }

    // --- main ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        bindAllTags();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var presets = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);
        var noiseSettings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseSettings);

        var dataPack = new DataPack(Path.of("data/mc/datapack"));
        var loader = new FeatureLoader(dataPack);

        var cases = args.length >= 2
                ? new String[][]{{args[0], args[1]}}
                : new String[][]{
                        {"cave_vines", "minecraft:lush_caves"},
                        {"lush_caves_vegetation", "minecraft:lush_caves"},
                        {"lush_caves_ceiling_vegetation", "minecraft:lush_caves"},
                        {"lush_caves_clay", "minecraft:lush_caves"},
                        {"dripstone_cluster", "minecraft:dripstone_caves"},
                        {"large_dripstone", "minecraft:dripstone_caves"},
                        {"pointed_dripstone", "minecraft:dripstone_caves"},
                };
        var runs = args.length >= 3 ? Integer.parseInt(args[2]) : 8;

        for (var testCase : cases) {
            var placedName = testCase[0];
            var biomeName = testCase[1];

            var placed = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                    .getOrThrow(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.PLACED_FEATURE,
                            net.minecraft.resources.Identifier.parse("minecraft:" + placedName)))
                    .value();
            var biome = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                    .getOrThrow(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.BIOME,
                            net.minecraft.resources.Identifier.parse(biomeName)));

            var ourPlaced = loader.getPlacedFeature(Key.key("minecraft:" + placedName));
            if (ourPlaced == null) {
                System.out.println(placedName + ": OUR SIDE UNPARSED");
                continue;
            }

            var totalMismatches = 0L;
            var totalVanillaSets = 0L;
            var totalOurSets = 0L;
            var drawMismatchRuns = 0;
            var printed = 0;
            for (var run = 0; run < runs; run++) {
                var salt = 1000L + run;
                var featureSeed = 987654321L + run * 7919L;
                var startX = run * 496;
                var startZ = -run * 272;

                // vanilla
                var handler = new Handler(salt, biome);
                var level = (WorldGenLevel) Proxy.newProxyInstance(
                        FeatureABCompare.class.getClassLoader(),
                        new Class<?>[]{WorldGenLevel.class},
                        handler);
                var vanillaCounted = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(0L));
                drawCounter = vanillaCounted;
                var vanillaRandom = new net.minecraft.world.level.levelgen.WorldgenRandom(vanillaCounted);
                vanillaRandom.setFeatureSeed(featureSeed, 3, 5);
                placed.placeWithBiomeCheck(level, generator, vanillaRandom, new BlockPos(startX, -64, startZ));

                var vanillaSets = new TreeMap<String, String>();
                for (var entry : handler.overlay.entrySet()) {
                    vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                            canonical(entry.getValue()));
                }

                // ours
                var ourWorld = new OurWorld(salt);
                var surfaceHeights = new int[16 * 16];
                var waterHeights = new int[16 * 16];
                var placementContext = new PlacementContext(
                        ourWorld, startX, startZ, 16, 16, surfaceHeights, waterHeights,
                        -64, 319, 63, null, null, loader);
                var ourCounted = new CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(0L));
                ourDrawCounter = ourCounted;
                var ourRandom = new rocks.minestom.worldgen.random.WorldgenRandom(ourCounted);
                ourRandom.setFeatureSeed(featureSeed, 3, 5);

                var configured = ourPlaced.configuredFeature(loader);
                if (configured == null) {
                    System.out.println(placedName + ": OUR CONFIGURED FEATURE MISSING");
                    break;
                }

                ourPlaced.place(placementContext, ourRandom, new BlockVec(startX, -64, startZ), (position, featureRandom) -> {
                    if (position.blockY() < -64 || position.blockY() > 319) {
                        return;
                    }

                    var context = new FeaturePlaceContext<>(
                            ourWorld, featureRandom, position, configured.config(), featureSeed, -64, 319);
                    var featureImpl = configured.feature();
                    if (featureImpl instanceof RandomSelectorFeature randomSelector) {
                        randomSelector.place((FeaturePlaceContext) context, loader);
                    } else {
                        ((Feature) featureImpl).place((FeaturePlaceContext) context);
                    }
                });

                var ourSets = new TreeMap<String, String>();
                for (var entry : ourWorld.overlay.entrySet()) {
                    ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                            canonical(entry.getValue()));
                }

                totalVanillaSets += vanillaSets.size();
                totalOurSets += ourSets.size();
                if (vanillaCounted.count != ourCounted.count) {
                    drawMismatchRuns++;
                    if (printed < 4) {
                        System.out.println(placedName + " run=" + run + " DRAWS vanilla=" + vanillaCounted.count + " ours=" + ourCounted.count);
                        printed++;
                    }
                }

                var keys = new java.util.TreeSet<String>();
                keys.addAll(vanillaSets.keySet());
                keys.addAll(ourSets.keySet());
                for (var key : keys) {
                    var vanilla = vanillaSets.get(key);
                    var ours = ourSets.get(key);
                    if (!java.util.Objects.equals(vanilla, ours)) {
                        totalMismatches++;
                        if (printed < 24) {
                            System.out.println(placedName + " run=" + run + " at " + key
                                    + " vanilla=" + vanilla + " ours=" + ours);
                            printed++;
                        }
                    }
                }
            }
            System.out.println(placedName + ": runs=" + runs + " vanillaSets=" + totalVanillaSets
                    + " ourSets=" + totalOurSets + " mismatches=" + totalMismatches
                    + " drawMismatchRuns=" + drawMismatchRuns);
            System.out.println();
        }
    }

    /** Binds every datapack block and fluid tag onto the built-in registries. */
    static void bindAllTags() throws Exception {
        bindRegistryTags(BuiltInRegistries.BLOCK, net.minecraft.core.registries.Registries.BLOCK,
                Path.of("data/mc/datapack/data/minecraft/tags/block"));
        bindRegistryTags(BuiltInRegistries.FLUID, net.minecraft.core.registries.Registries.FLUID,
                Path.of("data/mc/datapack/data/minecraft/tags/fluid"));
    }

    static <T> void bindRegistryTags(Object registry, net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registryKey, Path tagDir) throws Exception {
        var idToTags = new HashMap<String, List<TagKey<T>>>();
        try (var stream = Files.walk(tagDir)) {
            for (var file : stream.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(".json")).toList()) {
                var relative = tagDir.relativize(file).toString().replace('\\', '/').replace(".json", "");
                var tag = TagKey.create(registryKey, net.minecraft.resources.Identifier.parse("minecraft:" + relative));
                for (var id : resolveTagValues(tagDir, relative)) {
                    idToTags.computeIfAbsent(id, unused -> new ArrayList<>()).add(tag);
                }
            }
        }

        var holderBindTags = Class.forName("net.minecraft.core.Holder$Reference")
                .getDeclaredMethod("bindTags", java.util.Collection.class);
        holderBindTags.setAccessible(true);
        var listElements = registry.getClass().getMethod("listElements");
        for (var holderObject : ((java.util.stream.Stream<?>) listElements.invoke(registry)).toList()) {
            var key = holderObject.getClass().getMethod("key").invoke(holderObject);
            var location = key.getClass().getMethod("identifier").invoke(key);
            var tags = idToTags.get(location.toString());
            holderBindTags.invoke(holderObject, tags != null ? tags : List.of());
        }
    }

    static java.util.Set<String> resolveTagValues(Path tagDir, String tagName) throws Exception {
        var result = new java.util.HashSet<String>();
        var path = tagDir.resolve(tagName + ".json");
        if (!Files.exists(path)) {
            return result;
        }
        var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        for (var value : json.getAsJsonArray("values")) {
            var name = value.getAsString();
            if (name.startsWith("#")) {
                result.addAll(resolveTagValues(tagDir, name.substring(name.indexOf(':') + 1)));
            } else {
                result.add(name);
            }
        }
        return result;
    }
}
