package rocks.minestom.worldgen.structure.igloo;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.StructureWrites;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureShapeUpdater;
import rocks.minestom.worldgen.structure.template.StructureTemplate;
import rocks.minestom.worldgen.terrain.TerrainData;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places igloos with vanilla-exact seeding, mirroring
 * {@code rocks.minestom.worldgen.structure.oceanruin.OceanRuinPlacer}: the
 * start (rotation, basement roll, ladder depth and every piece's own surface
 * height) is a pure function of the chunk, and template placement runs per
 * intersecting chunk. Vanilla's {@code IglooPiece.postProcess} never consumes
 * the decoration-time random for block placement (only the terrain heightmap,
 * plus a chest loot seed that is out of scope), so - like ocean ruins - there
 * is no per-chunk feature-seed draw to replicate here.
 */
public final class IglooPlacer {
    /** The top igloo plus its basement footprint stays within 2 chunks. */
    private static final int REFERENCE_RADIUS = 2;
    /** Vanilla {@code IglooPieces.GENERATION_HEIGHT}. */
    private static final int GENERATION_HEIGHT = 90;
    private static final Key LADDER = Key.key("minecraft", "ladder");

    private final StructureLoader structureLoader;
    private final Map<Long, Optional<IglooStart>> starts = new ConcurrentHashMap<>();
    private final Map<Long, TerrainData> terrainCache = new ConcurrentHashMap<>();

    public IglooPlacer(StructureLoader structureLoader) {
        this.structureLoader = structureLoader;
    }

