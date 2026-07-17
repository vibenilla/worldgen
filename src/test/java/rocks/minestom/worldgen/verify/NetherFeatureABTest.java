package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.HugeFungusFeature;
import rocks.minestom.worldgen.feature.NetherForestVegetationFeature;
import rocks.minestom.worldgen.feature.TwistingVinesFeature;
import rocks.minestom.worldgen.feature.VinesFeature;
import rocks.minestom.worldgen.feature.WeepingVinesFeature;
import rocks.minestom.worldgen.feature.configurations.HugeFungusConfiguration;
import rocks.minestom.worldgen.feature.configurations.NetherForestVegetationConfig;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.feature.configurations.TwistingVinesConfig;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

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
 * A/B compares the nether feature ports (huge fungus, nether forest
 * vegetation, twisting/weeping vines, vines) against real vanilla 26.2 code in
 * process: both sides run over an identical synthetic nether cave (netherrack
 * floor and roof, nylium/wart/soul-soil caps, scattered plants) from identical
 * random sequences, and every block write plus the total draw count must match.
 */
final class NetherFeatureABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 300;

    private static Object lookup;
    private static net.minecraft.world.level.chunk.ChunkGenerator generator;
    private static BlockTagManager blockTags;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        lookup = registries;

        var presets = registries.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var netherPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:nether")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(netherPreset);
        var noiseSettings = registries.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:nether")));
        generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseSettings);

        blockTags = new BlockTagManager(ROOT);
    }

    @Test
    void hugeFungus() throws Exception {
        runCase("crimson_fungus", OriginMode.FLOOR);
        runCase("warped_fungus", OriginMode.FLOOR);
    }

    @Test
    void netherForestVegetation() throws Exception {
        runCase("crimson_forest_vegetation", OriginMode.FLOOR);
        runCase("warped_forest_vegetation", OriginMode.FLOOR);
        runCase("nether_sprouts", OriginMode.FLOOR);
    }

    @Test
    void twistingVines() throws Exception {
        runCase("twisting_vines", OriginMode.FLOOR);
    }

    @Test
    void weepingVines() throws Exception {
        runCase("weeping_vines", OriginMode.CEILING);
    }

    @Test
    void vines() throws Exception {
        runCase("vines", OriginMode.ALTERNATE);
    }

    private enum OriginMode {
        FLOOR, CEILING, ALTERNATE
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runCase(String configName, OriginMode originMode) throws Exception {
        var configJson = JsonParser.parseString(Files.readString(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/" + configName + ".json"))).getAsJsonObject();
        var typeName = configJson.get("type").getAsString();

        var registries = (net.minecraft.core.HolderLookup.Provider) lookup;
        var vanillaConfigured = registries.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();

        Feature feature;
        rocks.minestom.worldgen.feature.FeatureConfiguration config;
        switch (typeName) {
            case "minecraft:huge_fungus" -> {
                feature = new HugeFungusFeature();
                config = HugeFungusConfiguration.fromJson(configJson.get("config"), blockTags);
            }
            case "minecraft:nether_forest_vegetation" -> {
                feature = new NetherForestVegetationFeature();
                config = NetherForestVegetationConfig.CODEC.decode(Transcoder.JSON, configJson.get("config")).orElseThrow();
            }
            case "minecraft:twisting_vines" -> {
                feature = new TwistingVinesFeature();
                config = TwistingVinesConfig.CODEC.decode(Transcoder.JSON, configJson.get("config")).orElseThrow();
            }
            case "minecraft:weeping_vines" -> {
                feature = new WeepingVinesFeature();
                config = new NoneFeatureConfiguration();
            }
            case "minecraft:vines" -> {
                feature = new VinesFeature();
                config = new NoneFeatureConfiguration();
            }
            default -> throw new IllegalStateException("unexpected feature type " + typeName);
        }

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 5000L + run;
            var seed = 24680L + run * 104729L;
            var x = -2000 + run * 31;
            var z = 3000 - run * 47;
            var floor = floorHeight(x, z, salt);
            var ceiling = originMode == OriginMode.CEILING
                    || (originMode == OriginMode.ALTERNATE && run % 2 == 0);
            var originY = ceiling ? ceilHeight(x, z, salt) - 1 : floor + 1;

            // vanilla
            var handler = new NetherLevel(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    NetherFeatureABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaConfigured.place(level, generator, vanillaRandom, new BlockPos(x, originY, z));

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            // ours
            var ourWorld = new OurNetherWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext(ourWorld, ourRandom, new BlockVec(x, originY, z), config, seed, 0, 127, 32);
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
                    System.out.println(configName + " run=" + run + " DRAWS vanilla=" + vanillaRandom.count
                            + " ours=" + ourRandom.count);
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
                        System.out.println(configName + " run=" + run + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
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

    // --- synthetic nether cave ---------------------------------------------

    static int floorHeight(int x, int z, long salt) {
        return 28 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 14);
    }

    static int ceilHeight(int x, int z, long salt) {
        return floorHeight(x, z, salt) + 7 + FeatureABCompare.lattice(x, z, salt * 8 + 2, 24);
    }

    /** Canonical block id at a position, null for air. */
    static String baseNetherState(int x, int y, int z, long salt) {
        if (y < 0 || y > 127) {
            return null;
        }

        var floor = floorHeight(x, z, salt);
        var ceil = ceilHeight(x, z, salt);
        if (y >= ceil) {
            if (y == ceil && FeatureABCompare.lattice(x, z, salt * 8 + 6, 10) >= 8) {
                return "minecraft:nether_wart_block";
            }
            return "minecraft:netherrack";
        }

        if (y <= floor) {
            if (y == floor) {
                var cap = FeatureABCompare.lattice(x, z, salt * 8 + 3, 20);
                if (cap >= 16) {
                    return "minecraft:crimson_nylium";
                }
                if (cap >= 12) {
                    return "minecraft:warped_nylium";
                }
                if (cap >= 10) {
                    return "minecraft:warped_wart_block";
                }
                if (cap >= 9) {
                    return "minecraft:soul_soil";
                }
            }
            return "minecraft:netherrack";
        }

        // scattered pre-existing vegetation on the floor: replaceable roots and
        // sprouts, plus non-replaceable fungi covered by replaceable_blocks
        if (y == floor + 1) {
            var plant = FeatureABCompare.lattice(x, z, salt * 8 + 4, 40);
            if (plant >= 38) {
                return "minecraft:crimson_roots";
            }
            if (plant >= 36) {
                return "minecraft:warped_roots";
            }
            if (plant >= 34) {
                return "minecraft:nether_sprouts";
            }
            if (plant >= 32) {
                return "minecraft:crimson_fungus";
            }
            if (plant >= 30) {
                return "minecraft:fire";
            }
        }

        return null;
    }

    // --- vanilla level proxy -------------------------------------------------

    static final class NetherLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        NetherLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = baseNetherState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
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
                    this.overlay.put(((BlockPos) args[0]).immutable(),
                            net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "isOutsideBuildHeight" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    return y < 0 || y > 255;
                }
                case "getMinY" -> {
                    return 0;
                }
                case "getMaxY" -> {
                    return 255;
                }
                case "getHeight" -> {
                    return 256;
                }
                case "getSeaLevel" -> {
                    return 32;
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
                    return "nether-ab-level";
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

    // --- our side --------------------------------------------------------------

    static final class OurNetherWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurNetherWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseNetherState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
