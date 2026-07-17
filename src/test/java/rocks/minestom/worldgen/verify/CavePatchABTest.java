package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.feature.RootSystemFeature;
import rocks.minestom.worldgen.feature.SculkPatchFeature;
import rocks.minestom.worldgen.feature.UnderwaterMagmaFeature;
import rocks.minestom.worldgen.feature.configurations.RootSystemConfiguration;
import rocks.minestom.worldgen.feature.configurations.SculkPatchConfiguration;
import rocks.minestom.worldgen.feature.configurations.UnderwaterMagmaConfiguration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the sculk patch, root system and underwater magma feature
 * ports against real vanilla 26.2 code in process: both sides run over
 * identical synthetic worlds tailored to each feature (a deep-dark-like
 * stone/deepslate cave for sculk patches, a lush-cave-like moss ceiling over
 * a rooted stone shaft for the root system, and a buried underwater floor
 * for underwater magma) from identical random sequences, and every block
 * write plus the total draw count must match.
 */
final class CavePatchABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 300;

    private static boolean traceSets = false;
    private static int traceCounter = 0;
    private static Object lookup;
    private static net.minecraft.world.level.chunk.ChunkGenerator generator;
    private static FeatureLoader loader;
    private static net.minecraft.world.level.chunk.ChunkAccess dummyChunk;

    /** A working (but otherwise unused) {@code ChunkAccess} so {@code markPosForPostProcessing} has somewhere to write. */
    private static net.minecraft.world.level.chunk.ChunkAccess dummyChunk() {
        if (dummyChunk == null) {
            var heightAccessor = new net.minecraft.world.level.LevelHeightAccessor() {
                @Override
                public int getHeight() {
                    return 384;
                }

                @Override
                public int getMinY() {
                    return -64;
                }
            };
            var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
            var biomeLookup = registries.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
            var biomeList = biomeLookup.listElements().<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>map(holder -> holder).toList();
            var biomeIdMap = new net.minecraft.core.IdMap<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>>() {
                @Override
                public int getId(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> thing) {
                    return biomeList.indexOf(thing);
                }

                @Override
                public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> byId(int id) {
                    return id >= 0 && id < biomeList.size() ? biomeList.get(id) : null;
                }

                @Override
                public int size() {
                    return biomeList.size();
                }

                @Override
                public java.util.Iterator<net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome>> iterator() {
                    return biomeList.iterator();
                }
            };
            var blockStrategy = net.minecraft.world.level.chunk.Strategy.createForBlockStates(
                    net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
            var biomeStrategy = net.minecraft.world.level.chunk.Strategy.createForBiomes(biomeIdMap);
            var containerFactory = new net.minecraft.world.level.chunk.PalettedContainerFactory(
                    blockStrategy,
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                    null,
                    biomeStrategy,
                    biomeList.get(0),
                    null);
            dummyChunk = new net.minecraft.world.level.chunk.ProtoChunk(
                    new net.minecraft.world.level.ChunkPos(0, 0),
                    net.minecraft.world.level.chunk.UpgradeData.EMPTY,
                    heightAccessor,
                    containerFactory,
                    null);
        }

        return dummyChunk;
    }

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        lookup = registries;

        var presets = registries.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);
        var noiseSettings = registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseSettings);

        loader = new FeatureLoader(new DataPack(ROOT));
    }

    // --- sculk patch ---------------------------------------------------------

    @Test
    void sculkPatchDeepDark() throws Exception {
        runSculkPatchCase("sculk_patch_deep_dark");
    }

    @Test
    void sculkPatchAncientCity() throws Exception {
        runSculkPatchCase("sculk_patch_ancient_city");
    }

    private static void runSculkPatchCase(String configName) throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/" + configName + ".json"))).getAsJsonObject();

        var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();

        var feature = new SculkPatchFeature();
        var config = SculkPatchConfiguration.fromJson(configJson.get("config"));

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 6000L + run;
            var seed = 13579L + run * 65599L;
            var x = 500 - run * 23;
            var z = -700 + run * 37;
            var floor = sculkFloorHeight(x, z, salt);
            var originY = floor + 1;

            traceSets = false;
            traceCounter = 0;

            var handler = new CaveLevel(salt, CavePatchABTest::sculkCaveState);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    CavePatchABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, new BlockPos(x, originY, z));

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            traceCounter = 0;
            var ourWorld = new OurCaveWorld(salt, CavePatchABTest::sculkCaveState);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, originY, z), config, seed, -64, 319, 63);
            feature.place(context);
            traceSets = false;

            var ourSets = new TreeMap<String, String>();
            for (var entry : ourWorld.overlay.entrySet()) {
                ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println(configName + " run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            var keys = new TreeSet<String>();
            keys.addAll(vanillaSets.keySet());
            keys.addAll(ourSets.keySet());
            for (var key : keys) {
                var vanilla = vanillaSets.get(key);
                var ours = ourSets.get(key);
                if (!java.util.Objects.equals(vanilla, ours)) {
                    mismatches++;
                    if (printed < 16) {
                        System.out.println(configName + " run=" + run + " at " + key + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println(configName + ": runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, configName + " block write mismatches");
        assertEquals(0, drawMismatchRuns, configName + " runs with diverging draw counts");
    }

    static int sculkFloorHeight(int x, int z, long salt) {
        return -45 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 20);
    }

    /** Deep-dark-like stone/deepslate cave: solid floor and ceiling with an air gap and occasional water. */
    static String sculkCaveState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319) {
            return null;
        }

        var floor = sculkFloorHeight(x, z, salt);
        var gap = 6 + FeatureABCompare.lattice(x, z, salt * 8 + 2, 10);
        var ceil = floor + 1 + gap;
        if (y <= floor || y >= ceil) {
            return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
        }

        if (y == floor + 1 && FeatureABCompare.lattice(x, z, salt * 8 + 3, 10) >= 8) {
            return "minecraft:water";
        }

        return null;
    }

    // --- root system -----------------------------------------------------------

    @Test
    void rootedAzaleaTree() throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/rooted_azalea_tree.json"))).getAsJsonObject();

        var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:rooted_azalea_tree")))
                .value();

        var feature = new RootSystemFeature();
        var previousLoader = Features.currentLoader();
        RootSystemConfiguration config;
        Features.currentLoader(loader);
        try {
            config = RootSystemConfiguration.fromJson(configJson.get("config"), loader.blockTags());
        } finally {
            Features.currentLoader(previousLoader);
        }

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 7000L + run;
            var seed = 975321L + run * 48611L;
            var x = -300 + run * 17;
            var z = 900 - run * 29;
            var capHeight = 4 + (run % 5);
            var originY = 0;

            var handler = new RootSystemLevel(capHeight);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    CavePatchABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, new BlockPos(x, originY, z));

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            var ourWorld = new OurRootSystemWorld(capHeight);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, originY, z), config, seed, -64, 319, 63);
            feature.place(context);

            var ourSets = new TreeMap<String, String>();
            for (var entry : ourWorld.overlay.entrySet()) {
                ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("rooted_azalea_tree run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            var keys = new TreeSet<String>();
            keys.addAll(vanillaSets.keySet());
            keys.addAll(ourSets.keySet());
            for (var key : keys) {
                var vanilla = vanillaSets.get(key);
                var ours = ourSets.get(key);
                if (!java.util.Objects.equals(vanilla, ours)) {
                    mismatches++;
                    if (printed < 16) {
                        System.out.println("rooted_azalea_tree run=" + run + " at " + key + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println("rooted_azalea_tree: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "rooted_azalea_tree block write mismatches");
        assertEquals(0, drawMismatchRuns, "rooted_azalea_tree runs with diverging draw counts");
    }

    /**
     * Uniform-per-Y stratum world: solid stone below the origin's air pocket,
     * a rooted-stone shaft, a moss cap ({@code azalea_grows_on}), then open
     * air for the nested azalea tree to grow into.
     */
    static String rootSystemState(int y, int capHeight) {
        if (y < 0) {
            return "minecraft:stone";
        }
        if (y == 0) {
            return null;
        }
        if (y < capHeight) {
            return "minecraft:stone";
        }
        if (y == capHeight) {
            return "minecraft:moss_block";
        }
        return null;
    }

    // --- underwater magma --------------------------------------------------

    @Test
    void underwaterMagma() throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/underwater_magma.json"))).getAsJsonObject();

        var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:underwater_magma")))
                .value();

        var feature = new UnderwaterMagmaFeature();
        var config = UnderwaterMagmaConfiguration.fromJson(configJson.get("config"));

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var seed = 314159L + run * 27551L;
            var x = 120 - run * 11;
            var z = -450 + run * 19;
            var floorHeight = -2 + (run % 3);
            var originY = floorHeight + 2 + (run % 4);

            var handler = new UnderwaterLevel(floorHeight);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    CavePatchABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, new BlockPos(x, originY, z));

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            var ourWorld = new OurUnderwaterWorld(floorHeight);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, originY, z), config, seed, -64, 319, 63);
            feature.place(context);

            var ourSets = new TreeMap<String, String>();
            for (var entry : ourWorld.overlay.entrySet()) {
                ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("underwater_magma run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            var keys = new TreeSet<String>();
            keys.addAll(vanillaSets.keySet());
            keys.addAll(ourSets.keySet());
            for (var key : keys) {
                var vanilla = vanillaSets.get(key);
                var ours = ourSets.get(key);
                if (!java.util.Objects.equals(vanilla, ours)) {
                    mismatches++;
                    if (printed < 16) {
                        System.out.println("underwater_magma run=" + run + " at " + key + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println("underwater_magma: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "underwater_magma block write mismatches");
        assertEquals(0, drawMismatchRuns, "underwater_magma runs with diverging draw counts");
    }

    /** Uniform-per-Y world: solid buried floor below, open water above. */
    static String underwaterMagmaState(int y, int floorHeight) {
        return y <= floorHeight ? "minecraft:sand" : "minecraft:water";
    }

    // --- shared vanilla level proxy ---------------------------------------------

    @FunctionalInterface
    interface CaveStateFunction {
        String stateAt(int x, int y, int z, long salt);
    }

    static final class CaveLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;
        final CaveStateFunction stateFunction;

        CaveLevel(long salt, CaveStateFunction stateFunction) {
            this.salt = salt;
            this.stateFunction = stateFunction;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = this.stateFunction.stateAt(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : FeatureABCompare.vanillaState(base);
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
                    var pos = ((BlockPos) args[0]).immutable();
                    this.overlay.put(pos, (BlockState) args[1]);
                    if (traceSets) {
                        System.out.println("VSET #" + (traceCounter++) + " " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                                + " " + FeatureABCompare.canonical((BlockState) args[1]));
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
                    return 384;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? java.util.Optional.empty() : null;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt", "playSound", "levelEvent" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "getChunk" -> {
                    return dummyChunk();
                }
                case "toString" -> {
                    return "cave-patch-ab-level";
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

    static final class OurCaveWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;
        final CaveStateFunction stateFunction;

        OurCaveWorld(long salt, CaveStateFunction stateFunction) {
            this.salt = salt;
            this.stateFunction = stateFunction;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = this.stateFunction.stateAt(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
            if (traceSets) {
                System.out.println("OSET #" + (traceCounter++) + " " + x + "," + y + "," + z + " " + FeatureABCompare.canonical(block));
            }
        }
    }

    // --- root system level proxies ----------------------------------------------

    static final class RootSystemLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final int capHeight;

        RootSystemLevel(int capHeight) {
            this.capHeight = capHeight;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = rootSystemState(pos.getY(), this.capHeight);
            return base == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : FeatureABCompare.vanillaState(base);
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
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "removeBlock", "destroyBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "isFluidAtPosition" -> {
                    return ((java.util.function.Predicate<net.minecraft.world.level.material.FluidState>) args[1])
                            .test(this.state((BlockPos) args[0]).getFluidState());
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
                    return 320;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "getChunkGenerator" -> {
                    return generator;
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? java.util.Optional.empty() : null;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt", "playSound", "levelEvent" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "getChunk" -> {
                    return dummyChunk();
                }
                case "toString" -> {
                    return "root-system-ab-level";
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

    static final class OurRootSystemWorld implements Block.Getter, Block.Setter, rocks.minestom.worldgen.feature.LargeDripstoneFeature.WorldSurface {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final int capHeight;

        OurRootSystemWorld(int capHeight) {
            this.capHeight = capHeight;
        }

        @Override
        public int worldSurfaceHeight(int x, int z) {
            return 300;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = rootSystemState(y, this.capHeight);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- underwater magma level proxies -----------------------------------------

    static final class UnderwaterLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final int floorHeight;

        UnderwaterLevel(int floorHeight) {
            this.floorHeight = floorHeight;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            return FeatureABCompare.vanillaState(underwaterMagmaState(pos.getY(), this.floorHeight));
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
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "removeBlock", "destroyBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
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
                    return 384;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "getBlockEntity" -> {
                    return args != null && args.length > 1 ? java.util.Optional.empty() : null;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt", "playSound", "levelEvent" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "getChunk" -> {
                    return dummyChunk();
                }
                case "toString" -> {
                    return "underwater-magma-ab-level";
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

    static final class OurUnderwaterWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final int floorHeight;

        OurUnderwaterWorld(int floorHeight) {
            this.floorHeight = floorHeight;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            return FeatureABCompare.ourState(underwaterMagmaState(y, this.floorHeight));
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
