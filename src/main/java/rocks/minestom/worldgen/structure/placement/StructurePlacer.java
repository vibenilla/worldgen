package rocks.minestom.worldgen.structure.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.structure.*;
import rocks.minestom.worldgen.structure.assembly.JigsawAssembler;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates structure placement during world generation.
 *
 * <p>
 * The placer manages the complete lifecycle of structure generation:
 * <ol>
 * <li>Checks if each chunk is a valid structure start using
 * {@link StructurePlacement}
 * <li>Validates biome compatibility
 * <li>Assembles structures and caches the pieces
 * <li>Places cached pieces when their chunks generate
 * </ol>
 *
 * <p>
 * Structures are cached because they often span multiple chunks. When the start
 * chunk generates, the entire structure is assembled and stored. Then each
 * overlapping
 * chunk places only the pieces within its bounds.
 *
 * @see StructureSet for structure grouping and placement rules
 * @see Structure for the structure types
 */
public final class StructurePlacer {
    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final List<Key> structureSets;
    private final Map<StartKey, StructureStart> structureStarts;

    public StructurePlacer(StructureLoader structureLoader, FeatureLoader featureLoader, List<Key> structureSets) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
        this.structureSets = structureSets;
        this.structureStarts = new ConcurrentHashMap<>();
    }

    public void placeStructures(GenerationUnit unit, int[] surfaceHeights, BiomeZoomer biomeZoomer,
            NoiseGeneratorSettingsRuntime settings) {
        if (this.structureSets.isEmpty()) {
            return;
        }

        var startX = unit.absoluteStart().blockX();
        var startZ = unit.absoluteStart().blockZ();
        var chunkX = Math.floorDiv(startX, 16);
        var chunkZ = Math.floorDiv(startZ, 16);
        var centerX = startX + 8;
        var centerZ = startZ + 8;
        var localCenterX = centerX - startX;
        var localCenterZ = centerZ - startZ;
        var surfaceIndex = localCenterX * unit.size().blockZ() + localCenterZ;
        var surfaceY = surfaceHeights[surfaceIndex];
        if (surfaceY == Integer.MIN_VALUE) {
            surfaceY = settings.seaLevel();
        }

        var biomeKey = biomeZoomer.biome(centerX, surfaceY, centerZ);
        var chunkBounds = new BoundingBox(
                startX,
                settings.minY(),
                startZ,
                startX + unit.size().blockX() - 1,
                settings.maxYInclusive(),
                startZ + unit.size().blockZ() - 1);
        var adapter = new GenerationUnitAdapter(unit);

        for (var structureSetId : this.structureSets) {
            var structureSet = this.structureLoader.getStructureSet(structureSetId);
            if (structureSet == null) {
                continue;
            }

            var placement = structureSet.placement();
            if (placement.isStartChunk(chunkX, chunkZ, settings.randomState().seed(),
                    settings.randomState().legacyRandomSource())) {
                var structureKey = this.pickStructure(structureSetId, structureSet, settings);
                if (structureKey == null) {
                    continue;
                }

                var structure = this.structureLoader.getStructure(structureKey);
                if (structure == null) {
                    continue;
                }

                if (!structure.biomes().matches(biomeKey, this.structureLoader.biomeTags())) {
                    continue;
                }

                var startY = this.resolveStartY(structure, surfaceY, settings);
                var start = new BlockVec(centerX, startY, centerZ);
                var startKey = new StartKey(chunkKey(chunkX, chunkZ), structureSetId);
                var structureStart = this.buildStructureStart(structure, start, adapter, settings);
                if (structureStart != null) {
                    this.structureStarts.put(startKey, structureStart);
                }
            }

            this.placeCachedStructures(structureSetId, chunkBounds, adapter, settings, surfaceHeights, startX, startZ,
                    unit.size().blockX(), unit.size().blockZ());
        }
    }

    private StructureStart buildStructureStart(Structure structure, BlockVec start, GenerationUnitAdapter adapter,
            NoiseGeneratorSettingsRuntime settings) {
        var context = new StructurePlaceContext(
                adapter,
                settings,
                this.structureLoader,
                this.featureLoader,
                start,
                settings.randomState().getOrCreateRandomFactory(Key.key("minecraft:structure")));

        if (structure instanceof JigsawStructure jigsaw) {
            return this.buildJigsawStructureStart(jigsaw, start, context);
        }

        if (structure instanceof SimpleStructure simple) {
            return this.buildSimpleStructureStart(simple, start, context);
        }

        return null;
    }

    private StructureStart buildJigsawStructureStart(JigsawStructure jigsaw, BlockVec start,
            StructurePlaceContext context) {
        var assembler = new JigsawAssembler(context, jigsaw.size(), jigsaw.maxDistanceFromCenter());
        var assembly = assembler.assemblePieces(jigsaw.startPool());
        var pieces = assembly.pieces();
        var features = assembly.features();
        if (pieces.isEmpty()) {
            return null;
        }

        var firstBounds = pieces.getFirst().bounds();
        var bounds = new BoundingBox(
                firstBounds.minX(),
                firstBounds.minY(),
                firstBounds.minZ(),
                firstBounds.maxX(),
                firstBounds.maxY(),
                firstBounds.maxZ());
        for (var pieceIndex = 1; pieceIndex < pieces.size(); pieceIndex++) {
            bounds.encapsulate(pieces.get(pieceIndex).bounds());
        }

        for (var feature : features) {
            bounds.encapsulate(feature.bounds());
        }

        return new StructureStart(start, pieces, features, bounds);
    }

    private StructureStart buildSimpleStructureStart(SimpleStructure simple, BlockVec start,
            StructurePlaceContext context) {
        if (simple.templates().isEmpty()) {
            return null;
        }

        var random = context.randomFactory().fromHashOf(start.blockX() + ":" + start.blockZ());
        var templateKey = simple.templates().get(random.nextInt(simple.templates().size()));
        var template = this.structureLoader.getTemplate(templateKey);
        if (template == null) {
            return null;
        }

        var rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        var bounds = template.getBoundingBox(start, rotation);

        var piece = new JigsawAssembler.PlacedPiece(
                template,
                templateKey,
                start,
                rotation,
                bounds,
                StructureProcessorList.EMPTY,
                0,
                false,
                0);

        return new StructureStart(start, List.of(piece), List.of(), bounds);
    }

    private void placeCachedStructures(Key structureSetId, BoundingBox chunkBounds, GenerationUnitAdapter adapter,
            NoiseGeneratorSettingsRuntime settings, int[] surfaceHeights, int chunkStartX, int chunkStartZ,
            int chunkSizeX, int chunkSizeZ) {
        for (var entry : this.structureStarts.entrySet()) {
            if (!entry.getKey().structureSetId().equals(structureSetId)) {
                continue;
            }

            var structureStart = entry.getValue();
            if (!structureStart.bounds().intersects(chunkBounds)) {
                continue;
            }

            var context = new StructurePlaceContext(
                    adapter,
                    settings,
                    this.structureLoader,
                    this.featureLoader,
                    structureStart.start(),
                    settings.randomState().getOrCreateRandomFactory(Key.key("minecraft:structure")));

            for (var piece : structureStart.pieces()) {
                if (!piece.bounds().intersects(chunkBounds)) {
                    continue;
                }

                if (piece.terrainMatching()) {
                    piece.template().placeTerrainMatching(
                            context.level(),
                            piece.origin(),
                            piece.rotation(),
                            piece.processors(),
                            context.randomFactory(),
                            this.structureLoader.blockTags(),
                            piece.projectionOffset(),
                            surfaceHeights,
                            chunkStartX,
                            chunkStartZ,
                            chunkSizeX,
                            chunkSizeZ);
                } else {
                    piece.template().place(
                            context.level(),
                            piece.origin(),
                            piece.rotation(),
                            piece.processors(),
                            context.randomFactory(),
                            this.structureLoader.blockTags());
                    // Note: Foundation placement (dirt pillars) removed as it was creating
                    // ugly stepped foundations. Structures should be designed to sit on terrain.
                }
            }

            for (var feature : structureStart.features()) {
                if (!feature.bounds().intersects(chunkBounds)) {
                    continue;
                }
                JigsawAssembler.placeFeature(context, feature);
            }
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private record StartKey(long chunkKey, Key structureSetId) {
    }

    private record StructureStart(
            BlockVec start,
            List<JigsawAssembler.PlacedPiece> pieces,
            List<JigsawAssembler.PlacedFeature> features,
            BoundingBox bounds) {
    }

    private Key pickStructure(Key structureSetId, StructureSet structureSet, NoiseGeneratorSettingsRuntime settings) {
        var randomFactory = settings.randomState().getOrCreateRandomFactory(Key.key("minecraft:structure_set"));
        var random = randomFactory.fromHashOf(structureSetId.asString());

        var totalWeight = 0;
        for (var entry : structureSet.structures()) {
            totalWeight += entry.weight();
        }

        if (totalWeight <= 0) {
            return null;
        }

        var selected = random.nextInt(totalWeight);
        var running = 0;
        for (var entry : structureSet.structures()) {
            running += entry.weight();
            if (selected < running) {
                return entry.structure();
            }
        }

        return structureSet.structures().getFirst().structure();
    }

    private int resolveStartY(Structure structure, int surfaceY, NoiseGeneratorSettingsRuntime settings) {
        if (structure instanceof JigsawStructure jigsaw) {
            var baseHeight = jigsaw.startHeight();
            if (jigsaw.projectToHeightmap()) {
                return surfaceY + baseHeight;
            }
            return baseHeight;
        }

        if (structure instanceof SimpleStructure) {
            return surfaceY;
        }

        return surfaceY;
    }
}
