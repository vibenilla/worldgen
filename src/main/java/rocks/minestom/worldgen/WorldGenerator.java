package rocks.minestom.worldgen;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.biome.Biome;
import rocks.minestom.worldgen.biome.BiomeSource;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.biome.CachedBiomeSource;
import rocks.minestom.worldgen.carver.CarverLoader;
import rocks.minestom.worldgen.carver.Carvers;
import rocks.minestom.worldgen.feature.*;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
import rocks.minestom.worldgen.structure.StructureWrites;
import rocks.minestom.worldgen.structure.placement.StructurePlacer;
import rocks.minestom.worldgen.surface.BiomeResolver;
import rocks.minestom.worldgen.surface.SurfaceRules;
import rocks.minestom.worldgen.terrain.Beardifier;
import rocks.minestom.worldgen.terrain.TerrainData;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Orchestrates chunk generation by filling biomes, evaluating density to place
 * base blocks and fluids, applying surface rules, and placing features.
 * This is the step that turns abstract noise and biome data into actual blocks
 * in a chunk.
 */
public final class WorldGenerator implements Generator {
    private final NoiseGeneratorSettingsRuntime settings;
    private final CachedBiomeSource biomeSource;
    private final BiomeZoomer biomeZoomer;
    private final BiomeResolver biomeResolver;
    private final FeatureLoader featureLoader;
    // Must comfortably exceed a pregen run's whole working set: evicting an
    // already-decorated chunk here silently discards its feature writes, since
    // the recomputed replacement only has terrain, surface, and carvers again
    private static final int MAX_CACHED_TERRAIN = 3000;

    private final StructurePlacer structurePlacer;
    private final boolean generateEndStructures;
    private final Carvers carvers;
    // A chunk is blitted long before its own decoration finishes; spill writes
    // arriving in that window must still land chronologically, so writePending
    // only switches to the live (fork) path once decoration is fully done
    private final Set<Long> fullyDecorated = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Long, Map<Integer, Block>> pendingCrossWrites = new java.util.concurrent.ConcurrentHashMap<>();

