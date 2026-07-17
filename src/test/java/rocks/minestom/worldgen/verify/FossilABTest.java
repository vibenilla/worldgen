package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.FossilFeature;
import rocks.minestom.worldgen.feature.configurations.FossilFeatureConfiguration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the fossil feature port against real vanilla 26.2 code in
 * process: both sides run over an identical synthetic desert/swamp
 * underground world (a lattice-noise stone floor with occasional buried air,
 * water and lava pockets) from identical random sequences, and every block
 * write plus the total draw count on the shared random must match.
 *
 * <p>Vanilla's {@code FossilFeature.place} fetches its
 * {@code StructureTemplateManager} through {@code level.getLevel().getServer()},
 * which needs a real {@code ServerLevel}/{@code MinecraftServer} pair that
 * cannot reasonably be constructed in a unit test. Everything else in the
 * feature - rotation, template geometry, corner rejection, and template
 * placement (real vanilla {@code StructureTemplate.placeInWorld} and its real
 * {@code BlockRotProcessor}/{@code RuleProcessor}/{@code ProtectedBlockProcessor})
 * only needs a {@code ServerLevelAccessor}, an interface, so this test
 * replicates the feature's orchestration by hand with real vanilla helper
 * calls and a hand-loaded real {@code StructureTemplate} (bypassing only the
 * manager lookup, not the NBT parsing or placement logic) and a real
 * {@code StructureProcessorList} resolved from the vanilla worldgen registry.
 */
