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
import rocks.minestom.worldgen.feature.BlueIceFeature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.IceSpikeFeature;
import rocks.minestom.worldgen.feature.IcebergFeature;
import rocks.minestom.worldgen.feature.configurations.IcebergConfiguration;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

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
 * A/B compares the ice feature ports ({@code minecraft:iceberg},
 * {@code minecraft:blue_ice}, {@code minecraft:ice_spike}) against real
 * vanilla 26.2 code in process: both sides run over identical synthetic
 * worlds from identical random sequences, and every block write plus the
 * total draw count must match.
 *
 * <p>Vanilla 26.2 no longer ships a distinct {@code IceSpikeFeature} class;
 * the {@code minecraft:ice_spike} configured feature is generated today by
 * the generic {@code minecraft:spike} feature configured with a packed ice
 * state, a snow block placement surface, and the
 * {@code #minecraft:ice_spike_replaceable} block tag. The ice spike case
 * below therefore compares our {@link IceSpikeFeature} (which hardcodes
 * that exact configuration) against the real vanilla {@code minecraft:spike}
 * feature, looked up as the actual {@code minecraft:ice_spike} configured
 * feature from vanilla's registries: this is the genuine vanilla code path
 * that produces ice spikes today.
 */
final class IceFeaturesABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 300;
    private static final int SEA_LEVEL = 63;

    private static net.minecraft.core.HolderLookup.Provider registries;
    private static net.minecraft.world.level.chunk.ChunkGenerator generator;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        registries = net.minecraft.data.registries.VanillaRegistries.createLookup();

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
    }

    @Test
    void icebergPacked() throws Exception {
        runIcebergCase("iceberg_packed");
    }

    @Test
    void icebergBlue() throws Exception {
        runIcebergCase("iceberg_blue");
    }

    @Test
    void blueIce() throws Exception {
        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;

        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:blue_ice")))
                .value();

        for (var run = 0; run < RUNS; run++) {
            var salt = 9000L + run;
            var seed = 13579L + run * 92821L;
            var x = -1500 + run * 37;
            var z = 2200 - run * 53;
            var y = SEA_LEVEL - 1 - (run % 12);
            var origin = new BlockPos(x, y, z);

            var handler = new OceanLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    IceFeaturesABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, origin);

            var vanillaSets = toStringMap(handler.overlay);

            var ourWorld = new OurOceanWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, y, z),
                    new NoneFeatureConfiguration(), seed, -64, 319, SEA_LEVEL);
            new BlueIceFeature().place(context);

            var ourSets = toOurStringMap(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("blue_ice run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            printed = compareSets(vanillaSets, ourSets, "blue_ice", run, printed);
            mismatches += countMismatches(vanillaSets, ourSets);
        }

        System.out.println("blue_ice: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "blue_ice block write mismatches");
        assertEquals(0, drawMismatchRuns, "blue_ice runs with diverging draw counts");
    }

    @Test
    void iceSpike() throws Exception {
        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;

        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:ice_spike")))
                .value();

        for (var run = 0; run < RUNS; run++) {
            var salt = 4000L + run;
            var seed = 224466L + run * 65537L;
            var x = -900 + run * 29;
            var z = 1300 - run * 41;
            var surfaceY = 70;
            var originY = surfaceY + 4;
            var origin = new BlockPos(x, originY, z);

            var handler = new SnowFieldLevel(salt, surfaceY);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    IceFeaturesABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, origin);

            var vanillaSets = toStringMap(handler.overlay);

            var ourWorld = new OurSnowFieldWorld(salt, surfaceY);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, originY, z),
                    new NoneFeatureConfiguration(), seed, -64, 319, SEA_LEVEL);
            new IceSpikeFeature().place(context);

            var ourSets = toOurStringMap(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println("ice_spike run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            printed = compareSets(vanillaSets, ourSets, "ice_spike", run, printed);
            mismatches += countMismatches(vanillaSets, ourSets);
        }

        System.out.println("ice_spike: runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, "ice_spike block write mismatches");
        assertEquals(0, drawMismatchRuns, "ice_spike runs with diverging draw counts");
    }

    // --- shared driver for the iceberg cases (both use minecraft:iceberg) ---

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runIcebergCase(String configName) throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/" + configName + ".json"))).getAsJsonObject();
        var ourConfig = IcebergConfiguration.fromJson(configJson.get("config"));

        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;

        for (var run = 0; run < RUNS; run++) {
            var salt = 7000L + run;
            var seed = 445566L + run * 786433L;
            var x = -1200 + run * 43;
            var z = 1800 - run * 61;
            var origin = new BlockPos(x, 0, z);

            var handler = new OceanLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    IceFeaturesABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, origin);

            var vanillaSets = toStringMap(handler.overlay);

            var ourWorld = new OurOceanWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, 0, z),
                    ourConfig, seed, -64, 319, SEA_LEVEL);
            new IcebergFeature().place(context);

            var ourSets = toOurStringMap(ourWorld.overlay);

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println(configName + " run=" + run + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            printed = compareSets(vanillaSets, ourSets, configName, run, printed);
            mismatches += countMismatches(vanillaSets, ourSets);
        }

        System.out.println(configName + ": runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, configName + " block write mismatches");
        assertEquals(0, drawMismatchRuns, configName + " runs with diverging draw counts");
    }

    // --- comparison helpers -------------------------------------------------

    private static TreeMap<String, String> toStringMap(Map<BlockPos, BlockState> overlay) {
        var result = new TreeMap<String, String>();
        for (var entry : overlay.entrySet()) {
            result.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                    FeatureABCompare.canonical(entry.getValue()));
        }
        return result;
    }

    private static TreeMap<String, String> toOurStringMap(java.util.Map<BlockVec, Block> overlay) {
        var result = new TreeMap<String, String>();
        for (var entry : overlay.entrySet()) {
            result.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                    FeatureABCompare.canonical(entry.getValue()));
        }
        return result;
    }

    private static int compareSets(TreeMap<String, String> vanillaSets, TreeMap<String, String> ourSets,
            String name, int run, int printed) {
        var keys = new TreeSet<String>();
        keys.addAll(vanillaSets.keySet());
        keys.addAll(ourSets.keySet());
        for (var key : keys) {
            var vanilla = vanillaSets.get(key);
            var ours = ourSets.get(key);
            if (!java.util.Objects.equals(vanilla, ours) && printed < 24) {
                System.out.println(name + " run=" + run + " at " + key + " vanilla=" + vanilla + " ours=" + ours);
                printed++;
            }
        }
        return printed;
    }

    private static long countMismatches(TreeMap<String, String> vanillaSets, TreeMap<String, String> ourSets) {
        var keys = new TreeSet<String>();
        keys.addAll(vanillaSets.keySet());
        keys.addAll(ourSets.keySet());
        var mismatches = 0L;
        for (var key : keys) {
            if (!java.util.Objects.equals(vanillaSets.get(key), ourSets.get(key))) {
                mismatches++;
            }
        }
        return mismatches;
    }

    // --- synthetic frozen ocean world: water up to sea level, gravel floor,
    // and smooth "pre-existing iceberg" packed ice patches near the surface --

    static int oceanFloor(int x, int z, long salt) {
        return 40 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 8);
    }

    static boolean bergMask(int x, int z, long salt) {
        return FeatureABCompare.lattice(x, z, salt * 8 + 2, 10) >= 6;
    }

    static String oceanState(int x, int y, int z, long salt) {
        if (y > SEA_LEVEL) {
            return null;
        }

        if (bergMask(x, z, salt) && y >= 55 && y <= 61) {
            return "minecraft:packed_ice";
        }

        var floor = oceanFloor(x, z, salt);
        if (y > floor) {
            return "minecraft:water";
        }

        return y == floor ? "minecraft:gravel" : "minecraft:stone";
    }

    static final class OceanLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        OceanLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = oceanState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : FeatureABCompare.vanillaState(base);
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
                case "isWaterAt" -> {
                    return this.state((BlockPos) args[0]).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
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
                    return SEA_LEVEL;
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
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "getChunk" -> {
                    return null;
                }
                case "toString" -> {
                    return "ice-ab-ocean-level";
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

    static final class OurOceanWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurOceanWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = oceanState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- synthetic snow field world: flat snow block surface over solid
    // stone, open air above, used for the ice spike case ------------------

    static String snowFieldState(int x, int y, int z, long salt, int surfaceY) {
        if (y > surfaceY) {
            return null;
        }

        if (y == surfaceY) {
            return "minecraft:snow_block";
        }

        return y >= surfaceY - 20 ? "minecraft:dirt" : "minecraft:stone";
    }

    static final class SnowFieldLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;
        final int surfaceY;

        SnowFieldLevel(long salt, int surfaceY) {
            this.salt = salt;
            this.surfaceY = surfaceY;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = snowFieldState(pos.getX(), pos.getY(), pos.getZ(), this.salt, this.surfaceY);
            return base == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : FeatureABCompare.vanillaState(base);
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
                    return SEA_LEVEL;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "toString" -> {
                    return "ice-ab-snowfield-level";
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

    static final class OurSnowFieldWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;
        final int surfaceY;

        OurSnowFieldWorld(long salt, int surfaceY) {
            this.salt = salt;
            this.surfaceY = surfaceY;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = snowFieldState(x, y, z, this.salt, this.surfaceY);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
