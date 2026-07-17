package rocks.minestom.worldgen.structure.scattered;

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
import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.StructureWrites;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.surface.DataPackBiomeResolver;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places the procedural desert pyramid, jungle temple, swamp hut and buried
 * treasure structures with vanilla-exact seeding, mirroring
 * {@code rocks.minestom.worldgen.structure.mineshaft.MineshaftPlacer} and
 * {@code FortressPlacer}: starts are pure functions of their chunk (computed
 * with a {@code setLargeFeatureSeed}-seeded random, matching vanilla
 * {@code Structure.GenerationContext.makeRandom}), while piece postProcess
 * runs at decoration time with a random seeded from the generating chunk's
 * decoration seed and the structure's index within its {@code GenerationStep}.
 */
public final class ScatteredFeaturePlacer {
    /** Structures reach at most a couple of chunks from their start. */
    private static final int REFERENCE_RADIUS = 3;
    /** GenerationStep.Decoration.UNDERGROUND_STRUCTURES ordinal. */
    private static final int UNDERGROUND_STRUCTURES_STEP = 3;
    /** GenerationStep.Decoration.SURFACE_STRUCTURES ordinal. */
    private static final int SURFACE_STRUCTURES_STEP = 4;

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final Map<Long, Optional<ScatteredStart>> starts = new ConcurrentHashMap<>();
    private volatile Carvers carvers;
    private volatile List<Key> surfaceStructures;
    private volatile List<Key> undergroundStructures;

