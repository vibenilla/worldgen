package rocks.minestom.worldgen.structure.oceanruin;

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
import rocks.minestom.worldgen.structure.processor.BlockIgnoreProcessor;
import rocks.minestom.worldgen.structure.processor.BlockRotProcessor;
import rocks.minestom.worldgen.structure.processor.CappedProcessor;
import rocks.minestom.worldgen.structure.processor.ProcessorRule;
import rocks.minestom.worldgen.structure.processor.PosRuleTest;
import rocks.minestom.worldgen.structure.processor.RuleStructureProcessor;
import rocks.minestom.worldgen.structure.processor.RuleTest;
import rocks.minestom.worldgen.structure.processor.StructureProcessor;
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
 * Places ocean ruins with vanilla-exact seeding, mirroring
 * {@code rocks.minestom.worldgen.structure.mansion.MansionPlacer}: starts are
 * pure functions of their chunk (rotation, large/cluster rolls and every
 * piece's floor height resolved once), while template placement runs per
 * intersecting chunk. Vanilla's {@code OceanRuinPiece.postProcess} never
 * consumes the decoration-time random (only the terrain heightmap), so unlike
 * {@link rocks.minestom.worldgen.structure.scattered.ScatteredFeaturePlacer}
 * there is no per-chunk feature-seed draw to replicate here.
 */
public final class OceanRuinPlacer {
    /** GenerationStep.Decoration.SURFACE_STRUCTURES ordinal. */
    private static final int SURFACE_STRUCTURES_STEP = 4;
    /** The 15-block ruin plus an up to ~32-block cluster spread stays within 3 chunks. */
    private static final int REFERENCE_RADIUS = 3;

    private final StructureLoader structureLoader;
    private final Map<Long, Optional<OceanRuinStart>> starts = new ConcurrentHashMap<>();
    private final Map<Long, TerrainData> terrainCache = new ConcurrentHashMap<>();

    public OceanRuinPlacer(StructureLoader structureLoader) {
        this.structureLoader = structureLoader;
    }

    public boolean isOceanRuinSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof OceanRuinStructure) {
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

        var intersecting = new ArrayList<OceanRuinStart>();
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
        var random = new rocks.minestom.worldgen.random.WorldgenRandom(
                new rocks.minestom.worldgen.random.XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);
        for (var structureKey : this.structureLoader.structuresAtStep("surface_structures")) {
            var index = -1;
            var seeded = false;
            for (var start : intersecting) {
                if (!start.structureKey().equals(structureKey)) {
                    continue;
                }
                if (!seeded) {
                    index = this.structureLoader.structuresAtStep("surface_structures").indexOf(structureKey);
                    random.setFeatureSeed(decorationSeed, index, SURFACE_STRUCTURES_STEP);
                    seeded = true;
                }
                this.placeStart(start, adapter, chunkBounds, settings, connectionShapeUpdates, random);
            }
        }

        if (!connectionShapeUpdates.isEmpty()) {
            StructureShapeUpdater.updateEdges(adapter, this.structureLoader.blockTags(), connectionShapeUpdates);
            StructureShapeUpdater.update(adapter, this.structureLoader.blockTags(), connectionShapeUpdates);
        }
    }

    private void placeStart(OceanRuinStart start, GenerationUnitAdapter adapter, BoundingBox chunkBounds,
            NoiseGeneratorSettingsRuntime settings, List<BlockVec> connectionShapeUpdates,
            rocks.minestom.worldgen.random.RandomSource random) {
        var firstBounds = start.pieces().getFirst().bounds();
        var referencePos = new BlockVec(
                firstBounds.minX() + (firstBounds.maxX() - firstBounds.minX() + 1) / 2,
                firstBounds.minY(),
                firstBounds.minZ() + (firstBounds.maxZ() - firstBounds.minZ() + 1) / 2);

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
                    piece.position(), referencePos, null);
            var placementContext = new StructureTemplate.PlacementContext(
                    adapter, chunkBounds, processorContext, connectionShapeUpdates);
            template.place(placementContext, piece.position(), piece.rotation(),
                    processors(piece.biomeTemp(), piece.integrity()), false, false,
                    LiquidSettings.APPLY_WATERLOGGING, true);

            for (var marker : template.dataMarkers(piece.position(), piece.rotation(),
                    rocks.minestom.worldgen.structure.template.Mirror.NONE)) {
                if (!chunkBounds.isInside(marker.position())) {
                    continue;
                }
                this.handleDataMarker(marker, adapter, settings, random);
            }
        }
    }

    /**
     * Vanilla {@code OceanRuinPieces.OceanRuinPiece.handleDataMarker}: the
     * chest marker places a chest (waterlogged when the position holds water)
     * and seeds its loot table with {@code random.nextLong()} (the loot NBT
     * itself is out of scope); the drowned marker replaces itself with air
     * above sea level, water below.
     */
    private void handleDataMarker(StructureTemplate.DataMarker marker, GenerationUnitAdapter adapter,
            NoiseGeneratorSettingsRuntime settings, rocks.minestom.worldgen.random.RandomSource random) {
        var position = marker.position();
        switch (marker.metadata()) {
            case "chest" -> {
                var current = adapter.getBlock(position.blockX(), position.blockY(), position.blockZ());
                var waterlogged = rocks.minestom.worldgen.feature.WaterStates.hasWaterFluid(current);
                adapter.setBlock(position.blockX(), position.blockY(), position.blockZ(),
                        Block.CHEST.withProperty("waterlogged", Boolean.toString(waterlogged)));
                random.nextLong();
            }
            case "drowned" -> {
                if (position.blockY() > settings.seaLevel()) {
                    adapter.setBlock(position.blockX(), position.blockY(), position.blockZ(), Block.AIR);
                } else {
                    adapter.setBlock(position.blockX(), position.blockY(), position.blockZ(), Block.WATER);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Vanilla {@code OceanRuinPiece.makeSettings}: block rot at the piece's
     * integrity, structure/air stripping, then a capped rule that turns up to
     * 5 sand (warm) or gravel (cold) blocks into their suspicious variant
     * (the attached loot table NBT is out of scope, matching every other
     * template-based structure in this codebase).
     */
    private static StructureProcessorList processors(OceanRuinStructure.BiomeTemp biomeTemp, float integrity) {
        var suspicious = biomeTemp == OceanRuinStructure.BiomeTemp.COLD
                ? suspiciousBlockProcessor(Block.GRAVEL, Block.SUSPICIOUS_GRAVEL)
                : suspiciousBlockProcessor(Block.SAND, Block.SUSPICIOUS_SAND);
        return new StructureProcessorList(List.of(
                new BlockRotProcessor(null, integrity),
                BlockIgnoreProcessor.STRUCTURE_AND_AIR,
                suspicious));
    }

    private static StructureProcessor suspiciousBlockProcessor(Block candidate, Block replacement) {
        var rule = new ProcessorRule(new RuleTest.BlockMatchTest(candidate.key()), RuleTest.AlwaysTrueTest.INSTANCE,
                PosRuleTest.PosAlwaysTrueTest.INSTANCE, replacement);
        return new CappedProcessor(new RuleStructureProcessor(List.of(rule)), 5);
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

    private OceanRuinStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer)))
                .orElse(null);
    }

    private OceanRuinStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof OceanRuinStructure) {
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
     * Vanilla {@code OceanRuinStructure.findGenerationPoint} (biome check at
     * the chunk-center {@code OCEAN_FLOOR_WG} height) followed by
     * {@code generatePieces}: a random rotation, then
     * {@code OceanRuinPieces.addPieces} at the chunk's minimum corner with
     * {@code y = 90} (the vanilla stub height; every piece resolves its own
     * real height below, mirroring {@code OceanRuinPiece.postProcess}).
     */
    private OceanRuinStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof OceanRuinStructure oceanRuin)) {
            return null;
        }

        var centerX = (chunkX << 4) + 8;
        var centerZ = (chunkZ << 4) + 8;
        var centerY = this.oceanFloorHeight(centerX, centerZ, settings);
        var biome = biomeZoomer.source().biome(centerX >> 2, centerY >> 2, centerZ >> 2);
        if (!oceanRuin.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);
        var rotation = Rotation.getRandom(random);
        var position = new BlockVec(chunkX << 4, 90, chunkZ << 4);
        var rawPieces = OceanRuinPieces.addPieces(position, rotation, random, oceanRuin);
        if (rawPieces.isEmpty()) {
            return null;
        }

        var pieces = new ArrayList<PlacedOceanRuinPiece>(rawPieces.size());
        BoundingBox bounds = null;
        for (var raw : rawPieces) {
            var template = this.structureLoader.getTemplate(raw.template());
            if (template == null) {
                continue;
            }

            var finalY = this.resolveHeight(raw.position(), raw.rotation(), template.size(), settings);
            var finalPosition = new BlockVec(raw.position().blockX(), finalY, raw.position().blockZ());
            var pieceBounds = template.getBoundingBox(finalPosition, raw.rotation());
            pieces.add(new PlacedOceanRuinPiece(raw.template(), finalPosition, raw.rotation(), raw.integrity(),
                    oceanRuin.biomeTemp(), copyBounds(pieceBounds)));
            bounds = bounds == null ? copyBounds(pieceBounds) : encapsulate(bounds, pieceBounds);
        }

        if (pieces.isEmpty() || bounds == null) {
            return null;
        }

        return new OceanRuinStart(structureKey, pieces, bounds);
    }

    /**
     * Vanilla {@code OceanRuinPiece.postProcess}: the piece starts at the
     * {@code OCEAN_FLOOR_WG} height under its origin corner, then
     * {@code getHeight} scans the whole rotated footprint, descending through
     * air, water and ice from one below that height to find the true floor,
     * and only commits to the lower height when the footprint is deep and
     * wide enough (matching vanilla's {@code topY - minY > 2 && area > width - 2}).
     */
    private int resolveHeight(BlockVec origin, Rotation rotation, BlockVec templateSize,
            NoiseGeneratorSettingsRuntime settings) {
        var oceanFloorY = this.oceanFloorHeight(origin.blockX(), origin.blockZ(), settings);
        var pos = new BlockVec(origin.blockX(), oceanFloorY, origin.blockZ());
        var farCorner = rotation.rotate(
                new BlockVec(templateSize.blockX() - 1, 0, templateSize.blockZ() - 1), BlockVec.ZERO);
        var corner = pos.add(farCorner.blockX(), 0, farCorner.blockZ());
        return this.getHeight(pos, corner, settings);
    }

    private int getHeight(BlockVec pos, BlockVec corner, NoiseGeneratorSettingsRuntime settings) {
        var newY = pos.blockY();
        var minY = 512;
        var topY = newY - 1;
        var area = 0;
        var minX = Math.min(pos.blockX(), corner.blockX());
        var maxX = Math.max(pos.blockX(), corner.blockX());
        var minZ = Math.min(pos.blockZ(), corner.blockZ());
        var maxZ = Math.max(pos.blockZ(), corner.blockZ());
        var iceTag = Key.key("minecraft:ice");

        for (var x = minX; x <= maxX; x++) {
            for (var z = minZ; z <= maxZ; z++) {
                var floorY = pos.blockY() - 1;
                var state = this.terrainBlock(x, floorY, z, settings);
                while (isPassableFloor(state, iceTag, this.structureLoader) && floorY > settings.minY() + 1) {
                    floorY--;
                    state = this.terrainBlock(x, floorY, z, settings);
                }
                minY = Math.min(minY, floorY);
                if (floorY < topY - 2) {
                    area++;
                }
            }
        }

        var width = Math.abs(pos.blockX() - corner.blockX());
        if (topY - minY > 2 && area > width - 2) {
            newY = minY + 1;
        }
        return newY;
    }

    private static boolean isPassableFloor(Block state, Key iceTag, StructureLoader structureLoader) {
        return state.isAir() || state.key().equals(Key.key("minecraft:water"))
                || structureLoader.blockTags().blocks(iceTag).contains(state.key());
    }

    private int oceanFloorHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = this.terrainData(chunkX, chunkZ, settings);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() : solidTop + 1;
    }

    private Block terrainBlock(int blockX, int blockY, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = this.terrainData(chunkX, chunkZ, settings);
        var localX = blockX - (chunkX << 4);
        var localZ = blockZ - (chunkZ << 4);
        var yIndex = blockY - settings.minY();
        if (yIndex < 0 || yIndex >= settings.height()) {
            return Block.AIR;
        }
        var index = (localX * 16 + localZ) * settings.height() + yIndex;
        var block = terrainData.blocks()[index];
        return block != null ? block : Block.AIR;
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

    private record PlacedOceanRuinPiece(Key template, BlockVec position, Rotation rotation, float integrity,
            OceanRuinStructure.BiomeTemp biomeTemp, BoundingBox bounds) {
    }

    private record OceanRuinStart(Key structureKey, List<PlacedOceanRuinPiece> pieces, BoundingBox bounds) {
    }
}
