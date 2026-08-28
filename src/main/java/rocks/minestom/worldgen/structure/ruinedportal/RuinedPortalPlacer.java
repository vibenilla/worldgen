package rocks.minestom.worldgen.structure.ruinedportal;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.generator.GenerationUnit;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeClimate;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
import rocks.minestom.worldgen.structure.StructureRng;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.StructureWrites;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.BlackstoneReplaceProcessor;
import rocks.minestom.worldgen.structure.processor.BlockAgeProcessor;
import rocks.minestom.worldgen.structure.processor.BlockIgnoreProcessor;
import rocks.minestom.worldgen.structure.processor.LavaSubmergedBlockProcessor;
import rocks.minestom.worldgen.structure.processor.PosRuleTest;
import rocks.minestom.worldgen.structure.processor.ProcessorRule;
import rocks.minestom.worldgen.structure.processor.ProtectedBlockProcessor;
import rocks.minestom.worldgen.structure.processor.RuleStructureProcessor;
import rocks.minestom.worldgen.structure.processor.RuleTest;
import rocks.minestom.worldgen.structure.processor.StructureProcessor;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.ruinedportal.RuinedPortalStructure.Properties;
import rocks.minestom.worldgen.structure.ruinedportal.RuinedPortalStructure.Setup;
import rocks.minestom.worldgen.structure.ruinedportal.RuinedPortalStructure.VerticalPlacement;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.structure.template.Mirror;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureShapeUpdater;
import rocks.minestom.worldgen.structure.template.StructureTemplate;
import rocks.minestom.worldgen.surface.DataPackBiomeResolver;
import rocks.minestom.worldgen.terrain.TerrainData;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Places ruined portals with vanilla-exact seeding, mirroring
 * {@code rocks.minestom.worldgen.structure.shipwreck.ShipwreckPlacer}. The start
 * (weighted setup, air pocket, giant/normal template, rotation, mirror and the
 * suitable buried/surface Y) is a pure function of the start chunk, resolved
 * from the {@code setLargeFeatureSeed} random. Vanilla's
 * {@code RuinedPortalPiece.postProcess} places the whole piece and then spreads
 * netherrack, drips columns and adds vines/leaves once, in the chunk holding the
 * bounding-box center, drawing from the {@code surface_structures} feature-seed
 * random - so this placer runs the full placement exactly once (unclipped,
 * writing across chunk boundaries) when the center chunk generates.
 */
public final class RuinedPortalPlacer {
    /** The largest giant portal plus the netherrack spread stays within 3 chunks. */
    private static final int REFERENCE_RADIUS = 3;
    /** GenerationStep.Decoration.SURFACE_STRUCTURES ordinal. */
    private static final int SURFACE_STRUCTURES_STEP = 4;
    private static final float PROBABILITY_OF_GIANT_PORTAL = 0.05F;
    private static final int MIN_Y_INDEX = 15;
    private static final Key FEATURES_CANNOT_REPLACE = Key.key("minecraft:features_cannot_replace");
    private static final float[] NETHERRACK_PROBABILITY_BY_DISTANCE = {
            1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.9F, 0.9F, 0.8F, 0.7F, 0.6F, 0.4F, 0.2F};

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final Map<Long, Optional<RuinedPortalStart>> starts = new ConcurrentHashMap<>();
    private final Map<Long, TerrainData> terrainCache = new ConcurrentHashMap<>();
    private volatile List<Key> surfaceStructures;
    private volatile DataPackBiomeResolver biomeResolver;

