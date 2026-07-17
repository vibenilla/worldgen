package rocks.minestom.worldgen.structure.mansion;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
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
import rocks.minestom.worldgen.structure.template.Mirror;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places the woodland mansion with vanilla-exact seeding, mirroring
 * {@link rocks.minestom.worldgen.structure.fortress.FortressPlacer}: starts
 * are pure functions of their chunk (grid layout solved once, piece list
 * fixed), while template placement runs per intersecting chunk.
 *
 * <p>Two deviations from vanilla, both a consequence of this repository's
 * generic template pipeline dropping {@code minecraft:structure_block} data
 * markers rather than dispatching them to a handler (the same limitation
 * every other template-based structure in this codebase already has):
 * mansion chests are never placed (the marker cell is simply dropped, which
 * usually leaves it as the air the template already reserved there) and the
 * evoker / vindicator / allay entity markers are never spawned.
 */
public final class MansionPlacer {
    /** Vanilla structure reference radius: a mansion spans well under 8 chunks. */
    private static final int REFERENCE_RADIUS = 8;

    private final StructureLoader structureLoader;
    private final Map<Long, Optional<MansionStart>> starts = new ConcurrentHashMap<>();

    public MansionPlacer(StructureLoader structureLoader) {
        this.structureLoader = structureLoader;
    }

    public boolean isMansionSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof WoodlandMansionStructure) {
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
        var chunkBounds = new BoundingBox(startX, settings.minY(), startZ,
                startX + 15, settings.maxYInclusive(), startZ + 15);

        var intersecting = new ArrayList<MansionStart>();
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
                        settings.height(), null)
                : new GenerationUnitAdapter(unit);

        for (var start : intersecting) {
            this.placeStart(start, adapter, chunkBounds, settings);
        }
    }

    private void placeStart(MansionStart start, GenerationUnitAdapter adapter, BoundingBox chunkBounds,
            NoiseGeneratorSettingsRuntime settings) {
        var firstBounds = start.pieces().getFirst().bounds();
        var referencePos = new BlockVec(
                firstBounds.minX() + (firstBounds.maxX() - firstBounds.minX() + 1) / 2,
                firstBounds.minY(),
                firstBounds.minZ() + (firstBounds.maxZ() - firstBounds.minZ() + 1) / 2);

        for (var piece : start.pieces()) {
            if (!piece.bounds().intersects(chunkBounds)) {
                continue;
            }

            var template = this.structureLoader.getTemplate(piece.templateKey());
            if (template == null) {
                continue;
            }

            var processorContext = new StructureProcessorContext(
                    adapter, this.structureLoader.blockTags(), settings.randomState().seed(),
                    piece.position(), referencePos, null);
            var placementContext = new StructureTemplate.PlacementContext(adapter, chunkBounds, processorContext);
            template.place(placementContext, piece.position(), piece.rotation(), piece.mirror(),
                    StructureProcessorList.EMPTY, false, false, LiquidSettings.APPLY_WATERLOGGING);
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

    private MansionStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer)))
                .orElse(null);
    }

    private MansionStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof WoodlandMansionStructure) {
                return this.tryGenerate(selected.structure(), chunkX, chunkZ, settings, biomeZoomer);
            }

            options.remove(index);
            total -= selected.weight();
        }
        return null;
    }

    /**
     * Vanilla {@code WoodlandMansionStructure.findGenerationPoint}: a random
     * rotation, then the lowest {@code WORLD_SURFACE_WG} corner height of the
     * rotated 5x5 footprint offset from the chunk position - rejecting below
     * y=60 - followed (only if the biome at that stub also matches) by the
     * grid-solved piece layout, continuing the same random.
     */
    private MansionStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof WoodlandMansionStructure mansion)) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);

        var rotation = Rotation.getRandom(random);
        var startPos = this.lowestYIn5by5BoxOffset7Blocks(chunkX, chunkZ, rotation, settings);
        if (startPos.blockY() < 60) {
            return null;
        }

        var biome = biomeZoomer.source().biome(startPos.blockX() >> 2, startPos.blockY() >> 2, startPos.blockZ() >> 2);
        if (!mansion.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var wmPieces = MansionPiecePlacer.generateMansion(startPos, rotation, random);
        if (wmPieces.isEmpty()) {
            return null;
        }

        var pieces = new ArrayList<PlacedMansionPiece>(wmPieces.size());
        BoundingBox bounds = null;
        for (var piece : wmPieces) {
            var templateKey = Key.key("minecraft", "woodland_mansion/" + piece.templateName());
            var template = this.structureLoader.getTemplate(templateKey);
            if (template == null) {
                continue;
            }
            var pieceBounds = template.getBoundingBox(piece.position(), piece.rotation(), piece.mirror());
            pieces.add(new PlacedMansionPiece(templateKey, piece.position(), piece.rotation(), piece.mirror(), pieceBounds));
            bounds = bounds == null ? copyBounds(pieceBounds) : encapsulate(bounds, pieceBounds);
        }

        if (pieces.isEmpty() || bounds == null) {
            return null;
        }

        return new MansionStart(structureKey, List.copyOf(pieces), bounds);
    }

    /**
     * Vanilla {@code Structure.getLowestYIn5by5BoxOffset7Blocks} plus
     * {@code getCornerHeights}: {@code WORLD_SURFACE_WG} is approximated the
     * same way as the other placers in this codebase (one above the highest
     * solid block of the raw noise terrain).
     */
    private BlockVec lowestYIn5by5BoxOffset7Blocks(int chunkX, int chunkZ, Rotation rotation,
            NoiseGeneratorSettingsRuntime settings) {
        var offsetX = 5;
        var offsetZ = 5;
        if (rotation == Rotation.CLOCKWISE_90) {
            offsetX = -5;
        } else if (rotation == Rotation.CLOCKWISE_180) {
            offsetX = -5;
            offsetZ = -5;
        } else if (rotation == Rotation.COUNTERCLOCKWISE_90) {
            offsetZ = -5;
        }

        var blockX = (chunkX << 4) + 7;
        var blockZ = (chunkZ << 4) + 7;
        var a = this.worldSurfaceHeight(blockX, blockZ, settings);
        var b = this.worldSurfaceHeight(blockX, blockZ + offsetZ, settings);
        var c = this.worldSurfaceHeight(blockX + offsetX, blockZ, settings);
        var d = this.worldSurfaceHeight(blockX + offsetX, blockZ + offsetZ, settings);
        var lowest = Math.min(Math.min(a, b), Math.min(c, d));
        return new BlockVec(blockX, lowest, blockZ);
    }

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

    private static BoundingBox encapsulate(BoundingBox bounds, BoundingBox other) {
        bounds.encapsulate(other);
        return bounds;
    }

    private record PlacedMansionPiece(Key templateKey, BlockVec position, Rotation rotation, Mirror mirror, BoundingBox bounds) {
    }

    private record MansionStart(Key structureKey, List<PlacedMansionPiece> pieces, BoundingBox bounds) {
    }
}