    public boolean isIglooSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof IglooStructure) {
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

        var intersecting = new ArrayList<IglooStart>();
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

        var connectionShapeUpdates = new ArrayList<BlockVec>();
        for (var start : intersecting) {
            this.placeStart(start, adapter, chunkBounds, settings, connectionShapeUpdates);
        }

        if (!connectionShapeUpdates.isEmpty()) {
            StructureShapeUpdater.update(adapter, this.structureLoader.blockTags(), connectionShapeUpdates);
        }
    }

    private void placeStart(IglooStart start, GenerationUnitAdapter adapter, BoundingBox chunkBounds,
            NoiseGeneratorSettingsRuntime settings, List<BlockVec> connectionShapeUpdates) {
        for (var piece : start.pieces()) {
            if (!piece.bounds().intersects(chunkBounds)) {
                continue;
            }

            var template = this.structureLoader.getTemplate(piece.template());
            if (template == null) {
                continue;
            }

            var processorContext = new StructureProcessorContext(
                    adapter, this.structureLoader.blockTags(), settings.randomState().seed(),
                    piece.placementPosition(), piece.placementPosition(), null);
            var placementContext = new StructureTemplate.PlacementContext(
                    adapter, chunkBounds, processorContext, connectionShapeUpdates);
            template.place(placementContext, piece.placementPosition(), piece.rotation(),
                    new StructureProcessorList(List.of()), false, false,
                    LiquidSettings.IGNORE_WATERLOGGING, true);

            if (piece.template().equals(IglooPieces.STRUCTURE_LOCATION_TOP)) {
                this.fixTrapdoor(piece, adapter, chunkBounds);
            }
        }
    }

    /**
     * Vanilla {@code IglooPiece.postProcess} tail: for the visible igloo, if the
     * block below the trapdoor position is neither air nor a ladder (i.e. there
     * is no basement shaft under it), the trapdoor is replaced with a snow block.
     */
    private void fixTrapdoor(PlacedIglooPiece piece, GenerationUnitAdapter adapter, BoundingBox chunkBounds) {
        var pivot = IglooPieces.PIVOTS.get(piece.template());
        var relative = calculateRelativePosition(new BlockVec(3, 0, 5), pivot, piece.rotation());
        var trapdoorPos = piece.templatePosition().add(relative.blockX(), relative.blockY(), relative.blockZ());
        if (!chunkBounds.isInside(trapdoorPos)) {
            return;
        }

        var below = adapter.getBlock(trapdoorPos.blockX(), trapdoorPos.blockY() - 1, trapdoorPos.blockZ());
        if (!below.air() && !below.key().equals(LADDER)) {
            adapter.setBlock(trapdoorPos, Block.SNOW_BLOCK);
        }
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

    private IglooStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer)))
                .orElse(null);
    }

    private IglooStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof IglooStructure) {
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
     * Vanilla {@code IglooStructure.findGenerationPoint}: the biome check runs
     * at the chunk-center {@code WORLD_SURFACE_WG} height, then
     * {@code generatePieces} draws a rotation and runs
     * {@code IglooPieces.addPieces} at the chunk's minimum corner with
     * {@code y = 90} (every piece resolves its own real height below).
     */
    private IglooStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof IglooStructure igloo)) {
            return null;
        }

        var centerX = (chunkX << 4) + 8;
        var centerZ = (chunkZ << 4) + 8;
        var centerY = this.worldSurfaceHeight(centerX, centerZ, settings);
        var biome = biomeZoomer.source().biome(centerX >> 2, centerY >> 2, centerZ >> 2);
        if (!igloo.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var rotation = Rotation.getRandom(random);
        var position = new BlockVec(chunkX << 4, GENERATION_HEIGHT, chunkZ << 4);
        var rawPieces = IglooPieces.addPieces(position, rotation, random);

        var pieces = new ArrayList<PlacedIglooPiece>(rawPieces.size());
        BoundingBox bounds = null;
        for (var raw : rawPieces) {
            var template = this.structureLoader.getTemplate(raw.template());
            if (template == null) {
                continue;
            }

            var finalY = this.resolveHeight(raw, settings);
            var templatePosition = new BlockVec(raw.position().blockX(), finalY, raw.position().blockZ());
            var pivot = IglooPieces.PIVOTS.get(raw.template());
            var pivotOffset = raw.rotation().rotate(pivot, BlockVec.ZERO);
            var placementPosition = templatePosition.add(
                    pivot.blockX() - pivotOffset.blockX(),
                    pivot.blockY() - pivotOffset.blockY(),
                    pivot.blockZ() - pivotOffset.blockZ());
            var pieceBounds = template.getBoundingBox(placementPosition, raw.rotation());
            pieces.add(new PlacedIglooPiece(raw.template(), templatePosition, placementPosition, raw.rotation(),
                    copyBounds(pieceBounds)));
            bounds = bounds == null ? copyBounds(pieceBounds) : encapsulate(bounds, pieceBounds);
        }

        if (pieces.isEmpty() || bounds == null) {
            return null;
        }

        return new IglooStart(pieces, bounds);
    }

    /**
     * Vanilla {@code IglooPiece.postProcess} height correction: reads
     * {@code WORLD_SURFACE_WG} at the piece's own rotated entrance column and
     * shifts the stub template position by {@code height - 90 - 1}.
     */
    private int resolveHeight(IglooPieces.Piece piece, NoiseGeneratorSettingsRuntime settings) {
        var pivot = IglooPieces.PIVOTS.get(piece.template());
        var offset = IglooPieces.OFFSETS.get(piece.template());
        var entranceLocal = new BlockVec(3 - offset.blockX(), 0, -offset.blockZ());
        var relative = calculateRelativePosition(entranceLocal, pivot, piece.rotation());
        var entranceX = piece.position().blockX() + relative.blockX();
        var entranceZ = piece.position().blockZ() + relative.blockZ();
        var height = this.worldSurfaceHeight(entranceX, entranceZ, settings);
        return piece.position().blockY() + height - GENERATION_HEIGHT - 1;
    }

    /**
     * Vanilla {@code StructureTemplate.calculateRelativePosition} for mirror
     * {@code NONE}: rotate the local position about the template's rotation
     * pivot. Equivalent to {@code pivot + rotate(local - pivot)} because this
     * codebase's {@link Rotation#rotate} pivots at the origin.
     */
    private static BlockVec calculateRelativePosition(BlockVec local, BlockVec pivot, Rotation rotation) {
        var difference = new BlockVec(
                local.blockX() - pivot.blockX(),
                local.blockY() - pivot.blockY(),
                local.blockZ() - pivot.blockZ());
        var rotated = rotation.rotate(difference, BlockVec.ZERO);
        return new BlockVec(
                pivot.blockX() + rotated.blockX(),
                pivot.blockY() + rotated.blockY(),
                pivot.blockZ() + rotated.blockZ());
    }

    /**
     * Approximation of vanilla {@code WORLD_SURFACE_WG}, matching every other
     * placer in this codebase: one above the highest solid block of the raw
     * noise terrain.
     */
    private int worldSurfaceHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = this.terrainData(chunkX, chunkZ, settings);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() : solidTop + 1;
    }

    private TerrainData terrainData(int chunkX, int chunkZ, NoiseGeneratorSettingsRuntime settings) {
        return this.terrainCache.computeIfAbsent(packChunk(chunkX, chunkZ),
                unused -> new TerrainGenerator(settings).generate(chunkX, chunkZ));
    }

    private static BoundingBox copyBounds(BoundingBox bounds) {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static BoundingBox encapsulate(BoundingBox bounds, BoundingBox other) {
        bounds.encapsulate(other);
        return bounds;
    }

    private record PlacedIglooPiece(Key template, BlockVec templatePosition, BlockVec placementPosition,
            Rotation rotation, BoundingBox bounds) {
    }

    private record IglooStart(List<PlacedIglooPiece> pieces, BoundingBox bounds) {
    }
}