    public ScatteredFeaturePlacer(StructureLoader structureLoader, FeatureLoader featureLoader) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
    }

    public boolean isScatteredFeatureSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            var structure = this.structureLoader.getStructure(entry.structure());
            if (structure instanceof ScatteredFeatureStructure || structure instanceof BuriedTreasureStructure) {
                return true;
            }
        }
        return false;
    }

    public void place(GenerationUnit unit, BiomeZoomer biomeZoomer, NoiseGeneratorSettingsRuntime settings,
            StructureSet structureSet, int[] surfaceHeights) {
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
            var terrainGenerator = new TerrainGenerator(settings);
            var terrainData = terrainGenerator.generate(chunkX, chunkZ);
            this.carvers(settings, biomeZoomer).applyCarvers(terrainData, chunkX, chunkZ, terrainGenerator.aquifer());
            chunkBlocks = terrainData.blocks();
        }

        var forkUnit = unit.fork(
                new BlockVec(startX, settings.minY(), startZ),
                new BlockVec(startX + 16, settings.maxYInclusive() + 1, startZ + 16));
        var forkAdapter = new GenerationUnitAdapter(forkUnit);
        var replayHandle = usingLiveBuffer ? null : surfaceHeights;
        var level = new ScatteredFeatureLevel(forkAdapter, chunkBlocks, startX, startZ,
                settings.minY(), settings.maxYInclusive(), replayHandle);
        var chunkBB = new BoundingBox(startX, settings.minY(), startZ,
                startX + 15, settings.maxYInclusive(), startZ + 15);

        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);

        for (var reference : intersecting) {
            var structureKey = reference.start().structureKey();
            var step = this.stepListFor(structureKey);
            if (step == null) {
                continue;
            }

            var index = step.list().indexOf(structureKey);
            if (index < 0) {
                continue;
            }

            random.setFeatureSeed(decorationSeed, index, step.ordinal());
            var piece = reference.start().piece();
            if (piece.boundingBox().intersects(chunkBB)) {
                piece.postProcess(level, random, chunkBB, settings.randomState().seed());
            }
        }
    }

    private record StepList(List<Key> list, int ordinal) {
    }

    private StepList stepListFor(Key structureKey) {
        if (this.surfaceStructures().contains(structureKey)) {
            return new StepList(this.surfaceStructures(), SURFACE_STRUCTURES_STEP);
        }
        if (this.undergroundStructures().contains(structureKey)) {
            return new StepList(this.undergroundStructures(), UNDERGROUND_STRUCTURES_STEP);
        }
        return null;
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

    private ScatteredStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer)))
                .orElse(null);
    }

    private ScatteredStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var seed = settings.randomState().seed();
        var placement = structureSet.placement();
        if (!placement.isStartChunk(chunkX, chunkZ, seed, settings.randomState().legacyRandomSource(),
                biomeZoomer.source(), this.structureLoader.biomeTags())) {
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

    private ScatteredStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var structure = this.structureLoader.getStructure(structureKey);
        var chunkMinX = chunkX << 4;
        var chunkMinZ = chunkZ << 4;
        var seed = settings.randomState().seed();

        if (structure instanceof BuriedTreasureStructure buriedTreasure) {
            var stubY = this.worldSurfaceHeight(chunkMinX + 8, chunkMinZ + 8, settings);
            var biome = biomeZoomer.source().biome((chunkMinX + 8) >> 2, stubY >> 2, (chunkMinZ + 8) >> 2);
            if (!buriedTreasure.biomes().matches(biome, this.structureLoader.biomeTags())) {
                return null;
            }

            var anchor = BuriedTreasurePiece.anchor(chunkMinX, chunkMinZ);
            var piece = new AssembledPiece.OfBuriedTreasure(new BuriedTreasurePiece(anchor.blockX(), anchor.blockZ()));
            var bounds = new BoundingBox(anchor.blockX(), settings.minY(), anchor.blockZ(),
                    anchor.blockX(), settings.maxYInclusive(), anchor.blockZ());
            return new ScatteredStart(structureKey, piece, bounds);
        }

        if (!(structure instanceof ScatteredFeatureStructure scattered)) {
            return null;
        }

        var contextRandom = new WorldgenRandom(new LegacyRandomSource(0L));
        contextRandom.setLargeFeatureSeed(seed, chunkX, chunkZ);

        AssembledPiece piece;
        int width;
        int depth;
        switch (scattered.kind()) {
            case DESERT_PYRAMID -> {
                width = DesertPyramidPiece.WIDTH;
                depth = DesertPyramidPiece.DEPTH;
                if (this.getLowestY(chunkMinX, chunkMinZ, width, depth, settings) < settings.seaLevel()) {
                    return null;
                }
                piece = new AssembledPiece.OfDesertPyramid(new DesertPyramidPiece(contextRandom, chunkMinX, chunkMinZ));
            }
            case JUNGLE_TEMPLE -> {
                width = JungleTemplePiece.WIDTH;
                depth = JungleTemplePiece.DEPTH;
                if (this.getLowestY(chunkMinX, chunkMinZ, width, depth, settings) < settings.seaLevel()) {
                    return null;
                }
                piece = new AssembledPiece.OfJungleTemple(new JungleTemplePiece(contextRandom, chunkMinX, chunkMinZ));
            }
            case SWAMP_HUT -> piece = new AssembledPiece.OfSwampHut(
                    new SwampHutPiece(contextRandom, chunkMinX, chunkMinZ));
            default -> throw new IllegalStateException("unexpected kind " + scattered.kind());
        }

        var bounds = piece.boundingBox();
        var stubX = chunkMinX + 8;
        var stubZ = chunkMinZ + 8;
        var stubY = this.worldSurfaceHeight(stubX, stubZ, settings);
        var biome = biomeZoomer.source().biome(stubX >> 2, stubY >> 2, stubZ >> 2);
        if (!scattered.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        return new ScatteredStart(structureKey, piece, copyBounds(bounds));
    }

    /**
     * Vanilla {@code Structure.getLowestY}: the minimum of the four corner
     * {@code WORLD_SURFACE_WG} heights of the given footprint anchored at
     * (minX, minZ).
     */
    private int getLowestY(int minX, int minZ, int sizeX, int sizeZ, NoiseGeneratorSettingsRuntime settings) {
        var a = this.worldSurfaceHeight(minX, minZ, settings);
        var b = this.worldSurfaceHeight(minX, minZ + sizeZ, settings);
        var c = this.worldSurfaceHeight(minX + sizeX, minZ, settings);
        var d = this.worldSurfaceHeight(minX + sizeX, minZ + sizeZ, settings);
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    /**
     * Approximation of vanilla {@code getFirstOccupiedHeight} for
     * {@code WORLD_SURFACE_WG} (any non-air block, {@code oceanFloor=false})
     * or {@code OCEAN_FLOOR_WG} (solid, non-fluid blocks, {@code oceanFloor=true}):
     * one above the highest solid block of the raw noise terrain (fluids are
     * approximated as absent, matching {@link rocks.minestom.worldgen.structure.placement.StructurePlacer#surfaceYAt}'s
     * existing precedent of reading structure-start heights from
     * uncarved, undecorated terrain).
     */
    private int worldSurfaceHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = new TerrainGenerator(settings).generate(chunkX, chunkZ);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() : solidTop + 1;
    }

    private static BoundingBox copyBounds(BoundingBox bounds) {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
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

    private List<Key> undergroundStructures() {
        var cached = this.undergroundStructures;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.undergroundStructures == null) {
                this.undergroundStructures = this.loadStepStructures("underground_structures");
            }
            return this.undergroundStructures;
        }
    }

    /**
     * The step's structures in registry order (vanilla loads worldgen
     * registries sorted by resource path), which determines each
     * structure's feature-seed index within the step.
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

    private record ScatteredStart(Key structureKey, AssembledPiece piece, BoundingBox bounds) {
    }

    private record Reference(ScatteredStart start, long packedChunk) {
    }
}
