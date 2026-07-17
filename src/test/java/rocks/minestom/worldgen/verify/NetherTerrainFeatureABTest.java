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
import rocks.minestom.worldgen.feature.BasaltColumnsFeature;
import rocks.minestom.worldgen.feature.BasaltPillarFeature;
import rocks.minestom.worldgen.feature.DeltaFeature;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.GlowstoneFeature;
import rocks.minestom.worldgen.feature.ReplaceBlobsFeature;
import rocks.minestom.worldgen.feature.SpringFeature;
import rocks.minestom.worldgen.feature.configurations.ColumnFeatureConfiguration;
import rocks.minestom.worldgen.feature.configurations.DeltaFeatureConfiguration;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.feature.configurations.ReplaceSphereConfiguration;
import rocks.minestom.worldgen.feature.configurations.SpringConfiguration;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A/B compares the nether terrain features (springs, glowstone blobs, basalt
 * columns/pillars, deltas, replace blobs) against real vanilla code over a
 * synthetic nether world: identical configs parsed from the datapack JSON,
 * identical feature randoms, asserting identical block writes and draw counts
 * for a few hundred seeds each.
 */
final class NetherTerrainFeatureABTest {
    private static final int MIN_Y = 0;
    private static final int MAX_Y = 127;
    private static final int LAVA_SEA_LEVEL = 32;
    private static final int RUNS = 300;
    private static final Path CONFIGURED_FEATURES =
            Path.of("data/mc/datapack/data/minecraft/worldgen/configured_feature");

