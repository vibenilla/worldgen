package rocks.minestom.worldgen.structure.mineshaft;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.biome.CachedBiomeSource;
import rocks.minestom.worldgen.carver.CarverLoader;
import rocks.minestom.worldgen.carver.Carvers;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.StructureWrites;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.StructureShapeUpdater;
import rocks.minestom.worldgen.surface.DataPackBiomeResolver;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places procedural mineshafts with vanilla-exact seeding.
 *
 * <p>Vanilla seeds a mineshaft start per chunk via
 * {@code setLargeFeatureSeed(worldSeed, chunkX, chunkZ)} (frequency roll,
 * weighted structure selection and piece layout all derive from it), then each
 * generating chunk places the intersecting pieces during the
 * {@code underground_structures} decoration step with a random seeded from the
 * chunk's decoration seed and the structure's registry index within that step.
 *
 * <p>Because piece placement reads the surrounding blocks (liquid contact
 * aborts pieces, floors and supports probe solidity), the placer recomputes
 * the generating chunk's carved terrain as a read snapshot; structure writes
 * go through the normal generation-unit adapter and into that snapshot so
 * later pieces observe earlier ones like vanilla.
 */
public final class MineshaftPlacer {
    /** Vanilla structure reference radius: starts up to 8 chunks away can reach this chunk. */
    private static final int REFERENCE_RADIUS = 8;
    /** GenerationStep.Decoration.UNDERGROUND_STRUCTURES ordinal. */
    private static final int UNDERGROUND_STRUCTURES_STEP = 3;
    private static final Key MINESHAFT_BLOCKING_TAG = Key.key("minecraft:mineshaft_blocking");

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final Map<Long, Optional<MineshaftStart>> starts = new ConcurrentHashMap<>();
    private final Map<Long, List<BlockVec>> pendingShapeUpdates = new ConcurrentHashMap<>();
    private final java.util.Set<Long> decoratedChunks = ConcurrentHashMap.newKeySet();
    private volatile Carvers carvers;
    private volatile List<Key> undergroundStructures;

