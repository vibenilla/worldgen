package rocks.minestom.worldgen.verify;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Replays a vanilla PLACED ore-style feature (section-writing features that
 * go through {@code BulkSectionAccess}) for one chunk against a captured
 * decorTrace world snapshot, with the real zoomed biome manager, printing
 * every block whose state changed. Args: trace file, placed feature name,
 * trace key, chunk start x, chunk start z, world seed.
 */
public final class OreChunkReplay {
    private static final int MIN_SECTION_Y = -4;
    private static final int SECTION_COUNT = 24;

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();

        var traceFile = Path.of(args[0]);
        var placedName = args[1];
        var traceKey = args[2];
        var startX = Integer.parseInt(args[3]);
        var startZ = Integer.parseInt(args[4]);
        var worldSeed = Long.parseLong(args[5]);

        long seedLo = 0;
        long seedHi = 0;
        var world = new HashMap<BlockPos, BlockState>();
        for (var line : Files.readAllLines(traceFile)) {
            if (!line.startsWith("TRACE ")) {
                continue;
            }
            var parts = line.split(" ");
            if (parts.length < 3 || !parts[2].equals(traceKey)) {
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
        var placed = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + placedName)))
                .value();

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

        var randomState = net.minecraft.world.level.levelgen.RandomState.create(
                lookup, net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:overworld")),
                worldSeed);
        var sampler = randomState.sampler();
        var biomeManager = new net.minecraft.world.level.biome.BiomeManager(
                (quartX, quartY, quartZ) -> biomeSource.getNoiseBiome(quartX, quartY, quartZ, sampler),
                net.minecraft.world.level.biome.BiomeManager.obfuscateSeed(worldSeed));

        var handler = new SectionLevelHandler(world, biomeManager);
        var level = (WorldGenLevel) Proxy.newProxyInstance(
                OreChunkReplay.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class},
                handler);

        var counted = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seedLo, seedHi));
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(counted);
        var result = placed.placeWithBiomeCheck(level, generator, random, new BlockPos(startX, -64, startZ));
        System.out.println("TOTALDRAWS " + counted.count);
        System.out.println("RESULT " + result);
        handler.printWrites();
        System.exit(0);
    }

    /**
     * WorldGenLevel proxy backed by the snapshot map, serving real
     * {@code LevelChunkSection}s (so {@code BulkSectionAccess} works) that are
     * materialized lazily from the snapshot and diffed afterwards.
     */
    static final class SectionLevelHandler implements InvocationHandler {
        private final Map<BlockPos, BlockState> world;
        private final net.minecraft.world.level.biome.BiomeManager biomeManager;
        private final Map<Long, LevelChunkSection[]> chunks = new HashMap<>();

        SectionLevelHandler(Map<BlockPos, BlockState> world, net.minecraft.world.level.biome.BiomeManager biomeManager) {
            this.world = world;
            this.biomeManager = biomeManager;
        }

        private BlockState snapshotState(BlockPos pos) {
            var state = this.world.get(pos.immutable());
            return state != null ? state : Blocks.AIR.defaultBlockState();
        }

        private LevelChunkSection[] sectionsFor(int chunkX, int chunkZ) {
            return this.chunks.computeIfAbsent((long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL), key -> {
                var sections = new LevelChunkSection[SECTION_COUNT];
                for (var sectionIndex = 0; sectionIndex < SECTION_COUNT; sectionIndex++) {
                    var strategy = net.minecraft.world.level.chunk.Strategy
                            .createForBlockStates(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
                    var states = new net.minecraft.world.level.chunk.PalettedContainer<BlockState>(
                            Blocks.AIR.defaultBlockState(), strategy);
                    var section = new LevelChunkSection(states, null);
                    var baseY = (sectionIndex + MIN_SECTION_Y) * 16;
                    for (var localX = 0; localX < 16; localX++) {
                        for (var localY = 0; localY < 16; localY++) {
                            for (var localZ = 0; localZ < 16; localZ++) {
                                var state = this.snapshotState(new BlockPos(chunkX * 16 + localX, baseY + localY, chunkZ * 16 + localZ));
                                if (!state.isAir()) {
                                    section.setBlockState(localX, localY, localZ, state, false);
                                }
                            }
                        }
                    }
                    sections[sectionIndex] = section;
                }
                return sections;
            });
        }

        void printWrites() {
            for (var entry : this.chunks.entrySet()) {
                var chunkX = (int) (entry.getKey() >> 32);
                var chunkZ = (int) (long) entry.getKey();
                var sections = entry.getValue();
                for (var sectionIndex = 0; sectionIndex < SECTION_COUNT; sectionIndex++) {
                    var section = sections[sectionIndex];
                    if (section == null || section.hasOnlyAir()) {
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
                                var base = this.snapshotState(new BlockPos(x, y, z));
                                if (!current.equals(base)) {
                                    System.out.println("VSET " + x + " " + y + " " + z + " " + current);
                                }
                            }
                        }
                    }
                }
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getBlockState" -> {
                    return this.snapshotState((BlockPos) args[0]);
                }
                case "getFluidState" -> {
                    return this.snapshotState((BlockPos) args[0]).getFluidState();
                }
                case "isEmptyBlock" -> {
                    return this.snapshotState((BlockPos) args[0]).isAir();
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
                    var x = (Integer) args[1];
                    var z = (Integer) args[2];
                    for (var y = 319; y >= -64; y--) {
                        if (!this.snapshotState(new BlockPos(x, y, z)).isAir()) {
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
                        return new OreFeatureABTest.OreChunkAccess(
                                new net.minecraft.world.level.ChunkPos(chunkX, chunkZ), this.sectionsFor(chunkX, chunkZ));
                    }
                    if (args.length >= 1 && args[0] instanceof BlockPos pos) {
                        return new OreFeatureABTest.OreChunkAccess(
                                new net.minecraft.world.level.ChunkPos(pos.getX() >> 4, pos.getZ() >> 4),
                                this.sectionsFor(pos.getX() >> 4, pos.getZ() >> 4));
                    }
                    return null;
                }
                case "ensureCanWrite", "hasChunkAt", "hasChunksAt" -> {
                    return true;
                }
                case "getRandom" -> {
                    return net.minecraft.util.RandomSource.create(0L);
                }
                case "getBiome" -> {
                    return this.biomeManager.getBiome((BlockPos) args[0]);
                }
                case "scheduleTick", "markPosForPostProcessing", "blockUpdated", "updateNeighborsAt" -> {
                    return null;
                }
                case "toString" -> {
                    return "ore-chunk-replay-level";
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
