package rocks.minestom.worldgen.structure.fortress;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
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
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places the procedural nether fortress with vanilla-exact seeding, mirroring
 * {@link rocks.minestom.worldgen.structure.mineshaft.MineshaftPlacer}.
 *
 * <p>Fortresses share the {@code nether_complexes} structure set with the
 * jigsaw-based bastion remnant. Both structures draw from the same weighted,
 * per-chunk large-feature random, so this placer replicates that draw
 * independently (like {@code MineshaftPlacer} replicates the generic weighted
 * draw for its own set) rather than routing the whole set through a single
 * dispatcher. Because a full jigsaw assembly is out of scope for this
 * duplicate draw, a bastion candidate's success is approximated by a biome
 * check alone - the same simplification {@code StructurePlacer.startCandidate}
 * already documents for locate queries. {@link rocks.minestom.worldgen.structure.placement.StructurePlacer}
 * complements this by never letting its own generic (jigsaw/simple) path
 * build a structure at a chunk where the shared draw selects the fortress.
 */
public final class FortressPlacer {
    /** Vanilla structure reference radius: starts up to 8 chunks away can reach this chunk. */
    private static final int REFERENCE_RADIUS = 8;
    /** GenerationStep.Decoration.UNDERGROUND_DECORATION ordinal. */
    private static final int UNDERGROUND_DECORATION_STEP = 7;

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final Map<Long, Optional<FortressStart>> starts = new ConcurrentHashMap<>();
    private volatile List<Key> undergroundDecorationStructures;