final class FossilABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 300;

    private static final List<String> FOSSIL_NAMES = List.of(
            "spine_1", "spine_2", "spine_3", "spine_4", "skull_1", "skull_2", "skull_3", "skull_4");

    private static Map<String, StructureTemplate> vanillaTemplates;
    private static StructureProcessorList vanillaFossilRot;
    private static StructureProcessorList vanillaFossilCoal;
    private static StructureProcessorList vanillaFossilDiamonds;

    private static FeatureLoader ourFeatureLoader;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var registries = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var processorLists = registries.lookupOrThrow(net.minecraft.core.registries.Registries.PROCESSOR_LIST);
        vanillaFossilRot = processorLists.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.PROCESSOR_LIST, Identifier.parse("minecraft:fossil_rot"))).value();
        vanillaFossilCoal = processorLists.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.PROCESSOR_LIST, Identifier.parse("minecraft:fossil_coal"))).value();
        vanillaFossilDiamonds = processorLists.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.PROCESSOR_LIST, Identifier.parse("minecraft:fossil_diamonds"))).value();

        vanillaTemplates = new HashMap<>();
        for (var name : FOSSIL_NAMES) {
            vanillaTemplates.put(name, loadVanillaTemplate(name));
            vanillaTemplates.put(name + "_coal", loadVanillaTemplate(name + "_coal"));
        }

        ourFeatureLoader = new FeatureLoader(new DataPack(ROOT));
    }

    @Test
    void fossilCoal() {
        runCase("fossil_coal", vanillaFossilCoal);
    }

    @Test
    void fossilDiamonds() {
        runCase("fossil_diamonds", vanillaFossilDiamonds);
    }

    private static void runCase(String configName, StructureProcessorList vanillaOverlayProcessors) {
        var configJson = JsonParser.parseString(readFile(
                ROOT.resolve("data/minecraft/worldgen/configured_feature/" + configName + ".json"))).getAsJsonObject();

        Features.currentLoader(ourFeatureLoader);
        FossilFeatureConfiguration ourConfig;
        try {
            ourConfig = FossilFeatureConfiguration.fromJson(configJson.get("config"), ourFeatureLoader.blockTags());
        } finally {
            Features.currentLoader(null);
        }

        var vanillaFossilTemplates = FOSSIL_NAMES.stream().map(vanillaTemplates::get).toList();
        var vanillaOverlayTemplates = FOSSIL_NAMES.stream().map(name -> vanillaTemplates.get(name + "_coal")).toList();

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        for (var run = 0; run < RUNS; run++) {
            var salt = 7000L + run;
            var seed = 13579L + run * 104729L;
            var x = -1500 + run * 27;
            var z = 2200 - run * 41;
            var floor = floorHeight(x, z, salt);
            var originY = floor + 20 + (run % 5);

            // vanilla
            var handler = new FossilLevel(salt);
            var levelProxy = Proxy.newProxyInstance(
                    FossilABTest.class.getClassLoader(),
                    new Class<?>[]{ServerLevelAccessor.class},
                    handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            vanillaFossilPlace(levelProxy, vanillaRandom, new BlockPos(x, originY, z),
                    vanillaFossilTemplates, vanillaOverlayTemplates, vanillaFossilRot, vanillaOverlayProcessors,
                    4, -64, salt);

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            // ours
            var ourWorld = new OurFossilWorld(salt);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<>(ourWorld, ourRandom, new BlockVec(x, originY, z), ourConfig,
                    seed, -64, 319, 0);
            new FossilFeature().place(context);

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

    /**
     * Hand port of vanilla {@code FossilFeature.place}, using real vanilla
     * rotation, structure template and structure processor code, but skipping
     * the {@code StructureTemplateManager} lookup (the templates and
     * processor lists are supplied directly, already resolved the same way
     * vanilla's registries and NBT loader would resolve them).
     */
    private static boolean vanillaFossilPlace(
            Object levelProxy, net.minecraft.util.RandomSource random, BlockPos origin,
            List<StructureTemplate> fossilTemplates, List<StructureTemplate> overlayTemplates,
            StructureProcessorList fossilProcessors, StructureProcessorList overlayProcessors,
            int maxEmptyCornersAllowed, int minY, long salt) {
        var level = (ServerLevelAccessor) levelProxy;
        var rotation = Rotation.getRandom(random);
        var fossilIndex = random.nextInt(fossilTemplates.size());
        var fossilBase = fossilTemplates.get(fossilIndex);
        var fossilOverlay = overlayTemplates.get(fossilIndex);

        var size = fossilBase.getSize(rotation);
        var lowCorner = origin.offset(-size.getX() / 2, 0, -size.getZ() / 2);
        var lowestSurfaceY = origin.getY();
        for (var xscan = 0; xscan < size.getX(); xscan++) {
            for (var zscan = 0; zscan < size.getZ(); zscan++) {
                lowestSurfaceY = Math.min(lowestSurfaceY,
                        oceanFloorHeight(lowCorner.getX() + xscan, lowCorner.getZ() + zscan, salt));
            }
        }

        var targetY = Math.max(lowestSurfaceY - 15 - random.nextInt(10), minY + 10);
        var targetPos = fossilBase.getZeroPositionWithTransform(
                new BlockPos(lowCorner.getX(), targetY, lowCorner.getZ()), Mirror.NONE, rotation);

        var cornerBox = fossilBase.getBoundingBox(targetPos, rotation, BlockPos.ZERO, Mirror.NONE);
        if (countEmptyCorners(level, cornerBox) > maxEmptyCornersAllowed) {
            return false;
        }

        var settings = new StructurePlaceSettings().setRotation(rotation).setRandom(random).setKnownShape(true);
        settings.clearProcessors();
        for (var processor : fossilProcessors.list()) {
            settings.addProcessor(processor);
        }
        fossilBase.placeInWorld(level, targetPos, targetPos, settings, random, 260);

        settings.clearProcessors();
        for (var processor : overlayProcessors.list()) {
            settings.addProcessor(processor);
        }
        fossilOverlay.placeInWorld(level, targetPos, targetPos, settings, random, 260);
        return true;
    }

    /** Vanilla {@code FossilFeature.countEmptyCorners}. */
    private static int countEmptyCorners(ServerLevelAccessor level, BoundingBox box) {
        var count = new int[1];
        box.forAllCorners(pos -> {
            var state = level.getBlockState(pos);
            if (state.isAir() || state.is(net.minecraft.world.level.block.Blocks.LAVA)
                    || state.is(net.minecraft.world.level.block.Blocks.WATER)) {
                count[0]++;
            }
        });
        return count[0];
    }

    private static StructureTemplate loadVanillaTemplate(String name) throws Exception {
        var path = ROOT.resolve("data/minecraft/structure/fossil/" + name + ".nbt");
        CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        var template = new StructureTemplate();
        template.load(net.minecraft.core.registries.BuiltInRegistries.BLOCK, tag);
        return template;
    }

    private static String readFile(Path path) {
        try {
            return java.nio.file.Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    // --- shared synthetic desert/swamp underground world --------------------

    static int floorHeight(int x, int z, long salt) {
        return -30 + FeatureABCompare.lattice(x, z, salt * 8 + 1, 40);
    }

    /** Vanilla-side and our-side both query this for OCEAN_FLOOR_WG. */
    static int oceanFloorHeight(int x, int z, long salt) {
        return floorHeight(x, z, salt) + 1;
    }

    /** Canonical block id at a position, null for air. */
    static String baseFossilState(int x, int y, int z, long salt) {
        if (y < -64 || y > 100) {
            return null;
        }

        var floor = floorHeight(x, z, salt);
        if (y > floor) {
            return null;
        }

        var cave = FeatureABCompare.lattice(x, z, (salt * 8 + 2) ^ (y * 1000003L + 17), 100);
        if (cave < 5) {
            return null;
        }
        if (cave < 8) {
            return "minecraft:water";
        }
        if (cave < 10) {
            return "minecraft:lava";
        }

        return "minecraft:stone";
    }

    // --- vanilla level proxy -------------------------------------------------

    static final class FossilLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final long salt;

        FossilLevel(long salt) {
            this.salt = salt;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = baseFossilState(pos.getX(), pos.getY(), pos.getZ(), this.salt);
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
                    return y < -64 || y > 319;
                }
                case "getMinY", "getMinBuildHeight" -> {
                    return -64;
                }
                case "getMaxY", "getMaxBuildHeight" -> {
                    return 319;
                }
                case "getHeight" -> {
                    return 384;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "getBlockEntity" -> {
                    return null;
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
                case "getLevel" -> {
                    return null;
                }
                case "toString" -> {
                    return "fossil-ab-level";
                }
                case "hashCode" -> {
                    return 0;
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                default -> {
                    var returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    if (Optional.class.isAssignableFrom(returnType)) {
                        return Optional.empty();
                    }
                    return null;
                }
            }
        }
    }

    // --- our side --------------------------------------------------------------

    static final class OurFossilWorld implements Block.Getter, Block.Setter, FossilFeature.WorldSurface {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurFossilWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public int worldSurfaceHeight(int x, int z) {
            return oceanFloorHeight(x, z, this.salt);
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseFossilState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