    private static net.minecraft.world.level.chunk.ChunkGenerator generator;
    private static com.mojang.serialization.DynamicOps<com.google.gson.JsonElement> registryOps;
    private static BlockTagManager blockTags;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        registryOps = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, lookup);

        var presets = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var netherPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:nether")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(netherPreset);
        var noiseSettings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:nether")));
        generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseSettings);
        assertEquals(LAVA_SEA_LEVEL, generator.getSeaLevel(), "nether generator sea level");

        blockTags = new BlockTagManager(Path.of("data/mc/datapack"));
    }

    // --- synthetic nether world ---------------------------------------------

    static int floorHeight(int x, int z, long salt) {
        var floor = 24 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 16);
        // single-column jitter so deltas find one-deep pockets
        if ((FeatureABCompare.hash(x, z, salt * 8 + 9) & 3) == 0) {
            floor++;
        }
        return floor;
    }

    static int ceilingHeight(int x, int z, long salt) {
        return 96 + FeatureABCompare.lattice(x, z, salt * 8 + 2, 20);
    }

    /** Canonical block id at a position, null for air. */
    static String netherBase(int x, int y, int z, long salt) {
        if (y < MIN_Y || y > MAX_Y) {
            return null;
        }

        if (y <= 3 || y >= 124) {
            return "minecraft:netherrack";
        }

        var floor = floorHeight(x, z, salt);
        var ceiling = ceilingHeight(x, z, salt);
        if (y <= floor) {
            if (y == floor) {
                // occasional non-netherrack floor caps (basalt columns and
                // deltas treat several of these specially)
                var cap = FeatureABCompare.lattice(x, z, salt * 8 + 3, 20);
                if (cap >= 18) {
                    return "minecraft:soul_sand";
                }
                if (cap >= 16) {
                    return "minecraft:magma_block";
                }
                if (cap >= 14) {
                    return "minecraft:blackstone";
                }
                if (cap >= 12) {
                    return "minecraft:basalt";
                }
            }
            return "minecraft:netherrack";
        }

        if (y >= ceiling) {
            return "minecraft:netherrack";
        }

        // lava ocean up to the nether sea level
        if (y <= LAVA_SEA_LEVEL - 1) {
            return "minecraft:lava";
        }

        // occasional one-block hanging shelf below the ceiling
        if (y == ceiling - 4 && FeatureABCompare.lattice(x, z, salt * 8 + 4, 12) >= 10) {
            return "minecraft:netherrack";
        }

        return null;
    }

    // --- vanilla side ---------------------------------------------------------

    static final class NetherHandler implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        NetherHandler(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = netherBase(pos.getX(), pos.getY(), pos.getZ(), this.salt);
            return base == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                    : FeatureABCompare.vanillaState(base);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.state((BlockPos) args[0]);
                }
                case "getFluidState" -> {
                    return this.state((BlockPos) args[0]).getFluidState();
                }
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    return true;
                }
                case "isEmptyBlock" -> {
                    return this.state((BlockPos) args[0]).isAir();
                }
                case "isOutsideBuildHeight" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    return y < MIN_Y || y > MAX_Y;
                }
                case "getMinY" -> {
                    return MIN_Y;
                }
                case "getMaxY" -> {
                    return MAX_Y;
                }
                case "getHeight" -> {
                    return MAX_Y - MIN_Y + 1;
                }
                case "getSeaLevel" -> {
                    return LAVA_SEA_LEVEL;
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
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

    // --- our side ---------------------------------------------------------------

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

            var base = netherBase(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- cases ---------------------------------------------------------------------

    /** How the per-run origin y is chosen relative to the synthetic terrain. */
    private enum OriginY {
        NEAR_FLOOR,
        ABOVE_FLOOR,
        UNDER_CEILING,
        MID_AIR
    }

    @Test
    void springLavaNether() throws Exception {
        runCase("spring_lava_nether", OriginY.NEAR_FLOOR);
    }

    @Test
    void springNetherOpen() throws Exception {
        runCase("spring_nether_open", OriginY.NEAR_FLOOR);
    }

    @Test
    void springNetherClosed() throws Exception {
        runCase("spring_nether_closed", OriginY.NEAR_FLOOR);
    }

    @Test
    void glowstoneExtra() throws Exception {
        runCase("glowstone_extra", OriginY.UNDER_CEILING);
    }

    @Test
    void smallBasaltColumns() throws Exception {
        runCase("small_basalt_columns", OriginY.ABOVE_FLOOR);
    }

    @Test
    void largeBasaltColumns() throws Exception {
        runCase("large_basalt_columns", OriginY.ABOVE_FLOOR);
    }

    @Test
    void basaltPillar() throws Exception {
        runCase("basalt_pillar", OriginY.UNDER_CEILING);
    }

    @Test
    void delta() throws Exception {
        runCase("delta", OriginY.ABOVE_FLOOR);
    }

    @Test
    void basaltBlobs() throws Exception {
        runCase("basalt_blobs", OriginY.MID_AIR);
    }

    @Test
    void blackstoneBlobs() throws Exception {
        runCase("blackstone_blobs", OriginY.MID_AIR);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runCase(String configName, OriginY originY) throws Exception {
        var json = JsonParser.parseString(Files.readString(CONFIGURED_FEATURES.resolve(configName + ".json")))
                .getAsJsonObject();
        var type = json.get("type").getAsString();
        var configJson = json.get("config");

        net.minecraft.world.level.levelgen.feature.Feature vanillaFeature;
        Object vanillaConfig;
        Feature ourFeature;
        Object ourConfig;
        switch (type) {
            case "minecraft:spring_feature" -> {
                vanillaFeature = net.minecraft.world.level.levelgen.feature.Feature.SPRING;
                vanillaConfig = net.minecraft.world.level.levelgen.feature.configurations.SpringConfiguration.CODEC
                        .parse(registryOps, configJson).getOrThrow();
                ourFeature = new SpringFeature();
                ourConfig = SpringConfiguration.fromJson(configJson, blockTags);
            }
            case "minecraft:glowstone_blob" -> {
                vanillaFeature = net.minecraft.world.level.levelgen.feature.Feature.GLOWSTONE_BLOB;
                vanillaConfig = net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE;
                ourFeature = new GlowstoneFeature();
                ourConfig = new NoneFeatureConfiguration();
            }
            case "minecraft:basalt_columns" -> {
                vanillaFeature = net.minecraft.world.level.levelgen.feature.Feature.BASALT_COLUMNS;
                vanillaConfig = net.minecraft.world.level.levelgen.feature.configurations.ColumnFeatureConfiguration.CODEC
                        .parse(registryOps, configJson).getOrThrow();
                ourFeature = new BasaltColumnsFeature();
                ourConfig = ColumnFeatureConfiguration.fromJson(configJson);
            }
            case "minecraft:basalt_pillar" -> {
                vanillaFeature = net.minecraft.world.level.levelgen.feature.Feature.BASALT_PILLAR;
                vanillaConfig = net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE;
                ourFeature = new BasaltPillarFeature();
                ourConfig = new NoneFeatureConfiguration();
            }
            case "minecraft:delta_feature" -> {
                vanillaFeature = net.minecraft.world.level.levelgen.feature.Feature.DELTA_FEATURE;
                vanillaConfig = net.minecraft.world.level.levelgen.feature.configurations.DeltaFeatureConfiguration.CODEC
                        .parse(registryOps, configJson).getOrThrow();
                ourFeature = new DeltaFeature();
                ourConfig = DeltaFeatureConfiguration.fromJson(configJson);
            }
            case "minecraft:netherrack_replace_blobs" -> {
                vanillaFeature = net.minecraft.world.level.levelgen.feature.Feature.REPLACE_BLOBS;
                vanillaConfig = net.minecraft.world.level.levelgen.feature.configurations.ReplaceSphereConfiguration.CODEC
                        .parse(registryOps, configJson).getOrThrow();
                ourFeature = new ReplaceBlobsFeature();
                ourConfig = ReplaceSphereConfiguration.fromJson(configJson);
            }
            default -> throw new IllegalArgumentException("unhandled type: " + type);
        }

        var totalSets = 0L;
        var totalPlacedRuns = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 5000L + run;
            var featureSeed = 246813579L + run * 7919L;
            var positionHash = FeatureABCompare.hash(run, 31L * run + 17, 424242L);
            var x = (int) (positionHash & 1023) - 512;
            var z = (int) ((positionHash >>> 10) & 1023) - 512;
            var yBits = (int) ((positionHash >>> 20) & 7);
            var y = switch (originY) {
                case NEAR_FLOOR -> floorHeight(x, z, salt) + yBits - 3;
                case ABOVE_FLOOR -> floorHeight(x, z, salt) + 1;
                case UNDER_CEILING -> ceilingHeight(x, z, salt) - 1 - (yBits >= 6 ? 4 : yBits / 3);
                case MID_AIR -> floorHeight(x, z, salt) + 10;
            };

            // vanilla
            var handler = new NetherHandler(salt);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    NetherTerrainFeatureABTest.class.getClassLoader(),
                    new Class<?>[]{WorldGenLevel.class},
                    handler);
            var vanillaCounted = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(0L));
            var vanillaRandom = new net.minecraft.world.level.levelgen.WorldgenRandom(vanillaCounted);
            vanillaRandom.setFeatureSeed(featureSeed, 0, 7);
            vanillaFeature.place(
                    (net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration) vanillaConfig,
                    level, generator, vanillaRandom, new BlockPos(x, y, z));

            // ours
            var ourWorld = new OurNetherWorld(salt);
            var ourCounted = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(0L));
            var ourRandom = new rocks.minestom.worldgen.random.WorldgenRandom(ourCounted);
            ourRandom.setFeatureSeed(featureSeed, 0, 7);
            var context = new FeaturePlaceContext(ourWorld, ourRandom, new BlockVec(x, y, z),
                    (rocks.minestom.worldgen.feature.FeatureConfiguration) ourConfig,
                    featureSeed, MIN_Y, MAX_Y, LAVA_SEA_LEVEL);
            ourFeature.place(context);

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            var ourSets = new TreeMap<String, String>();
            for (var entry : ourWorld.overlay.entrySet()) {
                ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            assertEquals(vanillaSets, ourSets,
                    configName + " run=" + run + " origin=(" + x + "," + y + "," + z + ") block writes");
            assertEquals(vanillaCounted.count, ourCounted.count,
                    configName + " run=" + run + " origin=(" + x + "," + y + "," + z + ") draw count");

            totalSets += vanillaSets.size();
            if (!vanillaSets.isEmpty()) {
                totalPlacedRuns++;
            }
        }

        System.out.println(configName + ": runs=" + RUNS + " placedRuns=" + totalPlacedRuns + " sets=" + totalSets);
        assertTrue(totalSets > 0, configName + " never placed anything over " + RUNS + " runs");
    }
}