    public FortressPlacer(StructureLoader structureLoader, FeatureLoader featureLoader) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
    }

    /**
     * Whether the given structure set contains the procedural fortress
     * structure and should also be routed through this placer (in addition
     * to the generic jigsaw path, which still handles any other structure in
     * the same set, e.g. bastion remnant).
     */
    public boolean isFortressSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof FortressStructure) {
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
        // The connection shape pass (fences) reads neighbors across chunk
        // boundaries, so the adapter needs the same live cross-chunk terrain
        // access as regular feature decoration, not just an unbacked getter
        // that always reads air.
        var forkAdapter = new GenerationUnitAdapter(forkUnit, startX, startZ, 16, 16, settings.minY(),
                chunkBlocks, settings.height(), StructureWrites.terrainLookup());

        var replayHandle = usingLiveBuffer ? null : surfaceHeights;
        var shapeUpdatePositions = new ArrayList<BlockVec>();
        var level = new FortressLevel(forkAdapter, chunkBlocks, startX, startZ,
                settings.minY(), settings.maxYInclusive(), replayHandle, shapeUpdatePositions);
        var chunkBB = new BoundingBox(startX, settings.minY() + 1, startZ,
                startX + 15, settings.maxYInclusive(), startZ + 15);

        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);

        for (var structureKey : this.undergroundDecorationStructures()) {
            var references = new ArrayList<Reference>();
            for (var reference : intersecting) {
                if (reference.start().structureKey().equals(structureKey)) {
                    references.add(reference);
                }
            }

            if (references.isEmpty()) {
                continue;
            }

            var index = this.undergroundDecorationStructures().indexOf(structureKey);
            random.setFeatureSeed(decorationSeed, index, UNDERGROUND_DECORATION_STEP);

            for (var reference : referenceIterationOrder(references)) {
                for (var piece : reference.start().pieces()) {
                    if (piece.boundingBox().intersects(chunkBB)) {
                        piece.postProcess(level, random, chunkBB);
                    }
                }
            }
        }

        if (!shapeUpdatePositions.isEmpty()) {
            StructureShapeUpdater.update(forkAdapter, this.structureLoader.blockTags(), shapeUpdatePositions);
        }
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
        return hashSetOrder(insertionOrder, 32);
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

    private FortressStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer))).orElse(null);
    }

    /**
     * Replicates the same weighted, per-chunk large-feature draw as
     * {@code StructurePlacer.computeStart} for this structure set, stopping
     * as soon as any candidate (fortress or otherwise) would succeed - a
     * bastion candidate's success is approximated by a biome check only.
     */
    private FortressStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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

        while (!options.isEmpty() && total > 0) {
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof FortressStructure fortress) {
                return this.tryGenerateFortress(fortress, chunkX, chunkZ, settings, biomeZoomer);
            }

            // Another structure in the set (e.g. bastion remnant) was
            // selected instead; approximate its success with a biome check
            // only, matching StructurePlacer.startCandidate's documented
            // simplification. Success means no fortress here at all.
            if (this.biomeMatchesApprox(selected.structure(), chunkX, chunkZ, settings, biomeZoomer)) {
                return null;
            }

            options.remove(index);
            total -= selected.weight();
        }
        return null;
    }

    private FortressStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof FortressStructure fortress)) {
            return null;
        }
        return this.tryGenerateFortress(fortress, chunkX, chunkZ, settings, biomeZoomer);
    }

    /**
     * Vanilla {@code NetherFortressStructure.findGenerationPoint}: a fixed
     * stub position (chunk min X/Z, y=64) independent of any piece geometry,
     * so the biome check never depends on random state and a failed check
     * consumes no randomness (matching vanilla, where the piece-generating
     * consumer only runs after the biome check passes).
     */
    private FortressStart tryGenerateFortress(FortressStructure fortress, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var stubX = chunkX << 4;
        var stubY = 64;
        var stubZ = chunkZ << 4;
        var biome = biomeZoomer.source().biome(stubX >> 2, stubY >> 2, stubZ >> 2);
        if (!fortress.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var pieces = NetherFortressPieces.generatePieces(random, chunkX, chunkZ);
        var bounds = NetherFortressPieces.boundsOf(pieces);
        return new FortressStart(Key.key("minecraft:fortress"), List.copyOf(pieces), bounds);
    }

    /**
     * Approximate success check for a non-fortress candidate in the same
     * weighted draw (e.g. bastion remnant): biome match at the candidate's
     * terrain surface, without running its actual assembly. See the class
     * doc for why this is an accepted simplification.
     */
    private boolean biomeMatchesApprox(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var structure = this.structureLoader.getStructure(structureKey);
        if (structure == null) {
            return false;
        }
        var centerX = (chunkX << 4) + 8;
        var centerZ = (chunkZ << 4) + 8;
        var surfaceY = this.surfaceYAt(chunkX, chunkZ, settings);
        var biome = biomeZoomer.biome(centerX, surfaceY, centerZ);
        return structure.biomes().matches(biome, this.structureLoader.biomeTags());
    }

    private int surfaceYAt(int chunkX, int chunkZ, NoiseGeneratorSettingsRuntime settings) {
        var surfaceY = new TerrainGenerator(settings).generate(chunkX, chunkZ).surfaceHeights()[8 * 16 + 8];
        return surfaceY == Integer.MIN_VALUE ? settings.seaLevel() : surfaceY;
    }

    private List<Key> undergroundDecorationStructures() {
        var cached = this.undergroundDecorationStructures;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.undergroundDecorationStructures == null) {
                this.undergroundDecorationStructures = this.loadUndergroundDecorationStructures();
            }
            return this.undergroundDecorationStructures;
        }
    }

    /**
     * The {@code underground_decoration}-step structures in registry order
     * (vanilla loads worldgen registries sorted by resource path), which
     * determines each structure's feature-seed index for the step. See
     * {@code MineshaftPlacer.loadUndergroundStructures}.
     */
    private List<Key> loadUndergroundDecorationStructures() {
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
            if (this.isUndergroundDecorationStructure(key)) {
                result.add(key);
            }
        }
        return List.copyOf(result);
    }

    private boolean isUndergroundDecorationStructure(Key key) {
        try {
            var json = this.featureLoader.dataPack().readStructure(key);
            return json.isJsonObject()
                    && json.getAsJsonObject().has("step")
                    && json.getAsJsonObject().get("step").getAsString().equals("underground_decoration");
        } catch (Exception exception) {
            return false;
        }
    }

    private record FortressStart(Key structureKey, List<NetherFortressPieces.FortressPiece> pieces, BoundingBox bounds) {
    }

    private record Reference(FortressStart start, long packedChunk) {
    }
}
