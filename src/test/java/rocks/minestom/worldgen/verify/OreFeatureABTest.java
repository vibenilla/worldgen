package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the {@code minecraft:ore} feature port (used by granite,
 * andesite, and diorite blobs) against real vanilla 26.2 code in process:
 * both sides run the size-64 ellipsoid draw algorithm over an identical
 * synthetic underground stone world with identical feature randoms, and
 * every block write plus the total draw count must match.
 *
 * <p>Vanilla's {@code OreFeature} writes through a {@code BulkSectionAccess}
 * that mutates real {@code LevelChunkSection} block state containers rather
 * than calling {@code level.setBlock}, so this test backs the vanilla side
 * with genuine {@code LevelChunkSection} instances (populated from the same
 * synthetic terrain function) instead of the simple overlay map the other
 * A/B tests use.
 */
final class OreFeatureABTest {

    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 300;
    private static final int MIN_SECTION_Y = -4;
    private static final int SECTION_COUNT = 24;

    private static net.minecraft.core.HolderLookup.Provider lookup;
    private static FeatureLoader loader;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();
        lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        loader = new FeatureLoader(new rocks.minestom.worldgen.datapack.DataPack(ROOT));
    }

    @Test
    void oreGranite() throws Exception {
        runCase("ore_granite");
    }

    @Test
    void oreDiorite() throws Exception {
        runCase("ore_diorite");
    }

    @Test
    void oreAndesite() throws Exception {
        runCase("ore_andesite");
    }

    @SuppressWarnings("unchecked")
    private static void runCase(String configName) throws Exception {
        var vanillaConfigured = (net.minecraft.world.level.levelgen.feature.ConfiguredFeature<OreConfiguration, ?>) (Object) lookup
                .lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + configName)))
                .value();
        var vanillaConfig = vanillaConfigured.config();
        var vanillaFeature = new net.minecraft.world.level.levelgen.feature.OreFeature(OreConfiguration.CODEC);

        var ourConfigured = loader.getConfiguredFeature(Key.key("minecraft:" + configName));
        if (ourConfigured == null) {
            throw new AssertionError(configName + ": OUR SIDE UNPARSED");
        }
        var ourFeature = (rocks.minestom.worldgen.feature.OreFeature) ourConfigured.feature();
        var ourConfig = (rocks.minestom.worldgen.feature.configurations.OreConfiguration) ourConfigured.config();

        var originYs = new int[]{-40, -10, 0, 30, 60, 64, 90, 128, 150, 190};

        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;
        var totalAttempts = 0;
        for (var run = 0; run < RUNS; run++) {
            for (var yIndex = 0; yIndex < originYs.length; yIndex++) {
                totalAttempts++;
                var salt = 4000L + run;
                var seed = 24681L + run * 65599L + yIndex * 911L;
                var x = -12000 + run * 53;
                var z = 9000 - run * 37;
                var originY = originYs[yIndex];

                // vanilla
                var handler = new OreLevelHandler(salt);
                var level = (WorldGenLevel) Proxy.newProxyInstance(
                        OreFeatureABTest.class.getClassLoader(),
                        new Class<?>[]{WorldGenLevel.class},
                        handler);
                var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
                var vanillaContext = new FeaturePlaceContext<>(
                        Optional.empty(), level, null, vanillaRandom, new BlockPos(x, originY, z), vanillaConfig);
                vanillaFeature.place(vanillaContext);
                handler.recordWrites();

                var vanillaSets = new TreeMap<String, String>();
                for (var entry : handler.writes.entrySet()) {
                    vanillaSets.put(entry.getKey(), entry.getValue());
                }

                // ours
                var ourWorld = new OurOreWorld(salt);
                var ourRandom = new FeatureABCompare.CountingOurRandom(
                        new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
                var context = new rocks.minestom.worldgen.feature.FeaturePlaceContext<>(
                        ourWorld, ourRandom, new BlockVec(x, originY, z), ourConfig, seed, -64, 319, 63);
                ourFeature.place(context);

                // Vanilla's write set only contains positions whose state
                // actually changed (see recordWrites, which diffs the final
                // section content against the synthetic terrain). Ore blobs
                // routinely "place" a target block onto a position that
                // already holds that exact block (for example andesite onto
                // pre-existing andesite terrain), which is a real set call on
                // both sides but not a visible mutation, so no-op sets are
                // filtered out here to compare on the same basis.
                var ourSets = new TreeMap<String, String>();
                for (var entry : ourWorld.overlay.entrySet()) {
                    var position = entry.getKey();
                    var written = FeatureABCompare.canonical(entry.getValue());
                    var base = baseOreState(position.blockX(), position.blockY(), position.blockZ(), salt);
                    var baseCanonical = base == null
                            ? FeatureABCompare.canonical(Block.AIR)
                            : FeatureABCompare.canonical(FeatureABCompare.ourState(base));
                    if (!written.equals(baseCanonical)) {
                        ourSets.put(position.blockX() + "," + position.blockY() + "," + position.blockZ(), written);
                    }
                }

                vanillaSetTotal += vanillaSets.size();
                if (vanillaRandom.count != ourRandom.count) {
                    drawMismatchRuns++;
                    if (printed < 4) {
                        System.out.println(configName + " run=" + run + " y=" + originY
                                + " DRAWS vanilla=" + vanillaRandom.count + " ours=" + ourRandom.count);
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
                        if (printed < 32) {
                            System.out.println(configName + " run=" + run + " y=" + originY
                                    + " at " + key + " vanilla=" + vanilla + " ours=" + ours);
                            printed++;
                        }
                    }
                }
            }
        }

        System.out.println(configName + ": runs=" + totalAttempts + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, configName + " block write mismatches");
        assertEquals(0, drawMismatchRuns, configName + " runs with diverging draw counts");
    }

    // --- synthetic dense underground stone world ----------------------------

    /**
     * Canonical block id at a position, null for air. Dense stone/deepslate
     * terrain (with granite/diorite/andesite/tuff pockets already present, and
     * rare small air/dirt intrusions) up to y=260, open air above: this keeps
     * vanilla's {@code OCEAN_FLOOR_WG} heightmap probe reliably above every
     * tested origin so both sides always reach the ellipsoid draw.
     */
    static String baseOreState(int x, int y, int z, long salt) {
        if (y < -64 || y > 319 || y > 260) {
            return null;
        }

        var hash = FeatureABCompare.hash((long) x * 668265263L + y, z, salt);
        var pct = (int) ((hash >>> 40) % 1000);
        if (pct < 15) {
            return null;
        }
        if (pct < 20) {
            return "minecraft:dirt";
        }
        if (pct < 720) {
            return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
        }
        if (pct < 780) {
            return "minecraft:granite";
        }
        if (pct < 840) {
            return "minecraft:diorite";
        }
        if (pct < 900) {
            return "minecraft:andesite";
        }
        if (pct < 960) {
            return "minecraft:tuff";
        }
        return y < 0 ? "minecraft:deepslate" : "minecraft:stone";
    }

    // --- vanilla level proxy, backed by real LevelChunkSection --------------

    static final class OreLevelHandler implements InvocationHandler {
        final long salt;
        final Map<Long, LevelChunkSection[]> chunks = new HashMap<>();
        final Map<String, String> writes = new HashMap<>();

        OreLevelHandler(long salt) {
            this.salt = salt;
        }

        LevelChunkSection[] sectionsFor(int chunkX, int chunkZ) {
            var key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
            return this.chunks.computeIfAbsent(key, unused -> {
                var sections = new LevelChunkSection[SECTION_COUNT];
                for (var sectionIndex = 0; sectionIndex < SECTION_COUNT; sectionIndex++) {
                    sections[sectionIndex] = createSection(chunkX, chunkZ, sectionIndex, this.salt);
                }
                return sections;
            });
        }

        static LevelChunkSection createSection(int chunkX, int chunkZ, int sectionIndex, long salt) {
            var strategy = Strategy.createForBlockStates(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
            var states = new PalettedContainer<BlockState>(Blocks.AIR.defaultBlockState(), strategy);
            var section = new LevelChunkSection(states, null);
            var baseY = (sectionIndex + MIN_SECTION_Y) * 16;
            for (var localX = 0; localX < 16; localX++) {
                for (var localY = 0; localY < 16; localY++) {
                    for (var localZ = 0; localZ < 16; localZ++) {
                        var x = chunkX * 16 + localX;
                        var y = baseY + localY;
                        var z = chunkZ * 16 + localZ;
                        var base = baseOreState(x, y, z, salt);
                        if (base != null) {
                            section.setBlockState(localX, localY, localZ, FeatureABCompare.vanillaState(base), false);
                        }
                    }
                }
            }
            return section;
        }

        BlockState state(BlockPos pos) {
            var sections = this.sectionsFor(pos.getX() >> 4, pos.getZ() >> 4);
            var sectionIndex = (pos.getY() >> 4) - MIN_SECTION_Y;
            if (sectionIndex < 0 || sectionIndex >= SECTION_COUNT) {
                return Blocks.AIR.defaultBlockState();
            }
            return sections[sectionIndex].getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
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
                    if (args == null || args.length == 0) {
                        return 384;
                    }
                    // OCEAN_FLOOR_WG heightmap scan over the synthetic terrain
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    for (var y = 260; y >= -64; y--) {
                        if (!this.state(new BlockPos(x, y, z)).isAir()) {
                            return y + 1;
                        }
                    }
                    return -64;
                }
                case "getSeaLevel" -> {
                    return 63;
                }
                case "getSectionsCount" -> {
                    return SECTION_COUNT;
                }
                case "getMinSectionY" -> {
                    return MIN_SECTION_Y;
                }
                case "getMaxSectionY" -> {
                    return MIN_SECTION_Y + SECTION_COUNT - 1;
                }
                case "getSectionIndex" -> {
                    var y = args[0] instanceof BlockPos pos ? pos.getY() : (Integer) args[0];
                    return (y >> 4) - MIN_SECTION_Y;
                }
                case "getSectionIndexFromSectionY" -> {
                    return (Integer) args[0] - MIN_SECTION_Y;
                }
                case "getSectionYFromSectionIndex" -> {
                    return (Integer) args[0] + MIN_SECTION_Y;
                }
                case "getChunk" -> {
                    if (args.length >= 2 && args[0] instanceof Integer chunkX && args[1] instanceof Integer chunkZ) {
                        return chunkAccess(chunkX, chunkZ, this);
                    }
                    if (args.length >= 1 && args[0] instanceof BlockPos pos) {
                        return chunkAccess(pos.getX() >> 4, pos.getZ() >> 4, this);
                    }
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "toString" -> {
                    return "ore-ab-level";
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

        static ChunkAccess chunkAccess(int chunkX, int chunkZ, OreLevelHandler handler) {
            return new OreChunkAccess(new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), handler.sectionsFor(chunkX, chunkZ));
        }

        /**
         * LevelChunkSection has no hook for individual writes, so block sets
         * performed by the feature are recorded here by diffing every section
         * against the synthetic terrain after {@code doPlace} finishes.
         */
        void recordWrites() {
            for (var entry : this.chunks.entrySet()) {
                var chunkX = (int) (entry.getKey() >> 32);
                var chunkZ = (int) (long) entry.getKey();
                var sections = entry.getValue();
                for (var sectionIndex = 0; sectionIndex < SECTION_COUNT; sectionIndex++) {
                    var section = sections[sectionIndex];
                    if (section.hasOnlyAir()) {
                        continue;
                    }
                    var baseY = (sectionIndex + MIN_SECTION_Y) * 16;
                    for (var localX = 0; localX < 16; localX++) {
                        for (var localY = 0; localY < 16; localY++) {
                            for (var localZ = 0; localZ < 16; localZ++) {
                                var x = chunkX * 16 + localX;
                                var y = baseY + localY;
                                var z = chunkZ * 16 + localZ;
                                var current = section.getBlockState(localX, localY, localZ);
                                var base = baseOreState(x, y, z, this.salt);
                                var baseState = base == null ? Blocks.AIR.defaultBlockState() : FeatureABCompare.vanillaState(base);
                                if (!current.equals(baseState)) {
                                    this.writes.put(x + "," + y + "," + z, FeatureABCompare.canonical(current));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- our side --------------------------------------------------------------

    static final class OurOreWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final long salt;

        OurOreWorld(long salt) {
            this.salt = salt;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseOreState(x, y, z, this.salt);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }

    // --- minimal ChunkAccess backing BulkSectionAccess ----------------------

    static final class OreLevelHeight implements net.minecraft.world.level.LevelHeightAccessor {
        static final OreLevelHeight INSTANCE = new OreLevelHeight();

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinY() {
            return -64;
        }
    }

    /**
     * Real {@code ChunkAccess} backed by the synthetic {@link LevelChunkSection}
     * array, so {@code BulkSectionAccess} can acquire and mutate its sections
     * exactly as it would a genuine world chunk. Only {@code getSection}
     * (inherited, non-abstract) is exercised by {@code OreFeature}; every
     * other abstract member is unreachable from that code path and stubbed.
     */
    static final class OreChunkAccess extends ChunkAccess {
        OreChunkAccess(net.minecraft.world.level.ChunkPos chunkPos, LevelChunkSection[] sections) {
            super(chunkPos, net.minecraft.world.level.chunk.UpgradeData.EMPTY, OreLevelHeight.INSTANCE, null, 0L, sections, null);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            var sectionIndex = this.getSectionIndex(pos.getY());
            if (sectionIndex < 0 || sectionIndex >= SECTION_COUNT) {
                return Blocks.AIR.defaultBlockState();
            }
            return this.getSection(sectionIndex).getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }

        @Override
        public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public BlockState setBlockState(BlockPos pos, BlockState state, int flags) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.level.block.entity.@org.jspecify.annotations.Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public void setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addEntity(net.minecraft.world.entity.Entity entity) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.level.chunk.status.ChunkStatus getPersistedStatus() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeBlockEntity(BlockPos pos) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.nbt.CompoundTag getBlockEntityNbtForSaving(
                BlockPos blockPos, net.minecraft.core.HolderLookup.Provider registryAccess) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.ticks.TickContainerAccess<net.minecraft.world.level.block.Block> getBlockTicks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.world.ticks.TickContainerAccess<net.minecraft.world.level.material.Fluid> getFluidTicks() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChunkAccess.PackedTicks getTicksForSerialization(long currentTick) {
            throw new UnsupportedOperationException();
        }

        @Override
        public net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> getNoiseBiome(int quartX, int quartY, int quartZ) {
            throw new UnsupportedOperationException();
        }
    }
}
