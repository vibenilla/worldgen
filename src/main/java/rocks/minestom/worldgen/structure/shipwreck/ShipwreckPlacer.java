package rocks.minestom.worldgen.structure.shipwreck;

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
import rocks.minestom.worldgen.structure.processor.BlockIgnoreProcessor;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureShapeUpdater;
import rocks.minestom.worldgen.structure.template.StructureTemplate;
import rocks.minestom.worldgen.terrain.TerrainData;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places shipwrecks with vanilla-exact seeding, mirroring
 * {@code rocks.minestom.worldgen.structure.oceanruin.OceanRuinPlacer}: the
 * start (rotation, template pick) is a pure function of the chunk, but unlike
 * ocean ruins, vanilla's {@code ShipwreckPiece.postProcess} does draw from
 * the decoration-time random for the beached height jitter, so this placer
 * replicates the {@code surface_structures} feature-seed draw the same way
 * {@code MonumentPlacer} and {@code ScatteredFeaturePlacer} do.
 */
public final class ShipwreckPlacer {
    /** The largest shipwreck template stays well within 2 chunks. */
    private static final int REFERENCE_RADIUS = 2;
    /** GenerationStep.Decoration.SURFACE_STRUCTURES ordinal. */
    private static final int SURFACE_STRUCTURES_STEP = 4;

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final Map<Long, Optional<ShipwreckStart>> starts = new ConcurrentHashMap<>();
    private final Map<Long, TerrainData> terrainCache = new ConcurrentHashMap<>();
    private volatile List<Key> surfaceStructures;

