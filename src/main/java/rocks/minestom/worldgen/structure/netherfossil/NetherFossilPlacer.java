package rocks.minestom.worldgen.structure.netherfossil;

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
import rocks.minestom.worldgen.structure.TerrainAdjustment;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.BlockIgnoreProcessor;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureShapeUpdater;
import rocks.minestom.worldgen.structure.template.StructureTemplate;
import rocks.minestom.worldgen.terrain.Beardifier;
import rocks.minestom.worldgen.terrain.TerrainData;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places nether fossils with vanilla-exact seeding, mirroring
 * {@code rocks.minestom.worldgen.structure.shipwreck.ShipwreckPlacer}. Unlike
 * every other single-template structure in this codebase, vanilla's
 * {@code NetherFossilStructure.findGenerationPoint} does not place at the
 * chunk-center raw terrain surface: it samples a height provider, then walks
 * the raw noise column (vanilla {@code ChunkGenerator.getBaseColumn} - no
 * carvers, no bedrock) downward looking for the first air block directly
 * above a solid or soul sand block, giving up if that search reaches sea
 * level. Placing at the generic surface height instead (as the previous,
 * incorrect implementation did) lands almost every fossil at the nether
 * ceiling, where the raw terrain's topmost solid block sits right against the
 * bedrock roof.
 */
public final class NetherFossilPlacer {
    /** The largest fossil template stays within 2 chunks. */
    private static final int REFERENCE_RADIUS = 2;
    /** Vanilla {@code StructurePiece.isCloseToChunk} kernel distance. */
    private static final int BEARD_CLOSE_DISTANCE = 12;
    private static final Key SOUL_SAND = Key.key("minecraft:soul_sand");

    private final StructureLoader structureLoader;
    private final Map<Long, Optional<NetherFossilStart>> starts = new ConcurrentHashMap<>();
    private final Map<Long, TerrainData> terrainCache = new ConcurrentHashMap<>();

    public NetherFossilPlacer(StructureLoader structureLoader) {
        this.structureLoader = structureLoader;
    }