    private final GenerationUnitAdapter.TerrainLookup terrainAccess = new GenerationUnitAdapter.TerrainLookup() {
        @Override
        public TerrainData terrain(int chunkX, int chunkZ) {
            return WorldGenerator.this.terrainData(chunkX, chunkZ);
        }

        @Override
        public boolean writePending(int chunkX, int chunkZ, int bufferIndex, Block block, Block previousBlock) {
            var key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
            if (WorldGenerator.this.fullyDecorated.contains(key)) {
                return false;
            }

            // Every spill into a not-yet-decorated chunk defers to that
            // chunk's blit: Minestom applies queued forks only after the
            // target's whole generate pass, which would re-assert this
            // (older) write on top of the target's own decoration. The
            // pending queue also survives terrain cache eviction, which is
            // what actually broke the earlier unconditional-deferral
            // attempt (placement-style writes then lived only in the
            // evictable buffer)
            WorldGenerator.this.pendingCrossWrites
                    .computeIfAbsent(key, mapKey -> new java.util.concurrent.ConcurrentHashMap<>())
                    .put(bufferIndex, block);
            return true;
        }
    };
    // LRU: decoration probes neighbors, so evicting wholesale caused recompute
    // storms of the full terrain+surface+carve pipeline
    private final Map<Long, TerrainData> terrainDataCache = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, TerrainData> eldest) {
                    return this.size() > MAX_CACHED_TERRAIN;
                }
            });

    public WorldGenerator(NoiseGeneratorSettingsRuntime settings, BiomeSource biomeSource, long biomeZoomSeed,
            BiomeResolver biomeResolver, FeatureLoader featureLoader, StructurePlacer structurePlacer,
            boolean generateEndStructures) {
        this.settings = settings;
        this.biomeSource = new CachedBiomeSource(biomeSource, settings.minY(), settings.height());
        this.biomeZoomer = new BiomeZoomer(this.biomeSource, biomeZoomSeed);
        this.biomeResolver = biomeResolver;
        this.featureLoader = featureLoader;
        this.structurePlacer = structurePlacer;
        this.generateEndStructures = generateEndStructures;
        this.carvers = new Carvers(settings, this.biomeSource, biomeResolver, this.biomeZoomer,
                new CarverLoader(featureLoader.dataPack(), featureLoader.blockTags()));
    }

    @Override
    public void generate(GenerationUnit unit) {
        var modifier = unit.modifier();
        fillBiomesFromNoise(unit, this.biomeSource, this.settings.minY(), this.settings.maxYInclusive());
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var sizeZ = unit.size().blockZ();
        var height = this.settings.height();

        var chunkKey = (long) (startX >> 4) << 32 | ((startZ >> 4) & 0xFFFFFFFFL);
        var terrainData = this.terrainData(startX >> 4, startZ >> 4);
        var surfaceHeights = terrainData.surfaceHeights();
        var terrainBlocks = terrainData.blocks();

        // Earlier-decorated neighbors' spill-over writes land chronologically
        // before this chunk's own decoration, exactly like vanilla proto-chunks
        var pending = this.pendingCrossWrites.remove(chunkKey);
        if (pending != null) {
            for (var entry : pending.entrySet()) {
                terrainBlocks[entry.getKey()] = entry.getValue();
            }
        }

        // Single blit of terrain + surface + earlier neighbor spill; this chunk's
        // own feature blocks arrive through its fork afterwards. The unit's Y
        // range comes from the instance dimension and may exceed the generator
        // settings' range (nether/end terrain is 128 tall in a 256-tall world).
        var yOffset = unit.absoluteStart().blockY() - this.settings.minY();
        modifier.setAllRelative((x, y, z) -> {
            var terrainY = y + yOffset;
            if (terrainY < 0 || terrainY >= height) {
                return Block.AIR;
            }
            var block = terrainBlocks[(x * sizeZ + z) * height + terrainY];
            return block != null ? block : Block.AIR;
        });


        if (this.generateEndStructures) {
            this.placeEndPodium(unit, surfaceHeights);
        }

        this.placeFeatures(unit, terrainData);

        // This chunk is now fully decorated: replace-style writes queued by
        // neighbors while it was mid-decoration must be replayed directly onto
        // the already-blitted chunk (its own terrain blit will never run
        // again), landing on top of its decoration exactly like a live spill
        // would
        this.fullyDecorated.add(chunkKey);
        this.applyLateSpillWrites(unit, chunkKey, height);
    }

    /**
     * Replays the cross-chunk writes queued for this chunk while it was
     * mid-decoration, translating each terrain-buffer index back into a
     * position relative to the unit and writing it through the modifier so
     * it lands directly on the already-blitted chunk.
     */
    private void applyLateSpillWrites(GenerationUnit unit, long chunkKey, int height) {
        var lateWrites = this.pendingCrossWrites.remove(chunkKey);
        if (lateWrites == null) {
            return;
        }

        var minY = this.settings.minY();
        var unitStartY = unit.absoluteStart().blockY();
        var unitSizeY = unit.size().blockY();
        var lateModifier = unit.modifier();
        for (var entry : lateWrites.entrySet()) {
            var bufferIndex = entry.getKey();
            var yIndex = bufferIndex % height;
            var remainder = bufferIndex / height;
            var localZ = remainder % 16;
            var localX = remainder / 16;
            var localY = (minY + yIndex) - unitStartY;
            if (localY < 0 || localY >= unitSizeY) {
                continue;
            }
            lateModifier.setRelative(localX, localY, localZ, entry.getValue());
        }
    }

    /**
     * Debug trace for isolating lush-caves-style decoration divergences: dumps
     * the rng seed state and a world snapshot right before a placed feature
     * runs, in the same format {@code ChunkVegetationReplay} consumes.
     */
    private static void dumpDecorTrace(String key, WorldgenRandom random, GenerationUnitAdapter levelAdapter, BlockVec origin, int minY, int maxY) {
        try {
            var sourceField = random.getClass().getDeclaredField("randomSource");
            sourceField.setAccessible(true);
            var source = sourceField.get(random);
            var generatorField = source.getClass().getDeclaredField("randomNumberGenerator");
            generatorField.setAccessible(true);
            var generator = generatorField.get(source);
            var loField = generator.getClass().getDeclaredField("seedLo");
            var hiField = generator.getClass().getDeclaredField("seedHi");
            loField.setAccessible(true);
            hiField.setAccessible(true);
            System.out.println("TRACE rng " + key + " " + loField.getLong(generator) + " " + hiField.getLong(generator));
        } catch (ReflectiveOperationException exception) {
            System.out.println("TRACE rng " + key + " unavailable " + exception);
        }

        var reachXZ = Integer.getInteger("worldgen.decorTraceReach", 24);
        var traceMinY = Integer.getInteger("worldgen.decorTraceMinY", minY);
        var traceMaxY = Integer.getInteger("worldgen.decorTraceMaxY", maxY);
        for (var y = Math.max(minY, traceMinY); y <= Math.min(maxY, traceMaxY); y++) {
            for (var x = origin.blockX() - reachXZ; x < origin.blockX() + reachXZ; x++) {
                for (var z = origin.blockZ() - reachXZ; z < origin.blockZ() + reachXZ; z++) {
                    var block = levelAdapter.getBlock(x, y, z);
                    if (!block.isAir()) {
                        System.out.println("TRACE world " + key + " " + x + " " + y + " " + z + " " + serializeBlock(block));
                    }
                }
            }
        }
    }

    private static String serializeBlock(Block block) {
        var properties = block.properties();
        if (properties.isEmpty()) {
            return block.name();
        }
        var builder = new StringBuilder(block.name()).append('[');
        var first = true;
        for (var entry : new java.util.TreeMap<>(properties).entrySet()) {
            if (!first) {
                builder.append(',');
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        return builder.append(']').toString();
    }

    /**
     * Locates the nearest start of the given structure around a block
     * position, scanning up to {@code radiusChunks} chunks outward, or null
     * if none is found (or this dimension places no structures).
     */
    public BlockVec locateStructure(Key structureKey, int centerX, int centerZ, int radiusChunks) {
        return this.locateStructure(structureKey, centerX, centerZ, radiusChunks, java.util.Set.of());
    }

    /**
     * Same as {@link #locateStructure(Key, int, int, int)}, but skips any start chunk packed into
     * {@code excludedChunkKeys} (as {@code (long) chunkX << 32 | chunkZ & 0xffffffffL}), letting callers
     * enumerate the nearest N instances of a structure by excluding starts already found.
     */
    public BlockVec locateStructure(Key structureKey, int centerX, int centerZ, int radiusChunks,
            java.util.Set<Long> excludedChunkKeys) {
        if (this.structurePlacer == null) {
            return null;
        }
        return this.structurePlacer.locateNearest(structureKey, centerX >> 4, centerZ >> 4, radiusChunks,
                excludedChunkKeys, this.biomeZoomer, this.settings);
    }

    /**
     * Applies the surface rules onto the terrain buffer, mirroring vanilla's
     * per-column scan (fluid keeps the stone-depth-above run, air resets it;
     * rules only replace the default block).
     */
    private void applySurface(TerrainData terrainData, int chunkX, int chunkZ) {
        var surfaceRule = this.settings.surfaceRule();
        var defaultBlock = this.settings.defaultBlock();
        var constantSurface = SurfaceRules.constantBlock(surfaceRule);
        if (constantSurface != null && constantSurface.equals(defaultBlock)) {
            return;
        }

        var startX = chunkX * 16;
        var startZ = chunkZ * 16;
        var sizeX = 16;
        var sizeZ = 16;
        var minY = this.settings.minY();
        var maxY = this.settings.maxYInclusive();
        var height = maxY - minY + 1;
        var surfaceHeights = terrainData.surfaceHeights();
        var waterHeights = terrainData.waterHeights();
        var stoneMask = terrainData.stoneMask();
        var terrainBlocks = terrainData.blocks();

        var surfaceContext = new SurfaceRules.Context(
                this.settings.surfaceSystem(),
                this.settings.randomState(),
                this.biomeResolver,
                this.biomeZoomer,
                this.settings.preliminarySurfaceLevel(),
                minY,
                maxY);

        // WORLD_SURFACE_WG equivalent: highest non-air block (solid or fluid) per column
        var worldSurfaceHeights = new int[sizeX * sizeZ];
        for (var surfaceIndex = 0; surfaceIndex < worldSurfaceHeights.length; surfaceIndex++) {
            var solidTop = surfaceHeights[surfaceIndex];
            var fluidTop = waterHeights[surfaceIndex] == Integer.MIN_VALUE ? Integer.MIN_VALUE : waterHeights[surfaceIndex] - 1;
            worldSurfaceHeights[surfaceIndex] = Math.max(solidTop, fluidTop);
        }

        for (var localX = 0; localX < sizeX; localX++) {
            var blockX = startX + localX;

            for (var localZ = 0; localZ < sizeZ; localZ++) {
                var blockZ = startZ + localZ;
                var surfaceIndex = localX * sizeZ + localZ;
                var maskIndex = surfaceIndex * height;
                var scanStart = worldSurfaceHeights[surfaceIndex];
                if (scanStart == Integer.MIN_VALUE) {
                    continue;
                }

                var steep = isSteep(worldSurfaceHeights, sizeZ, localX, localZ);
                surfaceContext.updateXZ(blockX, blockZ, steep);

                var stoneDepthAbove = 0;
                var waterHeight = Integer.MIN_VALUE;
                var nextCeilingStoneY = Integer.MAX_VALUE;
                for (var blockY = Math.min(scanStart, maxY); blockY >= minY; blockY--) {
                    var state = stoneMask[maskIndex + blockY - minY];
                    if (state == TerrainData.AIR) {
                        stoneDepthAbove = 0;
                        waterHeight = Integer.MIN_VALUE;
                        continue;
                    }

                    if (state == TerrainData.FLUID) {
                        if (waterHeight == Integer.MIN_VALUE) {
                            waterHeight = blockY + 1;
                        }
                        continue;
                    }

                    if (nextCeilingStoneY >= blockY) {
                        nextCeilingStoneY = minY;
                        for (var lookaheadY = blockY - 1; lookaheadY >= minY; lookaheadY--) {
                            var lookaheadState = stoneMask[maskIndex + lookaheadY - minY];
                            if (lookaheadState == TerrainData.AIR || lookaheadState == TerrainData.FLUID) {
                                nextCeilingStoneY = lookaheadY + 1;
                                break;
                            }
                        }
                    }

                    stoneDepthAbove++;
                    if (state != TerrainData.SOLID) {
                        continue;
                    }

                    var stoneDepthBelow = blockY - nextCeilingStoneY + 1;
                    surfaceContext.updateY(blockY, stoneDepthAbove, stoneDepthBelow, waterHeight);
                    var newBlock = surfaceRule.tryApply(surfaceContext);
                    if (newBlock != null && !newBlock.equals(defaultBlock)) {
                        terrainBlocks[maskIndex + blockY - minY] = newBlock;
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void placeFeatures(GenerationUnit unit, TerrainData terrainData) {
        var surfaceHeights = terrainData.surfaceHeights();
        var waterHeights = terrainData.waterHeights();
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var sizeX = unit.size().blockX();
        var sizeZ = unit.size().blockZ();
        var forkPadding = 16;
        // The fork spans the full instance Y range, not just the generator
        // settings range: vanilla decorates above the generation height too
        // (mushrooms and roots on top of the nether bedrock roof at y 128)
        var unitMinY = unit.absoluteStart().blockY();
        var unitMaxY = unitMinY + unit.size().blockY();
        var forkStart = new BlockVec(startX - forkPadding, unitMinY, startZ - forkPadding);
        var forkEnd = new BlockVec(startX + sizeX + forkPadding, unitMaxY,
                startZ + sizeZ + forkPadding);
        var featureUnit = unit.fork(forkStart, forkEnd);
        var levelAdapter = new GenerationUnitAdapter(
                featureUnit,
                startX,
                startZ,
                sizeX,
                sizeZ,
                this.settings.minY(),
                terrainData.blocks(),
                this.settings.height(),
                this.terrainAccess);

        var placementContext = new PlacementContext(
                levelAdapter,
                startX,
                startZ,
                sizeX,
                sizeZ,
                surfaceHeights,
                waterHeights,
                this.settings.minY(),
                this.settings.maxYInclusive(),
                this.settings.seaLevel(),
                this.biomeZoomer,
                null,
                this.featureLoader);

        // Vanilla decoration: biomes from the 3x3 chunk neighborhood pick the feature
        // set; each feature is seeded from the decoration seed and its global index
        // within its step, so placement is independent of biome layout details.
        var chunkBiomes = this.collectNeighborhoodBiomes(startX >> 4, startZ >> 4);
        var featuresPerStep = this.featureLoader.featuresPerStep(this.biomeSource.possibleBiomes());
        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(this.settings.randomState().seed(), startX, startZ);
        var origin = new BlockVec(startX, this.settings.minY(), startZ);

        // Vanilla step interleave: each decoration step places its structures
        // first, then its features
        var totalSteps = Math.max(11, featuresPerStep.size());
        for (var stepIndex = 0; stepIndex < totalSteps; stepIndex++) {
            if (this.structurePlacer != null) {
                this.structurePlacer.placeStructures(unit, surfaceHeights, this.biomeZoomer, this.settings, stepIndex);
            }
            if (stepIndex >= featuresPerStep.size()) {
                continue;
            }
            var stepData = featuresPerStep.get(stepIndex);
            var featureIndexes = new TreeSet<Integer>();
            for (var biome : chunkBiomes) {
                var biomeSteps = this.featureLoader.getBiomeFeatures(biome);
                if (stepIndex >= biomeSteps.size()) {
                    continue;
                }

                for (var featureKey : biomeSteps.get(stepIndex)) {
                    var index = stepData.indexMapping().get(featureKey);
                    if (index != null) {
                        featureIndexes.add(index);
                    }
                }
            }

            for (var featureIndex : featureIndexes) {
                var placedFeatureKey = stepData.features().get(featureIndex);
                random.setFeatureSeed(decorationSeed, featureIndex, stepIndex);

                var placedFeature = this.featureLoader.getPlacedFeature(placedFeatureKey);
                if (placedFeature == null) {
                    continue;
                }

                var configuredFeature = placedFeature.configuredFeature(this.featureLoader);
                if (configuredFeature == null) {
                    continue;
                }

                placementContext.currentFeature(placedFeatureKey);
                var debugChunk = System.getProperty("worldgen.debugchunk");
                var debugThis = debugChunk != null && debugChunk.equals((startX >> 4) + "," + (startZ >> 4));
                var debugStep = stepIndex;
                if (debugThis) {
                    var state = "?";
                    try {
                        var sourceField = random.getClass().getDeclaredField("randomSource");
                        sourceField.setAccessible(true);
                        var source = sourceField.get(random);
                        var generatorField = source.getClass().getDeclaredField("randomNumberGenerator");
                        generatorField.setAccessible(true);
                        var generator = generatorField.get(source);
                        var loField = generator.getClass().getDeclaredField("seedLo");
                        var hiField = generator.getClass().getDeclaredField("seedHi");
                        loField.setAccessible(true);
                        hiField.setAccessible(true);
                        state = loField.getLong(generator) + "," + hiField.getLong(generator);
                    } catch (ReflectiveOperationException ignored) {
                    }
                    System.out.println("FEATSTART idx=" + featureIndex + " step=" + stepIndex + " rng=" + state
                            + " " + placedFeatureKey.asString());
                }

                var decorTraceChunk = System.getProperty("worldgen.decorTrace", "");
                var decorTraceFeature = System.getProperty("worldgen.decorTraceFeature", "");
                var tracingThisFeature = decorTraceChunk.equals((startX >> 4) + "," + (startZ >> 4))
                        && placedFeatureKey.asString().contains(decorTraceFeature);
                if (tracingThisFeature) {
                    dumpDecorTrace(placedFeatureKey.asString() + ":" + featureIndex + ":" + stepIndex, random, levelAdapter, origin,
                            this.settings.minY(), this.settings.maxYInclusive());
                }

                var placeRandom = tracingThisFeature ? new CountingRandomSource(random) : (RandomSource) random;
                placedFeature.place(placementContext, placeRandom, origin, (position, featureRandom) -> {
                    if (debugThis) {
                        System.out.println("FEATPOS " + placedFeatureKey.asString() + " idx=" + featureIndex + " step=" + debugStep + " pos=" + position);
                    }
                    // No world-bounds filter: vanilla runs features at out-of-world
                    // origins (deep ore blobs still reach into the world, and the
                    // shared random must consume their draws either way)
                    var context = new FeaturePlaceContext<>(
                            levelAdapter,
                            featureRandom,
                            position,
                            configuredFeature.config(),
                            this.settings.randomState().seed(),
                            this.settings.minY(),
                            this.settings.maxYInclusive(),
                            this.settings.seaLevel());

                    var featureImpl = configuredFeature.feature();
                    if (featureImpl instanceof RandomSelectorFeature randomSelector) {
                        randomSelector.place(context, this.featureLoader);
                    } else if (featureImpl instanceof FreezeTopLayerFeature freezeTopLayer) {
                        freezeTopLayer.place(context, placementContext, this.biomeResolver);
                    } else {
                        var placed = ((Feature) featureImpl).place(context);
                        if (placed && featureImpl instanceof UnderwaterMagmaFeature) {
                            UnderwaterMagmaFeature.convertBubbleColumnsAfterPlacement(levelAdapter,
                                    (FeaturePlaceContext) context);
                        }
                    }
                });
                if (tracingThisFeature) {
                    System.out.println("TRACE draws " + placedFeatureKey.asString() + ":" + featureIndex + ":" + stepIndex
                            + " " + ((CountingRandomSource) placeRandom).count);
                }
            }
        }

        // Structure-placed leaves settle their distance here, once this
        // chunk's own vegetation decoration has planted whatever natural
        // trees end up near the structure, mirroring vanilla only ever
        // resolving that distance through scheduled ticks that fire after
        // decoration. A wider fork than regular feature placement: a
        // structure's canopy can reach further than the usual 16-block
        // neighbor margin from whichever chunk happens to finish it off.
        var leavesForkPadding = 128;
        var leavesForkStart = new BlockVec(startX - leavesForkPadding, this.settings.minY(), startZ - leavesForkPadding);
        var leavesForkEnd = new BlockVec(startX + sizeX + leavesForkPadding, this.settings.maxYInclusive() + 1,
                startZ + sizeZ + leavesForkPadding);
        var leavesUnit = unit.fork(leavesForkStart, leavesForkEnd);
        var leavesAdapter = new GenerationUnitAdapter(
                leavesUnit,
                startX,
                startZ,
                sizeX,
                sizeZ,
                this.settings.minY(),
                terrainData.blocks(),
                this.settings.height(),
                this.terrainAccess);
        StructureWrites.flushLeavesUpdates(surfaceHeights, leavesAdapter);
    }

    /** Debug-only wrapper counting draws consumed by a single traced placed-feature call. */
    private static final class CountingRandomSource implements RandomSource {
        private final RandomSource delegate;
        private long count;

        CountingRandomSource(RandomSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public RandomSource fork() {
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
            this.count++;
            return this.delegate.nextInt(bound);
        }

        @Override
        public long nextLong() {
            this.count++;
            return this.delegate.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            this.count++;
            return this.delegate.nextBoolean();
        }

        @Override
        public float nextFloat() {
            this.count++;
            return this.delegate.nextFloat();
        }

        @Override
        public double nextDouble() {
            this.count++;
            return this.delegate.nextDouble();
        }
    }

    /**
     * Distinct biomes over the 3x3 chunk neighborhood at quart resolution,
     * mirroring the section palettes vanilla unions for decoration. Each chunk's
     * set is memoized since neighbors share it.
     */
    private List<Key> collectNeighborhoodBiomes(int chunkX, int chunkZ) {
        var union = new HashSet<Key>();
        for (var offsetX = -1; offsetX <= 1; offsetX++) {
            for (var offsetZ = -1; offsetZ <= 1; offsetZ++) {
                union.addAll(this.chunkBiomes(chunkX + offsetX, chunkZ + offsetZ));
            }
        }

        // Vanilla iterates an ObjectArraySet in insertion order, but the feature
        // index set is re-sorted afterwards, so ordering here does not matter.
        return List.copyOf(union);
    }

    /**
     * Memoized pure terrain computation per chunk. Decoration probes neighbor
     * heights (vanilla reads real neighbor chunks), so entries are shared
     * between a chunk's own generation and its neighbors' feature passes.
     */
    private TerrainData terrainData(int chunkX, int chunkZ) {
        var key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
        var cached = this.terrainDataCache.get(key);
        if (cached != null) {
            return cached;
        }

        var generator = new TerrainGenerator(this.settings);
        // Vanilla NOISE stage: the beardifier for the chunk's structure starts
        // joins the density before the solid/air decision. Start computation
        // itself only reads un-bearded terrain, so this cannot recurse.
        var beardifier = this.structurePlacer != null
                ? this.structurePlacer.beardifier(chunkX, chunkZ, this.biomeZoomer, this.settings)
                : Beardifier.EMPTY;
        var data = generator.generate(chunkX, chunkZ, beardifier);
        this.applySurface(data, chunkX, chunkZ);
        // Vanilla chunk status order is NOISE -> SURFACE -> CARVERS; the carvers
        // re-apply the top material where they expose the dirt under grass.
        this.carvers.applyCarvers(data, chunkX, chunkZ, generator.aquifer());
        var existing = this.terrainDataCache.putIfAbsent(key, data);
        return existing != null ? existing : data;
    }

    private int[] terrainHeights(int chunkX, int chunkZ) {
        return this.terrainData(chunkX, chunkZ).surfaceHeights();
    }

    private Set<Key> chunkBiomes(int chunkX, int chunkZ) {
        var column = this.biomeSource.chunkColumn(chunkX, chunkZ);
        var biomes = new HashSet<Key>();
        for (var biome : column) {
            biomes.add(biome);
        }
        return biomes;
    }

    private static boolean isSteep(int[] surfaceHeights, int sizeZ, int localX, int localZ) {
        var southZ = Math.max(localZ - 1, 0);
        var northZ = Math.min(localZ + 1, sizeZ - 1);
        var southHeight = surfaceHeights[localX * sizeZ + southZ];
        var northHeight = surfaceHeights[localX * sizeZ + northZ];
        if (northHeight >= southHeight + 4) {
            return true;
        }

        var westX = Math.max(localX - 1, 0);
        var eastX = Math.min(localX + 1, surfaceHeights.length / sizeZ - 1);
        var westHeight = surfaceHeights[westX * sizeZ + localZ];
        var eastHeight = surfaceHeights[eastX * sizeZ + localZ];
        return westHeight >= eastHeight + 4;
    }

    private static boolean isEndFeature(Key key) {
        return key.asString().equals("minecraft:end_platform")
                || key.asString().equals("minecraft:end_spike");
    }

    private void placeEndPodium(GenerationUnit unit, int[] surfaceHeights) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var sizeX = unit.size().blockX();
        var sizeZ = unit.size().blockZ();
        if (startX > 0 || startX + sizeX <= 0 || startZ > 0 || startZ + sizeZ <= 0) {
            return;
        }

        var localX = -startX;
        var localZ = -startZ;
        var surfaceY = surfaceHeights[localX * sizeZ + localZ];
        if (surfaceY == Integer.MIN_VALUE) {
            surfaceY = this.settings.seaLevel();
        }

        var forkPadding = 16;
        var forkStart = new BlockVec(-forkPadding, this.settings.minY(), -forkPadding);
        var forkEnd = new BlockVec(forkPadding + 1, this.settings.maxYInclusive() + 1, forkPadding + 1);
        var featureUnit = unit.fork(forkStart, forkEnd);
        var levelAdapter = new GenerationUnitAdapter(featureUnit);
        EndPodiumFeature.place(levelAdapter, new BlockVec(0, surfaceY, 0), false);
    }

    private static void fillBiomesFromNoise(GenerationUnit unit, BiomeSource biomes, int minY, int maxY) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var endX = startX + unit.size().blockX() - 1;
        var endZ = startZ + unit.size().blockZ() - 1;

        var startY = unit.absoluteStart().blockY();
        var endY = startY + unit.size().blockY() - 1;

        var startQuartX = Math.floorDiv(startX, 4);
        var endQuartX = Math.floorDiv(endX, 4);
        var startQuartZ = Math.floorDiv(startZ, 4);
        var endQuartZ = Math.floorDiv(endZ, 4);
        var startQuartY = Math.floorDiv(startY, 4);
        var endQuartY = Math.floorDiv(endY, 4);
        var minSampleQuartY = Math.floorDiv(minY, 4);
        var maxSampleQuartY = Math.floorDiv(maxY, 4);

        for (var quartX = startQuartX; quartX <= endQuartX; quartX++) {
            for (var quartZ = startQuartZ; quartZ <= endQuartZ; quartZ++) {
                for (var quartY = startQuartY; quartY <= endQuartY; quartY++) {
                    var sampleQuartY = Math.min(Math.max(quartY, minSampleQuartY), maxSampleQuartY);
                    var biome = biomes.biome(quartX, sampleQuartY, quartZ);
                    var biomeKey = RegistryKey.<Biome>unsafeOf(biome);

                    // Fill all 4x4x4 positions in the biome palette for this quart
                    // This matches Minecraft's fillBiomesFromNoise behavior
                    var baseX = quartX << 2;
                    var baseY = quartY << 2;
                    var baseZ = quartZ << 2;

                    for (var dx = 0; dx < 4; dx++) {
                        for (var dy = 0; dy < 4; dy++) {
                            for (var dz = 0; dz < 4; dz++) {
                                unit.modifier().setBiome(baseX + dx, baseY + dy, baseZ + dz, biomeKey);
                            }
                        }
                    }
                }
            }
        }
    }

    private static void fillBiomes(GenerationUnit unit, BiomeZoomer zoomer, int minY, int maxY) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var endX = startX + unit.size().blockX() - 1;
        var endZ = startZ + unit.size().blockZ() - 1;

        var startQuartX = Math.floorDiv(startX, 4);
        var endQuartX = Math.floorDiv(endX, 4);
        var startQuartZ = Math.floorDiv(startZ, 4);
        var endQuartZ = Math.floorDiv(endZ, 4);
        var startQuartY = Math.floorDiv(minY, 4);
        var endQuartY = Math.floorDiv(maxY, 4);

        for (var quartX = startQuartX; quartX <= endQuartX; quartX++) {
            var centerX = (quartX << 2) + 2;

            for (var quartZ = startQuartZ; quartZ <= endQuartZ; quartZ++) {
                var centerZ = (quartZ << 2) + 2;

                for (var quartY = startQuartY; quartY <= endQuartY; quartY++) {
                    var centerY = (quartY << 2) + 2;
                    var biome = zoomer.biome(centerX, centerY, centerZ);
                    var biomeKey = RegistryKey.<Biome>unsafeOf(biome);
                    unit.modifier().setBiome(centerX, centerY, centerZ, biomeKey);
                }
            }
        }
    }
}