    public ShipwreckPlacer(StructureLoader structureLoader, FeatureLoader featureLoader) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
    }

    public boolean isShipwreckSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof ShipwreckStructure) {
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
        var chunkBounds = new BoundingBox(startX, settings.minY(), startZ,
                startX + 15, settings.maxYInclusive(), startZ + 15);

        var intersecting = new ArrayList<ShipwreckStart>();
        for (var sourceX = chunkX - REFERENCE_RADIUS; sourceX <= chunkX + REFERENCE_RADIUS; sourceX++) {
            for (var sourceZ = chunkZ - REFERENCE_RADIUS; sourceZ <= chunkZ + REFERENCE_RADIUS; sourceZ++) {
                var start = this.startAt(sourceX, sourceZ, structureSet, settings, biomeZoomer);
                if (start != null && start.bounds().intersects(chunkBounds)) {
                    intersecting.add(start);
                }
            }
        }

        if (intersecting.isEmpty()) {
            return;
        }

        var chunkBlocks = this.liveChunkBlocks(chunkX, chunkZ, surfaceHeights);
        var adapter = chunkBlocks != null
                ? new GenerationUnitAdapter(unit, startX, startZ, 16, 16, settings.minY(), chunkBlocks,
                        settings.height(), StructureWrites.terrainLookup())
                : new GenerationUnitAdapter(unit);

        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);
        var connectionShapeUpdates = new ArrayList<BlockVec>();

        for (var start : intersecting) {
            var index = this.surfaceStructures().indexOf(start.structureKey());
            if (index < 0) {
                continue;
            }
            random.setFeatureSeed(decorationSeed, index, SURFACE_STRUCTURES_STEP);
            this.placeStart(start, adapter, chunkBounds, settings, random, connectionShapeUpdates);
        }

        if (!connectionShapeUpdates.isEmpty()) {
            StructureShapeUpdater.update(adapter, this.structureLoader.blockTags(), connectionShapeUpdates);
        }
    }

    private void placeStart(ShipwreckStart start, GenerationUnitAdapter adapter, BoundingBox chunkBounds,
            NoiseGeneratorSettingsRuntime settings, WorldgenRandom random, List<BlockVec> connectionShapeUpdates) {
        var piece = start.piece();
        if (!start.bounds().intersects(chunkBounds)) {
            return;
        }

        var template = this.structureLoader.getTemplate(piece.template());
        if (template == null) {
            return;
        }

        var finalY = this.resolveHeight(start, piece, template, settings, random);
        var position = new BlockVec(piece.position().blockX(), finalY, piece.position().blockZ());

        var processorContext = new StructureProcessorContext(
                adapter, this.structureLoader.blockTags(), settings.randomState().seed(),
                position, position, null);
        var placementContext = new StructureTemplate.PlacementContext(
                adapter, chunkBounds, processorContext, connectionShapeUpdates);
        var processors = new StructureProcessorList(List.of(BlockIgnoreProcessor.STRUCTURE_AND_AIR));
        template.place(placementContext, position, piece.rotation(), processors, false, false,
                LiquidSettings.APPLY_WATERLOGGING, true);
    }

    /**
     * Vanilla {@code ShipwreckPiece.postProcess}: the mean {@code OCEAN_FLOOR_WG}
     * (or {@code WORLD_SURFACE_WG} when beached) height across the footprint,
     * or - when beached - {@code minY - templateSizeY / 2 - random.nextInt(3)}
     * drawn from the decoration-time random.
     */
    private int resolveHeight(ShipwreckStart start, ShipwreckPieces.Piece piece, StructureTemplate template,
            NoiseGeneratorSettingsRuntime settings, WorldgenRandom random) {
        var size = template.size();
        var baseX = piece.position().blockX();
        var baseZ = piece.position().blockZ();
        var minY = Integer.MAX_VALUE;
        var sum = 0L;
        var count = 0;
        for (var x = baseX; x < baseX + size.blockX(); x++) {
            for (var z = baseZ; z < baseZ + size.blockZ(); z++) {
                var height = start.beached()
                        ? this.worldSurfaceHeight(x, z, settings)
                        : this.oceanFloorHeight(x, z, settings);
                sum += height;
                count++;
                minY = Math.min(minY, height);
            }
        }

        if (count == 0) {
            return start.beached()
                    ? this.worldSurfaceHeight(baseX, baseZ, settings)
                    : this.oceanFloorHeight(baseX, baseZ, settings);
        }

        if (start.beached()) {
            return minY - size.blockY() / 2 - random.nextInt(3);
        }
        return (int) (sum / count);
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

    private ShipwreckStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer)))
                .orElse(null);
    }

    private ShipwreckStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof ShipwreckStructure) {
                var start = this.tryGenerate(selected.structure(), chunkX, chunkZ, settings, biomeZoomer);
                if (start != null) {
                    return start;
                }
            }

            options.remove(index);
            total -= selected.weight();
        }
        return null;
    }

    /**
     * Vanilla {@code ShipwreckStructure.findGenerationPoint}: the biome check
     * runs at the chunk-center height of whichever heightmap the variant
     * uses, then {@code generatePieces} draws a rotation and the single
     * random template pick, continuing the same random.
     */
    private ShipwreckStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof ShipwreckStructure shipwreck)) {
            return null;
        }

        var centerX = (chunkX << 4) + 8;
        var centerZ = (chunkZ << 4) + 8;
        var centerY = shipwreck.isBeached()
                ? this.worldSurfaceHeight(centerX, centerZ, settings)
                : this.oceanFloorHeight(centerX, centerZ, settings);
        var biome = biomeZoomer.source().biome(centerX >> 2, centerY >> 2, centerZ >> 2);
        if (!shipwreck.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var rotation = Rotation.getRandom(random);
        var position = new BlockVec(chunkX << 4, 90, chunkZ << 4);
        var rawPiece = ShipwreckPieces.addRandomPiece(position, rotation, random, shipwreck.isBeached());

        var template = this.structureLoader.getTemplate(rawPiece.template());
        if (template == null) {
            return null;
        }

        // Vanilla ShipwreckPieces.makeSettings: rotation is applied around
        // the (4, 0, 15) pivot rather than the template origin. This
        // codebase's rotate() always pivots at zero, so the equivalent
        // shift is folded into the origin: world = origin + pivot -
        // rotate(pivot) + rotate(local), which matches vanilla's
        // rotate(local - pivot) + pivot once expanded.
        var pivotOffset = rotation.rotate(ShipwreckPieces.PIVOT, BlockVec.ZERO);
        var adjustedPosition = rawPiece.position().add(
                ShipwreckPieces.PIVOT.blockX() - pivotOffset.blockX(),
                ShipwreckPieces.PIVOT.blockY() - pivotOffset.blockY(),
                ShipwreckPieces.PIVOT.blockZ() - pivotOffset.blockZ());
        var piece = new ShipwreckPieces.Piece(rawPiece.template(), adjustedPosition, rotation);

        var bounds = template.getBoundingBox(piece.position(), piece.rotation());
        return new ShipwreckStart(structureKey, shipwreck.isBeached(), piece, copyBounds(bounds));
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

    private int oceanFloorHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = this.terrainData(chunkX, chunkZ, settings);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() : solidTop + 1;
    }

    /**
     * Approximation of vanilla {@code WORLD_SURFACE_WG}, matching every other
     * placer in this codebase: one above the highest solid block of the raw
     * noise terrain (fluids are not distinguished from solids here).
     */
    private int worldSurfaceHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        return this.oceanFloorHeight(blockX, blockZ, settings);
    }

    private TerrainData terrainData(int chunkX, int chunkZ, NoiseGeneratorSettingsRuntime settings) {
        return this.terrainCache.computeIfAbsent(packChunk(chunkX, chunkZ),
                unused -> new TerrainGenerator(settings).generate(chunkX, chunkZ));
    }

    private static BoundingBox copyBounds(BoundingBox bounds) {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private record ShipwreckStart(Key structureKey, boolean beached, ShipwreckPieces.Piece piece, BoundingBox bounds) {
    }
}