    public boolean isNetherFossilSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof NetherFossilStructure) {
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

        var intersecting = new ArrayList<NetherFossilStart>();
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

    private void placeStart(NetherFossilStart start, GenerationUnitAdapter adapter, BoundingBox chunkBounds,
            NoiseGeneratorSettingsRuntime settings, List<BlockVec> connectionShapeUpdates) {
        if (!start.bounds().intersects(chunkBounds)) {
            return;
        }

        var template = this.structureLoader.getTemplate(start.template());
        if (template == null) {
            return;
        }

        var processorContext = new StructureProcessorContext(
                adapter, this.structureLoader.blockTags(), settings.randomState().seed(),
                start.position(), start.position(), null);
        var placementContext = new StructureTemplate.PlacementContext(
                adapter, chunkBounds, processorContext, connectionShapeUpdates);
        var processors = new StructureProcessorList(List.of(BlockIgnoreProcessor.STRUCTURE_AND_AIR));
        template.place(placementContext, start.position(), start.rotation(), processors, false, false,
                LiquidSettings.APPLY_WATERLOGGING, true);
        this.placeDriedGhast(start, adapter, settings);
    }

    /**
     * Vanilla {@code NetherFossilPieces.NetherFossilPiece.placeDriedGhast}: a
     * positional random seeded at the fossil bounding box center sometimes
     * drops a dried ghast on the fossil floor. Vanilla runs this once per
     * intersecting chunk against the chunk box encapsulated with the fossil
     * box, which makes its in-box check always pass; the air check makes the
     * repeated runs idempotent, and the adapter drops writes outside the
     * current unit, so the chunk that owns the target position places it.
     */
    private void placeDriedGhast(NetherFossilStart start, GenerationUnitAdapter adapter,
            NoiseGeneratorSettingsRuntime settings) {
        var bounds = start.bounds();
        var positionalRandom = new LegacyRandomSource(settings.randomState().seed())
                .forkPositional()
                .at(bounds.getCenter().blockX(), bounds.getCenter().blockY(), bounds.getCenter().blockZ());
        if (positionalRandom.nextFloat() >= 0.5F) {
            return;
        }

        var x = bounds.minX() + positionalRandom.nextInt(bounds.getXSpan());
        var y = bounds.minY();
        var z = bounds.minZ() + positionalRandom.nextInt(bounds.getZSpan());
        if (!adapter.getBlock(x, y, z).isAir()) {
            return;
        }

        var rotation = Rotation.getRandom(positionalRandom);
        adapter.setBlock(new BlockVec(x, y, z),
                StructureTemplate.rotateBlockState(Block.DRIED_GHAST, rotation));
    }

    /**
     * Vanilla {@code Beardifier.forStructuresInChunk} for the beard_thin
     * support platform: like {@code StrongholdPlacer.contributeBeard}, this
     * bypasses {@code StructurePlacer.beardifier}'s generic template path
     * since fossils have their own start cache.
     */
    public void contributeBeard(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer, List<Beardifier.Rigid> rigids) {
        var chunkStartBlockX = chunkX << 4;
        var chunkStartBlockZ = chunkZ << 4;

        for (var sourceX = chunkX - REFERENCE_RADIUS; sourceX <= chunkX + REFERENCE_RADIUS; sourceX++) {
            for (var sourceZ = chunkZ - REFERENCE_RADIUS; sourceZ <= chunkZ + REFERENCE_RADIUS; sourceZ++) {
                var start = this.startAt(sourceX, sourceZ, structureSet, settings, biomeZoomer);
                if (start == null || !intersectsXZ(start.bounds(), chunkStartBlockX, chunkStartBlockZ,
                        chunkStartBlockX + 15, chunkStartBlockZ + 15)) {
                    continue;
                }

                if (intersectsXZ(start.bounds(), chunkStartBlockX - BEARD_CLOSE_DISTANCE,
                        chunkStartBlockZ - BEARD_CLOSE_DISTANCE,
                        chunkStartBlockX + 15 + BEARD_CLOSE_DISTANCE, chunkStartBlockZ + 15 + BEARD_CLOSE_DISTANCE)) {
                    rigids.add(new Beardifier.Rigid(start.bounds(), TerrainAdjustment.BEARD_THIN, 0));
                }
            }
        }
    }

    private static boolean intersectsXZ(BoundingBox bounds, int minX, int minZ, int maxX, int maxZ) {
        return bounds.maxX() >= minX && bounds.minX() <= maxX
                && bounds.maxZ() >= minZ && bounds.minZ() <= maxZ;
    }

    private Block[] liveChunkBlocks(int chunkX, int chunkZ, int[] surfaceHeights) {
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

    private NetherFossilStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = packChunk(chunkX, chunkZ);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer)))
                .orElse(null);
    }

    private NetherFossilStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof NetherFossilStructure) {
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
     * Vanilla {@code NetherFossilStructure.findGenerationPoint}: a random
     * column within the chunk, a height sampled from the structure's uniform
     * height provider, then a downward scan of the raw noise column for the
     * first air block directly above a solid or soul sand floor - giving up
     * once the scan reaches sea level. The biome check
     * ({@code Structure.isValidBiome}) runs at this resolved position, not
     * the chunk center.
     */
    private NetherFossilStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof NetherFossilStructure fossil)) {
            return null;
        }
        if (fossil.templates().isEmpty()) {
            return null;
        }

        var seed = settings.randomState().seed();
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(seed, chunkX, chunkZ);

        var blockX = (chunkX << 4) + random.nextInt(16);
        var blockZ = (chunkZ << 4) + random.nextInt(16);
        var seaLevel = settings.seaLevel();

        var minY = fossil.minHeight().resolveY(settings.minY(), settings.maxYInclusive());
        var maxY = fossil.maxHeight().resolveY(settings.minY(), settings.maxYInclusive());
        var y = minY > maxY ? minY : minY + random.nextInt(maxY - minY + 1);

        var terrainData = this.terrainData(chunkX, chunkZ, settings);
        var localX = blockX - (chunkX << 4);
        var localZ = blockZ - (chunkZ << 4);

        while (y > seaLevel) {
            var currentAir = this.columnMask(terrainData, localX, localZ, y, settings) == TerrainData.AIR;
            y--;
            var belowSturdy = this.isSturdyFloor(terrainData, localX, localZ, y, settings);
            if (currentAir && belowSturdy) {
                break;
            }
        }

        if (y <= seaLevel) {
            return null;
        }

        var position = new BlockVec(blockX, y, blockZ);
        var biome = biomeZoomer.source().biome(blockX >> 2, y >> 2, blockZ >> 2);
        if (!fossil.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var rotation = Rotation.getRandom(random);
        var templateKey = fossil.templates().get(random.nextInt(fossil.templates().size()));
        var template = this.structureLoader.getTemplate(templateKey);
        if (template == null) {
            return null;
        }

        var bounds = template.getBoundingBox(position, rotation);
        return new NetherFossilStart(templateKey, position, rotation, copyBounds(bounds));
    }

    private boolean isSturdyFloor(TerrainData terrainData, int localX, int localZ, int y,
            NoiseGeneratorSettingsRuntime settings) {
        var mask = this.columnMask(terrainData, localX, localZ, y, settings);
        if (mask == TerrainData.SOLID || mask == TerrainData.SOLID_OTHER) {
            return true;
        }
        var block = this.columnBlock(terrainData, localX, localZ, y, settings);
        return block != null && block.key().equals(SOUL_SAND);
    }

    private byte columnMask(TerrainData terrainData, int localX, int localZ, int y,
            NoiseGeneratorSettingsRuntime settings) {
        var yIndex = y - settings.minY();
        if (yIndex < 0 || yIndex >= settings.height()) {
            return TerrainData.AIR;
        }
        var index = (localX * 16 + localZ) * settings.height() + yIndex;
        return terrainData.stoneMask()[index];
    }

    private Block columnBlock(TerrainData terrainData, int localX, int localZ, int y,
            NoiseGeneratorSettingsRuntime settings) {
        var yIndex = y - settings.minY();
        if (yIndex < 0 || yIndex >= settings.height()) {
            return null;
        }
        var index = (localX * 16 + localZ) * settings.height() + yIndex;
        return terrainData.blocks()[index];
    }

    private TerrainData terrainData(int chunkX, int chunkZ, NoiseGeneratorSettingsRuntime settings) {
        return this.terrainCache.computeIfAbsent(packChunk(chunkX, chunkZ),
                unused -> new TerrainGenerator(settings).generate(chunkX, chunkZ));
    }

    private static BoundingBox copyBounds(BoundingBox bounds) {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private record NetherFossilStart(Key template, BlockVec position, Rotation rotation, BoundingBox bounds) {
    }
}