    public MineshaftPlacer(StructureLoader structureLoader, FeatureLoader featureLoader) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
    }

    /**
     * Whether the given structure set contains procedural mineshaft
     * structures and should be routed through this placer.
     */
    public boolean isMineshaftSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof MineshaftStructure) {
                return true;
            }
        }
        return false;
    }

    public void place(GenerationUnit unit, BiomeZoomer biomeZoomer,
            NoiseGeneratorSettingsRuntime settings, StructureSet structureSet, int[] surfaceHeights) {
        this.place(unit, biomeZoomer, settings, structureSet, surfaceHeights, null);
    }

    /**
     * When a {@code featureAdapter} is given (the interleaved structure step
     * inside feature decoration), writes go through the SAME fork as the
     * chunk's feature placement, so structure and feature writes land in
     * true chronological order - a corridor dug at the underground_structures
     * step no longer erases vegetation a later step plants inside it, which
     * is what happened with a separate fork (Minestom applies forks in
     * creation order, and the feature fork is created first).
     */
    public void place(GenerationUnit unit, BiomeZoomer biomeZoomer,
            NoiseGeneratorSettingsRuntime settings, StructureSet structureSet, int[] surfaceHeights,
            GenerationUnitAdapter featureAdapter) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var chunkX = Math.floorDiv(startX, 16);
        var chunkZ = Math.floorDiv(startZ, 16);

        // Collect starts whose bounding box reaches this chunk, scanning the
        // vanilla reference radius in createReferences order (x, then z).
        var intersecting = new ArrayList<Reference>();
        for (var sourceX = chunkX - REFERENCE_RADIUS; sourceX <= chunkX + REFERENCE_RADIUS; sourceX++) {
            for (var sourceZ = chunkZ - REFERENCE_RADIUS; sourceZ <= chunkZ + REFERENCE_RADIUS; sourceZ++) {
                var start = this.startAt(sourceX, sourceZ, structureSet, settings, biomeZoomer);
                if (start != null && intersectsColumn(start.bounds(), startX, startZ)) {
                    intersecting.add(new Reference(start, packChunk(sourceX, sourceZ)));
                }
            }
        }

        if (intersecting.isEmpty()) {
            return;
        }

        // Prefer the generator's live chunk buffer (terrain + surface +
        // carvers + margin writes mirrored from previously decorated
        // neighbors) - the same state vanilla pieces read at the structure
        // step. Falls back to a computed terrain+carvers snapshot when no
        // lookup has been captured yet (first chunk of a generator).
        var chunkBlocks = this.liveChunkBlocks(chunkX, chunkZ, surfaceHeights);
        var usingLiveBuffer = chunkBlocks != null;
        if (!usingLiveBuffer) {
            var terrainGenerator = new TerrainGenerator(settings);
            var terrainData = terrainGenerator.generate(chunkX, chunkZ);
            this.carvers(settings, biomeZoomer).applyCarvers(terrainData, chunkX, chunkZ, terrainGenerator.aquifer());
            chunkBlocks = terrainData.blocks();
        }

        GenerationUnitAdapter forkAdapter;
        if (featureAdapter != null && usingLiveBuffer) {
            forkAdapter = featureAdapter;
        } else {
            var forkUnit = unit.fork(
                    new BlockVec(startX, settings.minY(), startZ),
                    new BlockVec(startX + 16, settings.maxYInclusive() + 1, startZ + 16));
            forkAdapter = new GenerationUnitAdapter(forkUnit, startX, startZ, 16, 16, settings.minY(),
                    chunkBlocks, settings.height(), StructureWrites.terrainLookup());
        }

        // Writing into the live buffer already makes the blocks visible to
        // this chunk's decoration and to later neighbors; the fallback
        // snapshot needs the StructureWrites replay instead.
        var replayHandle = usingLiveBuffer ? null : surfaceHeights;
        var blockingBiomes = this.structureLoader.biomeTags().biomes(MINESHAFT_BLOCKING_TAG);
        var shapeUpdatePositions = new ArrayList<BlockVec>();
        var level = new MineshaftLevel(forkAdapter, chunkBlocks, startX, startZ,
                settings.minY(), settings.maxYInclusive(), biomeZoomer, blockingBiomes, replayHandle,
                shapeUpdatePositions);
        var chunkBB = new BoundingBox(startX, settings.minY() + 1, startZ,
                startX + 15, settings.maxYInclusive(), startZ + 15);

        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);

        for (var structureKey : this.undergroundStructures()) {
            var references = new ArrayList<Reference>();
            for (var reference : intersecting) {
                if (reference.start().structureKey().equals(structureKey)) {
                    references.add(reference);
                }
            }

            if (references.isEmpty()) {
                continue;
            }

            var index = this.undergroundStructures().indexOf(structureKey);
            random.setFeatureSeed(decorationSeed, index, UNDERGROUND_STRUCTURES_STEP);

            var pieceDebug = (chunkX + "," + chunkZ).equals(System.getProperty("worldgen.pieceChunk"));
            for (var reference : referenceIterationOrder(references)) {
                for (var piece : reference.start().pieces()) {
                    if (piece.boundingBox().intersects(chunkBB)) {
                        if (pieceDebug) {
                            System.out.println("PIECE " + piece.getClass().getSimpleName()
                                    + " box=" + piece.boundingBox() + " rng=" + rngState(random));
                        }
                        piece.postProcess(level, random, chunkBB);
                    }
                }
            }
        }

        if (!shapeUpdatePositions.isEmpty()) {
            this.pendingShapeUpdates
                    .computeIfAbsent(packChunk(chunkX, chunkZ),
                            unused -> java.util.Collections.synchronizedList(new ArrayList<>()))
                    .addAll(shapeUpdatePositions);
        }
    }

    /**
     * Runs the deferred connection-shape pass for every queued chunk whose
     * full 3x3 neighborhood has now finished decorating, mirroring vanilla
     * only recomputing marked positions in {@code postProcessGeneration}
     * when the chunk is promoted to FULL - which requires the surrounding
     * chunks to have completed their FEATURES stage, so a fence next to a
     * chunk border sees the neighbor chunk's corridor already dug out
     * instead of raw terrain.
     */
    public void flushShapeUpdates(int chunkX, int chunkZ, GenerationUnitAdapter level) {
        this.decoratedChunks.add(packChunk(chunkX, chunkZ));
        var iterator = this.pendingShapeUpdates.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var pendingChunkX = (int) (long) entry.getKey();
            var pendingChunkZ = (int) (entry.getKey() >> 32);
            if (!this.neighborhoodDecorated(pendingChunkX, pendingChunkZ)) {
                continue;
            }
            List<BlockVec> positions;
            var pending = entry.getValue();
            synchronized (pending) {
                positions = new ArrayList<>(pending);
            }
            StructureShapeUpdater.update(level, this.structureLoader.blockTags(), positions);
            iterator.remove();
        }
    }

    private boolean neighborhoodDecorated(int chunkX, int chunkZ) {
        for (var offsetX = -1; offsetX <= 1; offsetX++) {
            for (var offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (!this.decoratedChunks.contains(packChunk(chunkX + offsetX, chunkZ + offsetZ))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean intersectsColumn(BoundingBox bounds, int blockX, int blockZ) {
        return bounds.maxX() >= blockX && bounds.minX() <= blockX + 15
                && bounds.maxZ() >= blockZ && bounds.minZ() <= blockZ + 15;
    }

    /**
     * The generator's cached block buffer for this chunk, validated by the
     * surface-height array identity so a lookup captured from another
     * generator (other dimension) is never used.
     */
    private net.minestom.server.instance.block.Block[] liveChunkBlocks(int chunkX, int chunkZ, int[] surfaceHeights) {
        var lookup = StructureWrites.terrainLookup();
        if (lookup == null || surfaceHeights == null) {
            return null;
        }

        try {
            var terrainData = lookup.terrain(chunkX, chunkZ);
            return terrainData.surfaceHeights() == surfaceHeights ? terrainData.blocks() : null;
        } catch (Exception exception) {
            return null;
        }
    }

    /** Vanilla ChunkPos.pack. */
    private static long packChunk(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    /**
     * Vanilla stores a chunk's structure references in a fastutil
     * {@code LongOpenHashSet}; iteration order over that set decides how
     * multiple starts of the same structure share the placement random.
     * The set is created with the default 32-slot table at the references
     * stage. During the pregen ladder the server sits paused (empty), so
     * chunks never unload between the references and features stages and the
     * set is never rebuilt from a save - the original 32-slot insertion
     * table's iteration order is what the features stage sees.
     */
    private static List<Reference> referenceIterationOrder(List<Reference> insertionOrder) {
        if (insertionOrder.size() <= 1) {
            return insertionOrder;
        }

        return hashSetOrder(insertionOrder, 32);
    }

    /**
     * Iteration order of a fastutil {@code LongOpenHashSet} built by adding
     * the references in the given order: mix hash with linear probing, then
     * the iterator returns the zero key first and table positions descending.
     */
    private static List<Reference> hashSetOrder(List<Reference> insertionOrder, int tableSize) {
        while (insertionOrder.size() > tableSize * 3 / 4) {
            tableSize <<= 1;
        }
        var mask = tableSize - 1;
        var table = new Reference[tableSize];
        Reference zeroKey = null;
        for (var reference : insertionOrder) {
            if (reference.packedChunk() == 0L) {
                zeroKey = reference;
                continue;
            }

            var pos = (int) mixHash(reference.packedChunk()) & mask;
            while (table[pos] != null) {
                pos = pos + 1 & mask;
            }
            table[pos] = reference;
        }

        var result = new ArrayList<Reference>(insertionOrder.size());
        if (zeroKey != null) {
            result.add(zeroKey);
        }
        for (var pos = tableSize - 1; pos >= 0; pos--) {
            if (table[pos] != null) {
                result.add(table[pos]);
            }
        }
        return result;
    }

    /** fastutil HashCommon.mix(long). */
    private static long mixHash(long value) {
        var hash = value * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        return hash ^ hash >>> 16;
    }

    private MineshaftStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer))).orElse(null);
    }

    /**
     * Vanilla {@code ChunkGenerator.createStructures} for a single chunk:
     * placement gate, weighted structure selection with retries, piece
     * generation and biome validation at the generation stub.
     */
    private MineshaftStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var seed = settings.randomState().seed();
        var placement = structureSet.placement();
        if (!placement.isStartChunk(chunkX, chunkZ, seed, settings.randomState().legacyRandomSource())) {
            return null;
        }

        var structures = structureSet.structures();
        if (structures.size() == 1) {
            return this.tryGenerate(structures.getFirst().structure(), chunkX, chunkZ, settings, biomeZoomer);
        }

        var options = new ArrayList<>(structures);
        var selectionRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        selectionRandom.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var total = 0;
        for (var option : options) {
            total += option.weight();
        }

        while (!options.isEmpty()) {
            var choice = selectionRandom.nextInt(total);
            var index = 0;
            for (var option : options) {
                choice -= option.weight();
                if (choice < 0) {
                    break;
                }
                index++;
            }

            var selected = options.get(index);
            var start = this.tryGenerate(selected.structure(), chunkX, chunkZ, settings, biomeZoomer);
            if (start != null) {
                return start;
            }

            options.remove(index);
            total -= selected.weight();
        }

        return null;
    }

    private MineshaftStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof MineshaftStructure mineshaft)) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        // MineshaftStructure.findGenerationPoint consumes one double first
        // (the legacy inline frequency roll).
        random.nextDouble();

        var pieces = new ArrayList<MineshaftPieces.MineshaftPiece>();
        var room = new MineshaftPieces.Room(0, random, (chunkX << 4) + 2, (chunkZ << 4) + 2, mineshaft.type());
        pieces.add(room);
        room.addChildren(room, pieces, random);

        var seaLevel = settings.seaLevel();
        var bounds = boundsOf(pieces);
        int yOffset;
        if (mineshaft.type() == MineshaftType.MESA) {
            // Center per vanilla BoundingBox.getCenter
            var centerX = bounds.minX() + (bounds.maxX() - bounds.minX() + 1) / 2;
            var centerY = bounds.minY() + (bounds.maxY() - bounds.minY() + 1) / 2;
            var centerZ = bounds.minZ() + (bounds.maxZ() - bounds.minZ() + 1) / 2;
            var surfaceHeight = this.worldSurfaceHeight(centerX, centerZ, settings);
            var targetY = surfaceHeight <= seaLevel ? seaLevel
                    : random.nextInt(surfaceHeight - seaLevel + 1) + seaLevel;
            yOffset = targetY - centerY;
        } else {
            // StructurePiecesBuilder.moveBelowSeaLevel(seaLevel, minY, random, 10)
            var maxTargetY = seaLevel - 10;
            var targetTop = bounds.getYSpan() + settings.minY() + 1;
            if (targetTop < maxTargetY) {
                targetTop += random.nextInt(maxTargetY - targetTop);
            }
            yOffset = targetTop - bounds.maxY();
        }

        for (var piece : pieces) {
            piece.move(0, yOffset, 0);
        }

        // Biome validity at the generation stub (middle X, y=50+offset, min Z)
        // sampled from the unzoomed noise biome source at quart resolution.
        var stubX = (chunkX << 4) + 8;
        var stubY = 50 + yOffset;
        var stubZ = chunkZ << 4;
        var biome = biomeZoomer.source().biome(stubX >> 2, stubY >> 2, stubZ >> 2);
        if (!mineshaft.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        return new MineshaftStart(structureKey, List.copyOf(pieces), boundsOf(pieces));
    }

    /**
     * Approximation of vanilla {@code getBaseHeight(WORLD_SURFACE_WG)} for the
     * mesa variant: one above the highest solid or fluid block of the
     * uncarved noise terrain.
     */
    private int worldSurfaceHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var terrainData = new TerrainGenerator(settings).generate(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        var index = (blockX & 15) * 16 + (blockZ & 15);
        var solidTop = terrainData.surfaceHeights()[index];
        var fluidTop = terrainData.waterHeights()[index];
        var surface = Math.max(solidTop == Integer.MIN_VALUE ? Integer.MIN_VALUE : solidTop + 1, fluidTop);
        return surface == Integer.MIN_VALUE ? settings.minY() : surface;
    }

    private static BoundingBox boundsOf(List<MineshaftPieces.MineshaftPiece> pieces) {
        var first = pieces.getFirst().boundingBox();
        var bounds = new BoundingBox(first.minX(), first.minY(), first.minZ(), first.maxX(), first.maxY(), first.maxZ());
        for (var index = 1; index < pieces.size(); index++) {
            bounds.encapsulate(pieces.get(index).boundingBox());
        }
        return bounds;
    }

    private Carvers carvers(NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var carvers = this.carvers;
        if (carvers != null) {
            return carvers;
        }

        synchronized (this) {
            if (this.carvers == null) {
                var dataPack = this.featureLoader.dataPack();
                var biomeSource = biomeZoomer.source() instanceof CachedBiomeSource cached
                        ? cached
                        : new CachedBiomeSource(biomeZoomer.source(), settings.minY(), settings.height());
                this.carvers = new Carvers(settings, biomeSource, new DataPackBiomeResolver(dataPack), biomeZoomer,
                        new CarverLoader(dataPack, this.featureLoader.blockTags()));
            }
            return this.carvers;
        }
    }

    /**
     * The {@code underground_structures}-step structures in registry order
     * (vanilla loads worldgen registries sorted by resource path), which
     * determines each structure's feature-seed index for the step.
     */
    private List<Key> undergroundStructures() {
        var cached = this.undergroundStructures;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (this.undergroundStructures == null) {
                this.undergroundStructures = this.loadUndergroundStructures();
            }
            return this.undergroundStructures;
        }
    }

    private List<Key> loadUndergroundStructures() {
        return this.structureLoader.structuresAtStep("underground_structures");
    }

    private record MineshaftStart(Key structureKey, List<MineshaftPieces.MineshaftPiece> pieces, BoundingBox bounds) {
    }


    private static String rngState(rocks.minestom.worldgen.random.RandomSource random) {
        try {
            var sourceField = rocks.minestom.worldgen.random.WorldgenRandom.class.getDeclaredField("randomSource");
            sourceField.setAccessible(true);
            var source = sourceField.get(random);
            var generatorField = source.getClass().getDeclaredField("randomNumberGenerator");
            generatorField.setAccessible(true);
            var generator = generatorField.get(source);
            var loField = generator.getClass().getDeclaredField("seedLo");
            var hiField = generator.getClass().getDeclaredField("seedHi");
            loField.setAccessible(true);
            hiField.setAccessible(true);
            return loField.getLong(generator) + "," + hiField.getLong(generator);
        } catch (ReflectiveOperationException exception) {
            return "?";
        }
    }

    private record Reference(MineshaftStart start, long packedChunk) {
    }
}
