package rocks.minestom.worldgen.structure.monument;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.StructureWrites;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places the procedural ocean monument with vanilla-exact seeding, mirroring
 * {@code rocks.minestom.worldgen.structure.fortress.FortressPlacer}. Unlike
 * the fortress, vanilla's ocean monument structure registers a single top
 * piece ({@code MonumentBuilding}); that piece's own {@code postProcess}
 * dispatches to its child rooms internally, sharing one random draw sequence
 * per generating chunk, so this placer only ever has one piece per start.
 */
public final class MonumentPlacer {
    /** The 58-block building can reach a chunk from up to a few chunks away. */
    private static final int REFERENCE_RADIUS = 4;
    /** GenerationStep.Decoration.SURFACE_STRUCTURES ordinal. */
    private static final int SURFACE_STRUCTURES_STEP = 4;
    /** Vanilla {@code OceanMonumentPieces.MonumentBuilding.BIOME_RANGE_CHECK}. */
    private static final int BIOME_RANGE_CHECK = 29;

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final Map<Long, Optional<MonumentStart>> starts = new ConcurrentHashMap<>();
    private volatile List<Key> surfaceStructures;

    public MonumentPlacer(StructureLoader structureLoader, FeatureLoader featureLoader) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
    }

    public boolean isMonumentSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof OceanMonumentStructure) {
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
                if (start != null && intersectsColumn(start.building().boundingBox(), startX, startZ)) {
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
        var level = new MonumentLevel(forkAdapter, chunkBlocks, startX, startZ,
                settings.minY(), settings.maxYInclusive(), settings.seaLevel(), replayHandle);
        var chunkBB = new BoundingBox(startX, settings.minY() + 1, startZ,
                startX + 15, settings.maxYInclusive(), startZ + 15);

        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);
        var index = this.surfaceStructures().indexOf(Key.key("minecraft:monument"));
        if (index < 0) {
            return;
        }

        for (var reference : referenceIterationOrder(intersecting)) {
            var building = reference.start().building();
            if (building.boundingBox().intersects(chunkBB)) {
                random.setFeatureSeed(decorationSeed, index, SURFACE_STRUCTURES_STEP);
                building.postProcess(level, random, chunkBB);
            }
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

    private MonumentStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer))).orElse(null);
    }

    /**
     * Replicates the same weighted, per-chunk large-feature draw as
     * {@code StructurePlacer.computeStart} for this structure set. The
     * {@code ocean_monuments} set only ever contains the single
     * {@code minecraft:monument} structure in vanilla, but the weighted draw
     * is still replicated for parity with sets that add extra weight
     * entries.
     */
    private MonumentStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof OceanMonumentStructure monument) {
                return this.tryGenerateMonument(monument, chunkX, chunkZ, settings, biomeZoomer);
            }

            if (this.biomeMatchesApprox(selected.structure(), chunkX, chunkZ, settings, biomeZoomer)) {
                return null;
            }

            options.remove(index);
            total -= selected.weight();
        }
        return null;
    }

    private MonumentStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof OceanMonumentStructure monument)) {
            return null;
        }
        return this.tryGenerateMonument(monument, chunkX, chunkZ, settings, biomeZoomer);
    }

    /**
     * Vanilla {@code OceanMonumentStructure.findGenerationPoint} plus
     * {@code Structure.findValidGenerationPoint}: a 29-block-radius biome
     * surrounding check (every biome must carry
     * {@code required_ocean_monument_surrounding}), then a single biome
     * check at the chunk-center column's {@code OCEAN_FLOOR_WG} height
     * against the structure's own {@code biomes} predicate. Neither check
     * consumes randomness; the room graph (which does) is only built after
     * both succeed, matching vanilla's lazy generator.
     */
    private MonumentStart tryGenerateMonument(OceanMonumentStructure monument, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var chunkMinX = chunkX << 4;
        var chunkMinZ = chunkZ << 4;
        var offsetX = chunkMinX + 9;
        var offsetZ = chunkMinZ + 9;
        var seaLevel = settings.seaLevel();
        var requiredSurrounding = this.structureLoader.biomeTags().biomes(Key.key("minecraft:required_ocean_monument_surrounding"));

        var minQuartX = (offsetX - BIOME_RANGE_CHECK) >> 2;
        var maxQuartX = (offsetX + BIOME_RANGE_CHECK) >> 2;
        var minQuartY = (seaLevel - BIOME_RANGE_CHECK) >> 2;
        var maxQuartY = (seaLevel + BIOME_RANGE_CHECK) >> 2;
        var minQuartZ = (offsetZ - BIOME_RANGE_CHECK) >> 2;
        var maxQuartZ = (offsetZ + BIOME_RANGE_CHECK) >> 2;

        for (var quartZ = minQuartZ; quartZ <= maxQuartZ; quartZ++) {
            for (var quartX = minQuartX; quartX <= maxQuartX; quartX++) {
                for (var quartY = minQuartY; quartY <= maxQuartY; quartY++) {
                    var biome = biomeZoomer.source().biome(quartX, quartY, quartZ);
                    if (!requiredSurrounding.contains(biome)) {
                        return null;
                    }
                }
            }
        }

        var centerX = chunkMinX + 8;
        var centerZ = chunkMinZ + 8;
        var centerY = this.oceanFloorHeight(centerX, centerZ, settings);
        var centerBiome = biomeZoomer.source().biome(centerX >> 2, centerY >> 2, centerZ >> 2);
        if (!monument.biomes().matches(centerBiome, this.structureLoader.biomeTags())) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var direction = Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
        var building = OceanMonumentPieces.generateBuilding(random, chunkMinX - 29, chunkMinZ - 29, direction);
        return new MonumentStart(building);
    }

    /**
     * Approximation of vanilla {@code getFirstOccupiedHeight} for
     * {@code OCEAN_FLOOR_WG}: one above the highest solid block of the raw
     * noise terrain, matching {@code ScatteredFeaturePlacer.worldSurfaceHeight}'s
     * documented precedent.
     */
    private int oceanFloorHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = new TerrainGenerator(settings).generate(chunkX, chunkZ);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.seaLevel() : solidTop + 1;
    }

    private boolean biomeMatchesApprox(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var structure = this.structureLoader.getStructure(structureKey);
        if (structure == null) {
            return false;
        }
        var centerX = (chunkX << 4) + 8;
        var centerZ = (chunkZ << 4) + 8;
        var centerY = this.oceanFloorHeight(centerX, centerZ, settings);
        var biome = biomeZoomer.biome(centerX, centerY, centerZ);
        return structure.biomes().matches(biome, this.structureLoader.biomeTags());
    }

    private List<Key> surfaceStructures() {
        var cached = this.surfaceStructures;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.surfaceStructures == null) {
                this.surfaceStructures = this.loadStepStructures("surface_structures");
            }
            return this.surfaceStructures;
        }
    }

    /**
     * The {@code surface_structures}-step structures in registry order
     * (vanilla loads worldgen registries sorted by resource path), which
     * determines each structure's feature-seed index for the step. See
     * {@code ScatteredFeaturePlacer.loadStepStructures}.
     */
    private List<Key> loadStepStructures(String step) {
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
            if (this.isStepStructure(key, step)) {
                result.add(key);
            }
        }
        return List.copyOf(result);
    }

    private boolean isStepStructure(Key key, String step) {
        try {
            var json = this.featureLoader.dataPack().readStructure(key);
            return json.isJsonObject()
                    && json.getAsJsonObject().has("step")
                    && json.getAsJsonObject().get("step").getAsString().equals(step);
        } catch (Exception exception) {
            return false;
        }
    }

    private record MonumentStart(OceanMonumentPieces.MonumentBuilding building) {
    }

    private record Reference(MonumentStart start, long packedChunk) {
    }
}