    public RuinedPortalPlacer(StructureLoader structureLoader, FeatureLoader featureLoader) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
    }

    public boolean isRuinedPortalSet(StructureSet structureSet) {
        for (var entry : structureSet.structures()) {
            if (this.structureLoader.getStructure(entry.structure()) instanceof RuinedPortalStructure) {
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

        var centered = new ArrayList<RuinedPortalStart>();
        for (var sourceX = chunkX - REFERENCE_RADIUS; sourceX <= chunkX + REFERENCE_RADIUS; sourceX++) {
            for (var sourceZ = chunkZ - REFERENCE_RADIUS; sourceZ <= chunkZ + REFERENCE_RADIUS; sourceZ++) {
                var start = this.startAt(sourceX, sourceZ, structureSet, settings, biomeZoomer);
                if (start != null
                        && Math.floorDiv(start.center().blockX(), 16) == chunkX
                        && Math.floorDiv(start.center().blockZ(), 16) == chunkZ) {
                    centered.add(start);
                }
            }
        }

        if (centered.isEmpty()) {
            return;
        }

        var lookup = StructureWrites.terrainLookup();
        if (lookup == null) {
            return;
        }
        var chunkBlocks = lookup.terrain(chunkX, chunkZ).blocks();

        var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(settings.randomState().seed(), startX, startZ);

        for (var start : centered) {
            var index = this.surfaceStructures().indexOf(start.structureKey());
            if (index < 0) {
                continue;
            }
            if (System.getProperty("worldgen.portalDebug") != null) {
                System.out.println("PINDEX " + start.structureKey() + " index=" + index
                        + " center=" + start.center() + " bounds=" + start.bounds()
                        + " runChunk=" + chunkX + "," + chunkZ);
            }
            random.setFeatureSeed(decorationSeed, index, SURFACE_STRUCTURES_STEP);
            this.placeStart(start, unit, chunkX, chunkZ, chunkBlocks, lookup, settings, random);
        }
    }

    private void placeStart(RuinedPortalStart start, GenerationUnit unit, int chunkX, int chunkZ,
            Block[] chunkBlocks, GenerationUnitAdapter.TerrainLookup lookup,
            NoiseGeneratorSettingsRuntime settings, WorldgenRandom random) {
        var template = this.structureLoader.getTemplate(start.templateKey());
        if (template == null) {
            return;
        }

        // Fork the unit over the portal footprint plus the netherrack spread
        // margin so isInBounds accepts the cross-chunk writes and they land via
        // the spill path, mirroring how the feature phase forks a padded unit.
        var box = start.bounds();
        var margin = NETHERRACK_PROBABILITY_BY_DISTANCE.length + 2;
        var forkStart = new BlockVec(box.minX() - margin, settings.minY(), box.minZ() - margin);
        var forkEnd = new BlockVec(box.maxX() + margin + 1, settings.maxYInclusive() + 1, box.maxZ() + margin + 1);
        var forkedUnit = unit.fork(forkStart, forkEnd);
        var adapter = new GenerationUnitAdapter(forkedUnit, chunkX << 4, chunkZ << 4, 16, 16, settings.minY(),
                chunkBlocks, settings.height(), lookup);
        var connectionShapeUpdates = new ArrayList<BlockVec>();

        var properties = start.properties();
        var referencePos = start.origin();
        var processorContext = new StructureProcessorContext(
                adapter, this.structureLoader.blockTags(), settings.randomState().seed(),
                start.origin(), referencePos, null);
        // Unclipped placement (chunkBounds null): the whole piece is written in
        // the center chunk, spilling across chunk boundaries like vanilla's
        // WorldGenLevel, matching RuinedPortalPiece.postProcess's single run.
        var placementContext = new StructureTemplate.PlacementContext(
                adapter, null, processorContext, connectionShapeUpdates, random);
        template.place(placementContext, start.placementPosition(), start.rotation(), start.mirror(),
                processors(start.placement(), properties), false, false,
                LiquidSettings.APPLY_WATERLOGGING, true);

        this.spreadNetherrack(random, adapter, start);
        this.addNetherrackDripColumnsBelowPortal(random, adapter, start);
        if (properties.vines() || properties.overgrown()) {
            for (var x = box.minX(); x <= box.maxX(); x++) {
                for (var y = box.minY(); y <= box.maxY(); y++) {
                    for (var z = box.minZ(); z <= box.maxZ(); z++) {
                        var pos = new BlockVec(x, y, z);
                        if (properties.vines()) {
                            this.maybeAddVines(random, adapter, pos);
                        }
                        if (properties.overgrown()) {
                            this.maybeAddLeavesAbove(random, adapter, pos, properties);
                        }
                    }
                }
            }
        }

        if (!connectionShapeUpdates.isEmpty()) {
            StructureShapeUpdater.updateEdges(adapter, this.structureLoader.blockTags(), connectionShapeUpdates);
            StructureShapeUpdater.update(adapter, this.structureLoader.blockTags(), connectionShapeUpdates);
        }
    }

    /**
     * Vanilla {@code RuinedPortalPiece.makeSettings}: the ignore, rule,
     * weathering, protected-block, lava-submersion and (nether) blackstone
     * processors in order. The template placer already prepends
     * {@code STRUCTURE_BLOCK}, so only the extra air ignore (non-air-pocket) is
     * added here.
     */
    private StructureProcessorList processors(VerticalPlacement placement, Properties properties) {
        var processors = new ArrayList<StructureProcessor>();
        if (!properties.airPocket()) {
            processors.add(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        }

        var rules = new ArrayList<ProcessorRule>();
        rules.add(blockReplaceRule(Block.GOLD_BLOCK, 0.3F, Block.AIR));
        rules.add(lavaRule(placement, properties));
        if (!properties.cold()) {
            rules.add(blockReplaceRule(Block.NETHERRACK, 0.07F, Block.MAGMA_BLOCK));
        }

        processors.add(new RuleStructureProcessor(rules));
        processors.add(new BlockAgeProcessor(properties.mossiness()));
        processors.add(new ProtectedBlockProcessor(FEATURES_CANNOT_REPLACE));
        processors.add(LavaSubmergedBlockProcessor.INSTANCE);
        if (properties.replaceWithBlackstone()) {
            processors.add(BlackstoneReplaceProcessor.INSTANCE);
        }
        return new StructureProcessorList(processors);
    }

    private static ProcessorRule lavaRule(VerticalPlacement placement, Properties properties) {
        if (placement == VerticalPlacement.ON_OCEAN_FLOOR) {
            return blockReplaceRule(Block.LAVA, Block.MAGMA_BLOCK);
        }
        return properties.cold()
                ? blockReplaceRule(Block.LAVA, Block.NETHERRACK)
                : blockReplaceRule(Block.LAVA, 0.2F, Block.MAGMA_BLOCK);
    }

    private static ProcessorRule blockReplaceRule(Block source, float probability, Block target) {
        return new ProcessorRule(new RuleTest.RandomBlockMatchTest(source.key(), probability),
                RuleTest.AlwaysTrueTest.INSTANCE, PosRuleTest.PosAlwaysTrueTest.INSTANCE, target);
    }

    private static ProcessorRule blockReplaceRule(Block source, Block target) {
        return new ProcessorRule(new RuleTest.BlockMatchTest(source.key()),
                RuleTest.AlwaysTrueTest.INSTANCE, PosRuleTest.PosAlwaysTrueTest.INSTANCE, target);
    }

    private RuinedPortalStart startAt(int chunkX, int chunkZ, StructureSet structureSet,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var key = (long) chunkX << 32 ^ (chunkZ & 0xFFFFFFFFL);
        return this.starts.computeIfAbsent(key,
                unused -> Optional.ofNullable(this.computeStart(chunkX, chunkZ, structureSet, settings, biomeZoomer)))
                .orElse(null);
    }

    private RuinedPortalStart computeStart(int chunkX, int chunkZ, StructureSet structureSet,
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
            if (this.structureLoader.getStructure(selected.structure()) instanceof RuinedPortalStructure) {
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
     * Vanilla {@code RuinedPortalStructure.findGenerationPoint} followed by the
     * stub biome check at the resolved origin: pick a weighted setup, sample the
     * air pocket, roll giant/normal template, rotation and mirror, resolve the
     * suitable Y, then keep the start only if the noise biome at the origin
     * matches.
     */
    private RuinedPortalStart tryGenerate(Key structureKey, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        if (!(this.structureLoader.getStructure(structureKey) instanceof RuinedPortalStructure structure)) {
            return null;
        }

        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(settings.randomState().seed(), chunkX, chunkZ);

        var setups = structure.setups();
        Setup setup = null;
        if (setups.size() > 1) {
            var total = 0.0F;
            for (var candidate : setups) {
                total += candidate.weight();
            }
            var pick = random.nextFloat();
            for (var candidate : setups) {
                pick -= candidate.weight() / total;
                if (pick < 0.0F) {
                    setup = candidate;
                    break;
                }
            }
        } else {
            setup = setups.getFirst();
        }
        if (setup == null) {
            return null;
        }

        var airPocket = sample(random, setup.airPocketProbability());
        Key templateKey;
        if (random.nextFloat() < PROBABILITY_OF_GIANT_PORTAL) {
            templateKey = RuinedPortalPieces.STRUCTURE_LOCATION_GIANT_PORTALS[
                    random.nextInt(RuinedPortalPieces.STRUCTURE_LOCATION_GIANT_PORTALS.length)];
        } else {
            templateKey = RuinedPortalPieces.STRUCTURE_LOCATION_PORTALS[
                    random.nextInt(RuinedPortalPieces.STRUCTURE_LOCATION_PORTALS.length)];
        }

        var template = this.structureLoader.getTemplate(templateKey);
        if (template == null) {
            return null;
        }

        var rotation = Rotation.getRandom(random);
        var mirror = random.nextFloat() < 0.5F ? Mirror.NONE : Mirror.FRONT_BACK;
        var size = template.size();
        var pivot = new BlockVec(size.blockX() / 2, 0, size.blockZ() / 2);
        var basePosition = new BlockVec(chunkX << 4, 0, chunkZ << 4);
        var placementBase = pivotFoldedOrigin(basePosition, pivot, rotation);
        var box = template.getBoundingBox(placementBase, rotation, mirror);

        var centerX = box.minX() + box.getXSpan() / 2;
        var centerZ = box.minZ() + box.getZSpan() / 2;
        var heightmapOceanFloor = setup.placement() == VerticalPlacement.ON_OCEAN_FLOOR;
        var surfaceY = this.baseHeight(centerX, centerZ, heightmapOceanFloor, settings) - 1;
        var projectedY = this.findSuitableY(random, setup.placement(), airPocket, surfaceY, box.getYSpan(), box, settings);

        var origin = new BlockVec(basePosition.blockX(), projectedY, basePosition.blockZ());
        var biome = biomeZoomer.source().biome(origin.blockX() >> 2, origin.blockY() >> 2, origin.blockZ() >> 2);
        if (!structure.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var cold = setup.canBeCold()
                && BiomeClimate.coldEnoughToSnow(this.biomeResolver(), biome,
                        origin.blockX(), origin.blockY(), origin.blockZ(), settings.seaLevel());
        var properties = new Properties(cold, setup.mossiness(), airPocket, setup.overgrown(),
                setup.vines(), setup.replaceWithBlackstone());

        var placementPosition = new BlockVec(placementBase.blockX(), projectedY, placementBase.blockZ());
        var worldBox = template.getBoundingBox(placementPosition, rotation, mirror);
        var center = new BlockVec(
                worldBox.minX() + worldBox.getXSpan() / 2,
                worldBox.minY() + worldBox.getYSpan() / 2,
                worldBox.minZ() + worldBox.getZSpan() / 2);

        return new RuinedPortalStart(structureKey, templateKey, origin, placementPosition, rotation, mirror,
                setup.placement(), properties, copyBounds(worldBox), center);
    }

    /**
     * Vanilla {@code StructureTemplate.transform} folds the rotation pivot into
     * the placement origin: the codebase rotates around the template origin, so
     * {@code origin + pivot - rotate(pivot)} reproduces vanilla's pivot rotation.
     */
    private static BlockVec pivotFoldedOrigin(BlockVec origin, BlockVec pivot, Rotation rotation) {
        var rotatedPivot = rotation.rotate(pivot, BlockVec.ZERO);
        return origin.add(
                pivot.blockX() - rotatedPivot.blockX(),
                pivot.blockY() - rotatedPivot.blockY(),
                pivot.blockZ() - rotatedPivot.blockZ());
    }

    private static boolean sample(WorldgenRandom random, float limit) {
        if (limit == 0.0F) {
            return false;
        }
        return limit == 1.0F || random.nextFloat() < limit;
    }

    /**
     * Vanilla {@code RuinedPortalStructure.findSuitableY}: seed a Y per vertical
     * placement, then walk down until three of the four bottom bounding-box
     * corners rest on opaque raw terrain.
     */
    private int findSuitableY(WorldgenRandom random, VerticalPlacement placement, boolean airPocket,
            int surfaceYAtCenter, int ySpan, BoundingBox box, NoiseGeneratorSettingsRuntime settings) {
        var minY = settings.minY() + MIN_Y_INDEX;
        int newY;
        if (placement == VerticalPlacement.IN_NETHER) {
            if (airPocket) {
                newY = StructureRng.randomBetweenInclusive(random, 32, 100);
            } else if (random.nextFloat() < 0.5F) {
                newY = StructureRng.randomBetweenInclusive(random, 27, 29);
            } else {
                newY = StructureRng.randomBetweenInclusive(random, 29, 100);
            }
        } else if (placement == VerticalPlacement.IN_MOUNTAIN) {
            newY = getRandomWithinInterval(random, 70, surfaceYAtCenter - ySpan);
        } else if (placement == VerticalPlacement.UNDERGROUND) {
            newY = getRandomWithinInterval(random, minY, surfaceYAtCenter - ySpan);
        } else if (placement == VerticalPlacement.PARTLY_BURIED) {
            newY = surfaceYAtCenter - ySpan + StructureRng.randomBetweenInclusive(random, 2, 8);
        } else {
            newY = surfaceYAtCenter;
        }

        var corners = List.of(
                new int[]{box.minX(), box.minZ()},
                new int[]{box.maxX(), box.minZ()},
                new int[]{box.minX(), box.maxZ()},
                new int[]{box.maxX(), box.maxZ()});
        var oceanFloor = placement == VerticalPlacement.ON_OCEAN_FLOOR;

        var projectedY = newY;
        for (; projectedY > minY; projectedY--) {
            var cornersOnSolidGround = 0;
            for (var corner : corners) {
                var block = this.terrainBlock(corner[0], projectedY, corner[1], settings);
                if (isOpaque(block, oceanFloor)) {
                    if (++cornersOnSolidGround == 3) {
                        return projectedY;
                    }
                }
            }
        }
        return projectedY;
    }

    private static int getRandomWithinInterval(WorldgenRandom random, int minPreferred, int max) {
        return minPreferred < max ? StructureRng.randomBetweenInclusive(random, minPreferred, max) : max;
    }

    private static boolean isOpaque(Block block, boolean oceanFloor) {
        return oceanFloor ? block.solid() : !block.air();
    }

    private void spreadNetherrack(RandomSource random, GenerationUnitAdapter level, RuinedPortalStart start) {
        var box = start.bounds();
        var placement = start.placement();
        var followGroundSurface = placement == VerticalPlacement.ON_LAND_SURFACE
                || placement == VerticalPlacement.ON_OCEAN_FLOOR;
        var center = start.center();
        var centerX = center.blockX();
        var centerZ = center.blockZ();
        var maxDistance = NETHERRACK_PROBABILITY_BY_DISTANCE.length;
        var averageWidth = (box.getXSpan() + box.getZSpan()) / 2;
        if (System.getProperty("worldgen.portalDebug") != null) {
            System.out.println("PSTART box=" + box + " avgWidth=" + averageWidth
                    + " rng=" + rngState(random)
                    + " count=" + (random instanceof WorldgenRandom worldgenRandom ? worldgenRandom.getCount() : -1));
        }
        var distanceAdjustment = random.nextInt(Math.max(1, 8 - averageWidth / 2));

        for (var x = centerX - maxDistance; x <= centerX + maxDistance; x++) {
            for (var z = centerZ - maxDistance; z <= centerZ + maxDistance; z++) {
                var distance = Math.abs(x - centerX) + Math.abs(z - centerZ);
                var adjustedDistance = Math.max(0, distance + distanceAdjustment);
                if (adjustedDistance < maxDistance) {
                    var probabilityOfNetherrack = NETHERRACK_PROBABILITY_BY_DISTANCE[adjustedDistance];
                    var roll = random.nextDouble() < probabilityOfNetherrack;
                    if (System.getProperty("worldgen.portalDebug") != null) {
                        System.out.println("PSPREAD " + x + "," + z + " roll=" + roll
                                + " surfaceY=" + this.getSurfaceY(level, x, z, placement));
                    }
                    if (roll) {
                        var surfaceY = this.getSurfaceY(level, x, z, placement);
                        var y = followGroundSurface ? surfaceY : Math.min(box.minY(), surfaceY);
                        var pos = new BlockVec(x, y, z);
                        if (Math.abs(y - box.minY()) <= 3 && this.canBlockBeReplacedByNetherrackOrMagma(level, pos, placement)) {
                            this.placeNetherrackOrMagma(random, level, pos, start.properties());
                            if (start.properties().overgrown()) {
                                this.maybeAddLeavesAbove(random, level, pos, start.properties());
                            }
                            this.addNetherrackDripColumn(random, level, pos.add(0, -1, 0), start.properties());
                        }
                    }
                }
            }
        }
    }

    private void addNetherrackDripColumnsBelowPortal(RandomSource random, GenerationUnitAdapter level,
            RuinedPortalStart start) {
        var box = start.bounds();
        for (var x = box.minX() + 1; x < box.maxX(); x++) {
            for (var z = box.minZ() + 1; z < box.maxZ(); z++) {
                var pos = new BlockVec(x, box.minY(), z);
                if (level.getBlock(pos.blockX(), pos.blockY(), pos.blockZ()).compare(Block.NETHERRACK)) {
                    this.addNetherrackDripColumn(random, level, pos.add(0, -1, 0), start.properties());
                }
            }
        }
    }

    private void addNetherrackDripColumn(RandomSource random, GenerationUnitAdapter level, BlockVec pos,
            Properties properties) {
        var current = pos;
        this.placeNetherrackOrMagma(random, level, current, properties);
        var remainingCap = 8;
        while (remainingCap > 0 && random.nextFloat() < 0.5F) {
            current = current.add(0, -1, 0);
            remainingCap--;
            this.placeNetherrackOrMagma(random, level, current, properties);
        }
    }

    private boolean canBlockBeReplacedByNetherrackOrMagma(GenerationUnitAdapter level, BlockVec pos,
            VerticalPlacement placement) {
        var state = level.getBlock(pos.blockX(), pos.blockY(), pos.blockZ());
        return !state.air()
                && !state.compare(Block.OBSIDIAN)
                && !this.structureLoader.blockTags().blocks(FEATURES_CANNOT_REPLACE).contains(state.key())
                && (placement == VerticalPlacement.IN_NETHER || !state.compare(Block.LAVA));
    }

    private void placeNetherrackOrMagma(RandomSource random, GenerationUnitAdapter level, BlockVec pos,
            Properties properties) {
        if (!properties.cold() && random.nextFloat() < 0.07F) {
            level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.MAGMA_BLOCK);
        } else {
            level.setBlock(pos.blockX(), pos.blockY(), pos.blockZ(), Block.NETHERRACK);
        }
    }

    /**
     * Vanilla {@code getSurfaceY} asks for the WG heightmap: the surrounding
     * chunks are still proto chunks during the pregen ladder, so the value is
     * the frozen post-carver heightmap, not one primed over structure and
     * feature writes.
     */
    private int getSurfaceY(GenerationUnitAdapter level, int x, int z, VerticalPlacement placement) {
        var height = placement == VerticalPlacement.ON_OCEAN_FLOOR
                ? level.frozenOceanFloor(x, z)
                : level.frozenWorldSurface(x, z);
        return height - 1;
    }

    private void maybeAddVines(RandomSource random, GenerationUnitAdapter level, BlockVec pos) {
        var state = level.getBlock(pos.blockX(), pos.blockY(), pos.blockZ());
        if (state.air() || state.compare(Block.VINE)) {
            return;
        }
        var direction = Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
        var neighbourPos = direction.relative(pos);
        var neighbourState = level.getBlock(neighbourPos.blockX(), neighbourPos.blockY(), neighbourPos.blockZ());
        if (neighbourState.air() && isFaceFull(state)) {
            var face = direction.opposite().serializedName();
            level.setBlock(neighbourPos.blockX(), neighbourPos.blockY(), neighbourPos.blockZ(),
                    Block.VINE.withProperty(face, "true"));
        }
    }

    private void maybeAddLeavesAbove(RandomSource random, GenerationUnitAdapter level, BlockVec pos,
            Properties properties) {
        if (random.nextFloat() < 0.5F
                && level.getBlock(pos.blockX(), pos.blockY(), pos.blockZ()).compare(Block.NETHERRACK)
                && level.getBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ()).air()) {
            level.setBlock(pos.blockX(), pos.blockY() + 1, pos.blockZ(),
                    Block.JUNGLE_LEAVES.withProperty("persistent", "true"));
        }
    }

    private static boolean isFaceFull(Block state) {
        var shape = state.collisionShape();
        var startCorner = shape.relativeStart();
        var endCorner = shape.relativeEnd();
        return startCorner.x() == 0.0 && startCorner.y() == 0.0 && startCorner.z() == 0.0
                && endCorner.x() == 1.0 && endCorner.y() == 1.0 && endCorner.z() == 1.0;
    }

    private int baseHeight(int blockX, int blockZ, boolean oceanFloor, NoiseGeneratorSettingsRuntime settings) {
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

    private DataPackBiomeResolver biomeResolver() {
        var cached = this.biomeResolver;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (this.biomeResolver == null) {
                this.biomeResolver = new DataPackBiomeResolver(this.featureLoader.dataPack());
            }
            return this.biomeResolver;
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

    private List<Key> loadStepStructures(String step) {
        return this.structureLoader.structuresAtStep(step);
    }


    private static long packChunk(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    private static BoundingBox copyBounds(BoundingBox bounds) {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private record RuinedPortalStart(
            Key structureKey,
            Key templateKey,
            BlockVec origin,
            BlockVec placementPosition,
            Rotation rotation,
            Mirror mirror,
            VerticalPlacement placement,
            Properties properties,
            BoundingBox bounds,
            BlockVec center) {
    }

    private static String rngState(RandomSource random) {
        try {
            var sourceField = rocks.minestom.worldgen.random.WorldgenRandom.class.getDeclaredField("randomSource");
            sourceField.setAccessible(true);
            var source = sourceField.get(random);
            var generatorField = source.getClass().getDeclaredField("randomNumberGenerator");
            generatorField.setAccessible(true);
            var generator = generatorField.get(source);
            var loField = generator.getClass().getDeclaredField("seedLo");
            var hiField = generator.getClass().getDeclaredField("seedHi");
            loField.setAccessible(true);
            hiField.setAccessible(true);
            return loField.getLong(generator) + "," + hiField.getLong(generator);
        } catch (ReflectiveOperationException exception) {
            return "?";
        }
    }
}

