package rocks.minestom.worldgen.structure.stronghold;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.StructureWrites;
import rocks.minestom.worldgen.structure.TerrainAdjustment;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.terrain.Beardifier;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places the single procedural stronghold with vanilla-exact seeding,
 * mirroring {@link rocks.minestom.worldgen.structure.mineshaft.MineshaftPlacer}.
 *
 * <p>Unlike the mineshaft, a stronghold start retries its whole layout
 * (vanilla {@code StrongholdStructure.generatePieces}) until it both places
 * at least one piece and contains a portal room, reseeding the piece random
 * with {@code seed + attempt} on every retry.
 */
public final class StrongholdPlacer {
    /** Vanilla structure reference radius: starts up to 8 chunks away can reach this chunk. */
    private static final int REFERENCE_RADIUS = 8;
    /** GenerationStep.Decoration.SURFACE_STRUCTURES ordinal. */
    private static final int SURFACE_STRUCTURES_STEP = 4;
    /** Vanilla StructurePiece.isCloseToChunk / beard kernel radius. */
    private static final int BEARD_CLOSE_DISTANCE = 12;

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final Map<Long, Optional<StrongholdStart>> starts = new ConcurrentHashMap<>();
    private volatile List<Key> surfaceStructuresStructures;

    public StrongholdPlacer(StructureLoader structureLoader, FeatureLoader featureLoader) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
    }

    /**
     * Whether the given structure set contains the procedural stronghold
     * structure and should be routed through this placer.
     */
    public boolean isStrongholdSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof StrongholdStructure) {
                return true;
            }
        }
        return false;
    }

    public void place(GenerationUnit unit, BiomeZoomer biomeZoomer,
            NoiseGeneratorSettingsRuntime settings, StructureSet structureSet, int[] surfaceHeights) {
        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var chunkX = Math.floorDiv(startX, 16);
        var chunkZ = Math.floorDiv(startZ, 16);

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

        var chunkBlocks = this.liveChunkBlocks(chunkX, chunkZ, surfaceHeights);
        var usingLiveBuffer = chunkBlocks != null;
        if (!usingLiveBuffer) {
            chunkBlocks = new TerrainGenerator(settings).generate(chunkX, chunkZ).blocks();
        }

        var forkUnit = unit.fork(
                new BlockVec(startX, settings.minY(), startZ),
                new BlockVec(startX + 16, settings.maxYInclusive() + 1, startZ + 16));
        var forkAdapter = new GenerationUnitAdapter(forkUnit);

        var replayHandle = usingLiveBuffer ? null : surfaceHeights;
        var level = new StrongholdLevel(forkAdapter, chunkBlocks, startX, startZ,
                settings.minY(), settings.maxYInclusive(), replayHandle);
        var chunkBB = new BoundingBox(startX, settings.minY() + 1, startZ,
                startX + 15, settings.maxYInclusive(), startZ + 15);

        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);

        for (var structureKey : this.surfaceStructuresStructures()) {
            var references = new ArrayList<Reference>();
            for (var reference : intersecting) {
                if (reference.start().structureKey().equals(structureKey)) {
                    references.add(reference);
                }
            }

            if (references.isEmpty()) {
                continue;
            }

            var index = this.surfaceStructuresStructures().indexOf(structureKey);
            random.setFeatureSeed(decorationSeed, index, SURFACE_STRUCTURES_STEP);

            for (var reference : referenceIterationOrder(references)) {
                for (var piece : reference.start().pieces()) {
                    if (piece.boundingBox().intersects(chunkBB)) {
                        piece.postProcess(level, random, chunkBB);
                    }
                }
            }
        }
    }

    /**
     * Contributes the beard-adaptation rigid boxes for stronghold pieces
     * close to the given chunk, mirroring vanilla {@code
     * Beardifier.forStructuresInChunk} for a plain (non pool-element)
     * structure piece: every piece within {@link #BEARD_CLOSE_DISTANCE}
     * blocks of the chunk contributes its whole bounding box with no ground
     * level delta.
     */
    public void contributeBeard(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer, List<Beardifier.Rigid> rigids) {
        var chunkStartBlockX = chunkX << 4;
        var chunkStartBlockZ = chunkZ << 4;

        for (var sourceX = chunkX - REFERENCE_RADIUS; sourceX <= chunkX + REFERENCE_RADIUS; sourceX++) {
            for (var sourceZ = chunkZ - REFERENCE_RADIUS; sourceZ <= chunkZ + REFERENCE_RADIUS; sourceZ++) {
                var start = this.startAt(sourceX, sourceZ, structureSet, settings, biomeZoomer);
                if (start == null || !intersectsColumn(start.bounds(), chunkStartBlockX, chunkStartBlockZ)) {
                    continue;
                }

                for (var piece : start.pieces()) {
                    if (intersectsXZ(piece.boundingBox(), chunkStartBlockX - BEARD_CLOSE_DISTANCE,
                            chunkStartBlockZ - BEARD_CLOSE_DISTANCE,
                            chunkStartBlockX + 15 + BEARD_CLOSE_DISTANCE, chunkStartBlockZ + 15 + BEARD_CLOSE_DISTANCE)) {
                        rigids.add(new Beardifier.Rigid(piece.boundingBox(), TerrainAdjustment.BURY, 0));
                    }
                }
            }
        }
    }

    private static boolean intersectsXZ(BoundingBox bounds, int minX, int minZ, int maxX, int maxZ) {
        return bounds.maxX() >= minX && bounds.minX() <= maxX
                && bounds.maxZ() >= minZ && bounds.minZ() <= maxZ;
    }

    private static boolean intersectsColumn(BoundingBox bounds, int blockX, int blockZ) {
        return bounds.maxX() >= blockX && bounds.minX() <= blockX + 15
                && bounds.maxZ() >= blockZ && bounds.minZ() <= blockZ + 15;
    }

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

    private static long packChunk(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    /** See {@code MineshaftPlacer.referenceIterationOrder}: emulates fastutil LongOpenHashSet iteration order. */
    private static List<Reference> referenceIterationOrder(List<Reference> insertionOrder) {
        if (insertionOrder.size() <= 1) {
            return insertionOrder;
        }
        var order = hashSetOrder(insertionOrder, 32);
        for (var reload = 0; reload < 8; reload++) {
            var rebuilt = hashSetOrder(order, reloadTableSize(order.size()));
            if (rebuilt.equals(order)) {
                break;
            }
            order = rebuilt;
        }
        return order;
    }

    private static int reloadTableSize(int count) {
        var needed = (int) Math.ceil(count / 0.75);
        var size = 2;
        while (size < needed) {
            size <<= 1;
        }
        return size;
    }

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

    private static long mixHash(long value) {
        var hash = value * 0x9E3779B97F4A7C15L;
        hash ^= hash >>> 32;
        return hash ^ hash >>> 16;
    }

    private StrongholdStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer))).orElse(null);
    }

    /**
     * Vanilla {@code StrongholdStructure.generatePieces}: a single-entry
     * structure set (no weighted draw), a fixed-position biome check that
     * consumes no randomness, and then a retry loop that reseeds the piece
     * random with {@code seed + attempt} until the layout both places at
     * least one piece and contains a portal room.
     */
    private StrongholdStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var seed = settings.randomState().seed();
        var placement = structureSet.placement();
        if (!placement.isStartChunk(chunkX, chunkZ, seed, settings.randomState().legacyRandomSource())) {
            return null;
        }

        var structureKey = structureSet.structures().getFirst().structure();
        if (!(this.structureLoader.getStructure(structureKey) instanceof StrongholdStructure stronghold)) {
            return null;
        }

        var stubX = chunkX << 4;
        var stubZ = chunkZ << 4;
        var biome = biomeZoomer.source().biome(stubX >> 2, 0, stubZ >> 2);
        if (!stronghold.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var pieces = StrongholdPieces.generatePieces(seed, chunkX, chunkZ, settings.seaLevel(), settings.minY());
        return new StrongholdStart(structureKey, List.copyOf(pieces), StrongholdPieces.boundsOf(pieces));
    }

    private List<Key> surfaceStructuresStructures() {
        var cached = this.surfaceStructuresStructures;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.surfaceStructuresStructures == null) {
                this.surfaceStructuresStructures = this.loadSurfaceStructuresStructures();
            }
            return this.surfaceStructuresStructures;
        }
    }

    /**
     * The {@code surface_structures}-step structures in registry order
     * (vanilla loads worldgen registries sorted by resource path), which
     * determines each structure's feature-seed index for the step.
     */
    private List<Key> loadSurfaceStructuresStructures() {
        var dataPack = this.featureLoader.dataPack();
        var keys = new ArrayList<Key>();
        var dataRoot = dataPack.rootPath().resolve("data");
        try (var namespaces = Files.list(dataRoot)) {
            for (var namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                var structureDir = namespaceDir.resolve("worldgen").resolve("structure");
                if (!Files.isDirectory(structureDir)) {
                    continue;
                }
                try (var files = Files.list(structureDir)) {
                    for (var file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                        var name = file.getFileName().toString();
                        keys.add(Key.key(namespaceDir.getFileName().toString(),
                                name.substring(0, name.length() - ".json".length())));
                    }
                }
            }
        } catch (Exception exception) {
            return List.of();
        }

        keys.sort((left, right) -> {
            var byPath = left.value().compareTo(right.value());
            return byPath != 0 ? byPath : left.namespace().compareTo(right.namespace());
        });

        var result = new ArrayList<Key>();
        for (var key : keys) {
            if (this.isSurfaceStructuresStructure(key)) {
                result.add(key);
            }
        }
        return List.copyOf(result);
    }

    private boolean isSurfaceStructuresStructure(Key key) {
        try {
            var json = this.featureLoader.dataPack().readStructure(key);
            return json.isJsonObject()
                    && json.getAsJsonObject().has("step")
                    && json.getAsJsonObject().get("step").getAsString().equals("surface_structures");
        } catch (Exception exception) {
            return false;
        }
    }

    private record StrongholdStart(Key structureKey, List<StrongholdPieces.StrongholdPiece> pieces, BoundingBox bounds) {
    }

    private record Reference(StrongholdStart start, long packedChunk) {
    }
}
