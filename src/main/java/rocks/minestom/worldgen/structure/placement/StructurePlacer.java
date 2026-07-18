package rocks.minestom.worldgen.structure.placement;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.generator.GenerationUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeatureLoader;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.feature.RandomSelectorFeature;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.*;
import rocks.minestom.worldgen.structure.assembly.JigsawAssembler;
import rocks.minestom.worldgen.structure.endcity.EndCityPieces;
import rocks.minestom.worldgen.structure.endcity.EndCityStructure;
import rocks.minestom.worldgen.structure.fortress.FortressPlacer;
import rocks.minestom.worldgen.structure.fortress.FortressStructure;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.mineshaft.MineshaftPlacer;
import rocks.minestom.worldgen.structure.mansion.MansionPlacer;
import rocks.minestom.worldgen.structure.monument.MonumentPlacer;
import rocks.minestom.worldgen.structure.igloo.IglooPlacer;
import rocks.minestom.worldgen.structure.netherfossil.NetherFossilPlacer;
import rocks.minestom.worldgen.structure.oceanruin.OceanRuinPlacer;
import rocks.minestom.worldgen.structure.pool.*;
import rocks.minestom.worldgen.structure.ruinedportal.RuinedPortalPlacer;
import rocks.minestom.worldgen.structure.shipwreck.ShipwreckPlacer;
import rocks.minestom.worldgen.structure.stronghold.StrongholdPlacer;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.scattered.ScatteredFeaturePlacer;
import rocks.minestom.worldgen.structure.scattered.ScatteredFeatureStructure;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;
import rocks.minestom.worldgen.terrain.Beardifier;
import rocks.minestom.worldgen.terrain.TerrainGenerator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Structures are cached because they often span multiple chunks. When the
 * start chunk generates, the entire structure is assembled and stored. Then
 * each overlapping chunk places only the pieces within its bounds.
 *
 * @see StructureSet for structure grouping and placement rules
 * @see Structure for the structure types
 */
public final class StructurePlacer {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructurePlacer.class);
    /**
     * How far away (in chunks) an end city start can lie while its pieces
     * still reach the generating chunk. End cities recurse up to
     * {@code EndCityPieces.MAX_GEN_DEPTH} levels deep through bridges that
     * each span multiple chunks (the end ship alone can sit up to 79 blocks
     * from its bridge), so this is deliberately generous.
     */
    private static final int END_CITY_SCAN_RADIUS = 8;

    private final StructureLoader structureLoader;
    private final FeatureLoader featureLoader;
    private final List<Key> structureSets;
    private final Map<StartKey, Optional<StructureStart>> structureStarts;
    private final Map<Key, Integer> scanRadii;
    private final Map<Key, Boolean> setsWithAdaptation;
    private final Map<Long, Integer> startSurfaceHeights;
    private final MineshaftPlacer mineshaftPlacer;
    private final FortressPlacer fortressPlacer;
    private final ScatteredFeaturePlacer scatteredFeaturePlacer;
    private final MonumentPlacer monumentPlacer;
    private final StrongholdPlacer strongholdPlacer;
    private final MansionPlacer mansionPlacer;
    private final OceanRuinPlacer oceanRuinPlacer;
    private final ShipwreckPlacer shipwreckPlacer;
    private final RuinedPortalPlacer ruinedPortalPlacer;
    private final IglooPlacer iglooPlacer;
    private final NetherFossilPlacer netherFossilPlacer;

    public StructurePlacer(StructureLoader structureLoader, FeatureLoader featureLoader, List<Key> structureSets) {
        this.structureLoader = structureLoader;
        this.featureLoader = featureLoader;
        this.structureSets = structureSets;
        this.structureStarts = new ConcurrentHashMap<>();
        this.scanRadii = new ConcurrentHashMap<>();
        this.setsWithAdaptation = new ConcurrentHashMap<>();
        this.startSurfaceHeights = new ConcurrentHashMap<>();
        this.mineshaftPlacer = new MineshaftPlacer(structureLoader, featureLoader);
        this.fortressPlacer = new FortressPlacer(structureLoader, featureLoader);
        this.scatteredFeaturePlacer = new ScatteredFeaturePlacer(structureLoader, featureLoader);
        this.monumentPlacer = new MonumentPlacer(structureLoader, featureLoader);
        this.strongholdPlacer = new StrongholdPlacer(structureLoader, featureLoader);
        this.mansionPlacer = new MansionPlacer(structureLoader);
        this.oceanRuinPlacer = new OceanRuinPlacer(structureLoader);
        this.shipwreckPlacer = new ShipwreckPlacer(structureLoader, featureLoader);
        this.ruinedPortalPlacer = new RuinedPortalPlacer(structureLoader, featureLoader);
        this.iglooPlacer = new IglooPlacer(structureLoader);
        this.netherFossilPlacer = new NetherFossilPlacer(structureLoader);
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
        var chunkBounds = new BoundingBox(
                startX,
                settings.minY(),
                startZ,
                startX + unit.size().blockX() - 1,
                settings.maxYInclusive(),
                startZ + unit.size().blockZ() - 1);

        // Write through the generator's live chunk buffer when available so
        // structure blocks are visible to this chunk's decoration and later
        // neighbors, like vanilla's proto-chunk writes.
        var lookup = validatedLookup(chunkX, chunkZ, surfaceHeights);
        var adapter = lookup != null
                ? new GenerationUnitAdapter(unit, startX, startZ, unit.size().blockX(), unit.size().blockZ(),
                        settings.minY(), lookup.terrain(chunkX, chunkZ).blocks(), settings.height(), null)
                : new GenerationUnitAdapter(unit);

        StructureSet fortressStructureSet = null;
        for (var structureSetId : this.structureSets) {
            var structureSet = this.structureLoader.getStructureSet(structureSetId);
            if (structureSet == null) {
                continue;
            }

            if (this.mineshaftPlacer.isMineshaftSet(structureSet)) {
                // Procedural piece structures use vanilla-exact seeding and
                // their own multi-chunk placement path.
                this.mineshaftPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.scatteredFeaturePlacer.isScatteredFeatureSet(structureSet)) {
                // Desert pyramid, jungle temple, swamp hut and buried
                // treasure are also procedural single pieces rather than
                // templates.
                this.scatteredFeaturePlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.strongholdPlacer.isStrongholdSet(structureSet)) {
                // Like the mineshaft, the strongholds set contains a single
                // procedural structure with its own multi-chunk placement path.
                this.strongholdPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.mansionPlacer.isMansionSet(structureSet)) {
                // The woodland mansion is a single grid-solved template
                // structure with its own multi-chunk placement path.
                this.mansionPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.oceanRuinPlacer.isOceanRuinSet(structureSet)) {
                // Ocean ruins draw a large/cluster roll and resolve each
                // piece's own floor height, unlike the generic template path,
                // so - like the scattered features - this placer fully
                // replaces the generic path for its set.
                this.oceanRuinPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.shipwreckPlacer.isShipwreckSet(structureSet)) {
                // Shipwrecks pick from a beached/ocean-specific template pool,
                // rotate around a non-zero pivot and resolve their own
                // height, so this placer also fully replaces the generic
                // path for its set.
                this.shipwreckPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.ruinedPortalPlacer.isRuinedPortalSet(structureSet)) {
                // Ruined portals pick a weighted setup, resolve a buried or
                // surface Y, apply weathering processors and then spread
                // netherrack once in the center chunk, so this placer fully
                // replaces the generic path for its set.
                this.ruinedPortalPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.iglooPlacer.isIglooSet(structureSet)) {
                // Igloos roll a 50% basement (an igloo/bottom laboratory plus
                // an igloo/middle ladder shaft) and each piece resolves its own
                // surface height, so this placer fully replaces the generic
                // path for its set.
                this.iglooPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.netherFossilPlacer.isNetherFossilSet(structureSet)) {
                // Nether fossils resolve their position through a height
                // provider and raw-column scan rather than the generic
                // chunk-center surface height, so this placer fully replaces
                // the generic path for its set.
                this.netherFossilPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.monumentPlacer.isMonumentSet(structureSet)) {
                // The ocean monument is a single procedural top piece
                // (its child rooms are dispatched internally), so - like
                // mineshaft and scattered features - it fully replaces the
                // generic path for its set rather than complementing it.
                this.monumentPlacer.place(unit, biomeZoomer, settings, structureSet, surfaceHeights);
                continue;
            }

            if (this.fortressPlacer.isFortressSet(structureSet)) {
                fortressStructureSet = structureSet;
            }

            // Starts are pure functions of their start chunk, so scan every
            // candidate start chunk whose pieces could reach this chunk and
            // build them on demand (memoized). This places structures whose
            // start chunk generates later than - or outside of - the chunks
            // their pieces intersect (trial chambers span 8+ chunks).
            var radius = this.scanRadius(structureSetId, structureSet);
            for (var sourceX = chunkX - radius; sourceX <= chunkX + radius; sourceX++) {
                for (var sourceZ = chunkZ - radius; sourceZ <= chunkZ + radius; sourceZ++) {
                    var structureStart = this.startAt(structureSetId, structureSet, sourceX, sourceZ,
                            biomeZoomer, settings);
                    if (structureStart == null || !structureStart.bounds().intersects(chunkBounds)) {
                        continue;
                    }

                    this.placeStart(structureStart, chunkBounds, adapter, settings, surfaceHeights,
                            startX, startZ, unit.size().blockX(), unit.size().blockZ());
                }
            }
        }

        if (fortressStructureSet != null) {
            // Unlike mineshafts, nether_complexes mixes the procedural
            // fortress with the jigsaw bastion remnant, so the fortress
            // placer runs in addition to (not instead of) the generic path
            // above, which places bastion remnant (and any other structure
            // set's, e.g. a nether ruined portal's) starts. Vanilla decorates
            // every other nether structure step (surface_structures for
            // bastion and ruined portal) before fortress's
            // underground_decoration step (the last structure-placing step
            // before vegetal decoration), so where two structures overlap at
            // a set cell boundary, fortress's later write wins - this placer
            // must therefore run after every structure set's generic path,
            // not interleaved with it, so its unconditional block writes land
            // on top of anything already placed this chunk.
            this.fortressPlacer.place(unit, biomeZoomer, settings, fortressStructureSet, surfaceHeights);
        }
    }

    /**
     * Finds the nearest chunk that starts the given structure, scanning
     * outward in square rings from the center chunk like vanilla locate.
     * Applies the same placement, weighted selection and biome checks as
     * generation without assembling any pieces, so the returned position is
     * the start candidate's chunk center at the raw terrain surface.
     */
    public BlockVec locateNearest(Key structureKey, int centerChunkX, int centerChunkZ, int radiusChunks,
            BiomeZoomer biomeZoomer, NoiseGeneratorSettingsRuntime settings) {
        return this.locateNearest(structureKey, centerChunkX, centerChunkZ, radiusChunks, java.util.Set.of(),
                biomeZoomer, settings);
    }

    /**
     * Same as {@link #locateNearest(Key, int, int, int, BiomeZoomer, NoiseGeneratorSettingsRuntime)}, but
     * skips any start chunk packed into {@code excludedChunkKeys} (as {@code (long) chunkX << 32 | chunkZ &
     * 0xffffffffL}), letting callers enumerate the nearest N instances of a structure by excluding starts
     * already found.
     */
    public BlockVec locateNearest(Key structureKey, int centerChunkX, int centerChunkZ, int radiusChunks,
            java.util.Set<Long> excludedChunkKeys, BiomeZoomer biomeZoomer, NoiseGeneratorSettingsRuntime settings) {
        var candidateSets = new ArrayList<Map.Entry<Key, StructureSet>>();
        for (var structureSetId : this.structureSets) {
            var structureSet = this.structureLoader.getStructureSet(structureSetId);
            if (structureSet == null) {
                continue;
            }
            for (var entry : structureSet.structures()) {
                if (entry.structure().equals(structureKey)) {
                    candidateSets.add(Map.entry(structureSetId, structureSet));
                    break;
                }
            }
        }
        if (candidateSets.isEmpty()) {
            return null;
        }

        for (var ring = 0; ring <= radiusChunks; ring++) {
            BlockVec best = null;
            var bestDistanceSquared = Long.MAX_VALUE;
            for (var chunkX = centerChunkX - ring; chunkX <= centerChunkX + ring; chunkX++) {
                for (var chunkZ = centerChunkZ - ring; chunkZ <= centerChunkZ + ring; chunkZ++) {
                    if (Math.max(Math.abs(chunkX - centerChunkX), Math.abs(chunkZ - centerChunkZ)) != ring) {
                        continue;
                    }
                    var chunkKey = ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
                    if (excludedChunkKeys.contains(chunkKey)) {
                        continue;
                    }
                    var candidate = this.startCandidate(structureKey, candidateSets, chunkX, chunkZ,
                            biomeZoomer, settings);
                    if (candidate == null) {
                        continue;
                    }
                    var deltaX = (long) (chunkX - centerChunkX);
                    var deltaZ = (long) (chunkZ - centerChunkZ);
                    var distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                    if (distanceSquared < bestDistanceSquared) {
                        bestDistanceSquared = distanceSquared;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * The start position if the given structure would start in this chunk,
     * mirroring {@link #computeStart}'s selection and biome checks without
     * assembling pieces (like vanilla locate, assembly failures that would
     * fall through to the next weighted option are not simulated).
     */
    private BlockVec startCandidate(Key structureKey, List<Map.Entry<Key, StructureSet>> candidateSets,
            int chunkX, int chunkZ, BiomeZoomer biomeZoomer, NoiseGeneratorSettingsRuntime settings) {
        for (var setEntry : candidateSets) {
            var structureSet = setEntry.getValue();
            if (!structureSet.placement().isStartChunk(chunkX, chunkZ, settings.randomState().seed(),
                    settings.randomState().legacyRandomSource(), biomeZoomer.source(), this.structureLoader.biomeTags())) {
                continue;
            }

            var centerX = (chunkX << 4) + 8;
            var centerZ = (chunkZ << 4) + 8;
            var surfaceY = this.surfaceYAt(chunkX, chunkZ, settings);
            var biomeKey = biomeZoomer.biome(centerX, surfaceY, centerZ);

            if (structureSet.structures().size() == 1) {
                var only = structureSet.structures().getFirst().structure();
                if (only.equals(structureKey) && this.passesSeaLevelGate(only, chunkX, chunkZ, settings)
                        && this.biomeMatches(only, biomeKey)) {
                    return new BlockVec(centerX, surfaceY, centerZ);
                }
                continue;
            }

            var options = new ArrayList<>(structureSet.structures());
            var random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(settings.randomState().seed(), chunkX, chunkZ);
            var total = 0;
            for (var option : options) {
                total += option.weight();
            }

            while (!options.isEmpty() && total > 0) {
                var index = pickWeightedIndex(options, random.nextInt(total));
                var selected = options.get(index);
                if (this.passesSeaLevelGate(selected.structure(), chunkX, chunkZ, settings)
                        && this.biomeMatches(selected.structure(), biomeKey)) {
                    if (selected.structure().equals(structureKey)) {
                        return new BlockVec(centerX, surfaceY, centerZ);
                    }
                    break;
                }
                options.remove(index);
                total -= selected.weight();
            }
        }
        return null;
    }

    private boolean biomeMatches(Key structureKey, Key biomeKey) {
        var structure = this.structureLoader.getStructure(structureKey);
        return structure != null && structure.biomes().matches(biomeKey, this.structureLoader.biomeTags());
    }

    /**
     * Vanilla {@code SinglePieceStructure.findGenerationPoint}: the desert
     * pyramid and jungle temple refuse to start at all (before ever sampling
     * a biome) if the lowest {@code WORLD_SURFACE_WG} corner height across
     * their footprint dips below sea level. The swamp hut is a plain
     * {@code Structure}, not a {@code SinglePieceStructure}, so it has no
     * such gate. Mirrors {@code ScatteredFeaturePlacer}'s own gate so this
     * locate-only path agrees with what generation would actually place.
     */
    private boolean passesSeaLevelGate(Key structureKey, int chunkX, int chunkZ, NoiseGeneratorSettingsRuntime settings) {
        var structure = this.structureLoader.getStructure(structureKey);
        if (!(structure instanceof ScatteredFeatureStructure scattered) || !scattered.kind().hasSeaLevelGate()) {
            return true;
        }

        var chunkMinX = chunkX << 4;
        var chunkMinZ = chunkZ << 4;
        var width = scattered.kind().footprintWidth();
        var depth = scattered.kind().footprintDepth();
        var lowestY = Math.min(
                Math.min(this.rawSurfaceHeight(chunkMinX, chunkMinZ, settings),
                        this.rawSurfaceHeight(chunkMinX, chunkMinZ + depth, settings)),
                Math.min(this.rawSurfaceHeight(chunkMinX + width, chunkMinZ, settings),
                        this.rawSurfaceHeight(chunkMinX + width, chunkMinZ + depth, settings)));
        return lowestY >= settings.seaLevel();
    }

    /**
     * The raw noise terrain's topmost solid block at an arbitrary block
     * position, matching vanilla's {@code getFirstOccupiedHeight}.
     */
    private int rawSurfaceHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = new TerrainGenerator(settings).generate(chunkX, chunkZ);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() - 1 : solidTop;
    }

    /**
     * Vanilla {@code Beardifier.forStructuresInChunk}: collects, from every
     * start with a terrain adaptation whose bounds intersect the chunk (the
     * structure-reference criterion), the rigid pool pieces within the
     * 12-block beard kernel range plus their jigsaw junctions. Pure memoized
     * function of the chunk position - it is called from the terrain phase,
     * so start computation must never read bearded terrain.
     */
    public Beardifier beardifier(int chunkX, int chunkZ, BiomeZoomer biomeZoomer,
            NoiseGeneratorSettingsRuntime settings) {
        if (this.structureSets.isEmpty()) {
            return Beardifier.EMPTY;
        }

        var chunkStartBlockX = chunkX * 16;
        var chunkStartBlockZ = chunkZ * 16;
        var rigids = new ArrayList<Beardifier.Rigid>();
        var junctions = new ArrayList<Beardifier.Junction>();

        for (var structureSetId : this.structureSets) {
            var structureSet = this.structureLoader.getStructureSet(structureSetId);
            if (structureSet == null || !this.hasTerrainAdaptation(structureSetId, structureSet)) {
                continue;
            }

            if (this.strongholdPlacer.isStrongholdSet(structureSet)) {
                // The stronghold is generated by its own piece-based placer
                // rather than the generic template path below, so its beard
                // contribution is queried directly from that placer's cache.
                this.strongholdPlacer.contributeBeard(chunkX, chunkZ, structureSet, settings, biomeZoomer, rigids);
                continue;
            }

            if (this.netherFossilPlacer.isNetherFossilSet(structureSet)) {
                // Nether fossils are generated by their own placer rather
                // than the generic template path below, so their beard_thin
                // contribution is queried directly from that placer's cache.
                this.netherFossilPlacer.contributeBeard(chunkX, chunkZ, structureSet, settings, biomeZoomer, rigids);
                continue;
            }

            var radius = this.scanRadius(structureSetId, structureSet);
            for (var sourceX = chunkX - radius; sourceX <= chunkX + radius; sourceX++) {
                for (var sourceZ = chunkZ - radius; sourceZ <= chunkZ + radius; sourceZ++) {
                    var start = this.startAt(structureSetId, structureSet, sourceX, sourceZ, biomeZoomer, settings);
                    if (start == null || start.terrainAdaptation() == TerrainAdjustment.NONE) {
                        continue;
                    }

                    // Vanilla reference semantics: only starts whose overall
                    // bounds reach the chunk itself contribute.
                    if (!intersectsXZ(start.bounds(), chunkStartBlockX, chunkStartBlockZ,
                            chunkStartBlockX + 15, chunkStartBlockZ + 15)) {
                        continue;
                    }

                    for (var piece : start.pieces()) {
                        // Vanilla StructurePiece.isCloseToChunk(chunkPos, 12).
                        if (!intersectsXZ(piece.bounds(), chunkStartBlockX - 12, chunkStartBlockZ - 12,
                                chunkStartBlockX + 15 + 12, chunkStartBlockZ + 15 + 12)) {
                            continue;
                        }

                        if (piece.element().projection() == Projection.RIGID) {
                            rigids.add(new Beardifier.Rigid(piece.bounds(), start.terrainAdaptation(),
                                    piece.groundLevelDelta()));
                        }

                        for (var junction : piece.junctions()) {
                            if (junction.sourceX() > chunkStartBlockX - 12
                                    && junction.sourceZ() > chunkStartBlockZ - 12
                                    && junction.sourceX() < chunkStartBlockX + 15 + 12
                                    && junction.sourceZ() < chunkStartBlockZ + 15 + 12) {
                                junctions.add(new Beardifier.Junction(
                                        junction.sourceX(), junction.sourceGroundY(), junction.sourceZ()));
                            }
                        }
                    }
                }
            }
        }

        return Beardifier.create(rigids, junctions);
    }

    /**
     * Whether any structure of the set declares a terrain adaptation, so the
     * beard query can skip scanning sets that can never contribute.
     */
    private boolean hasTerrainAdaptation(Key structureSetId, StructureSet structureSet) {
        return this.setsWithAdaptation.computeIfAbsent(structureSetId, unused -> {
            for (var entry : structureSet.structures()) {
                var structure = this.structureLoader.getStructure(entry.structure());
                if (structure != null && structure.terrainAdaptation() != TerrainAdjustment.NONE) {
                    return true;
                }
            }
            return false;
        });
    }

    private static boolean intersectsXZ(BoundingBox bounds, int minX, int minZ, int maxX, int maxZ) {
        return bounds.maxX() >= minX && bounds.minX() <= maxX
                && bounds.maxZ() >= minZ && bounds.minZ() <= maxZ;
    }

    /**
     * The generator's terrain lookup captured from the feature phase,
     * validated by surface-height array identity so a lookup from another
     * generator (other dimension) is never used.
     */
    private static GenerationUnitAdapter.TerrainLookup validatedLookup(int chunkX, int chunkZ, int[] surfaceHeights) {
        var lookup = StructureWrites.terrainLookup();
        if (lookup == null || surfaceHeights == null) {
            return null;
        }

        try {
            return lookup.terrain(chunkX, chunkZ).surfaceHeights() == surfaceHeights ? lookup : null;
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * How far away (in chunks) a start of this set can lie while its pieces
     * still reach the generating chunk.
     */
    private int scanRadius(Key structureSetId, StructureSet structureSet) {
        var cached = this.scanRadii.get(structureSetId);
        if (cached != null) {
            return cached;
        }

        // Simple template structures span at most a couple of chunks.
        var radius = 2;
        for (var entry : structureSet.structures()) {
            var structure = this.structureLoader.getStructure(entry.structure());
            if (structure instanceof JigsawStructure jigsaw) {
                var span = Math.max(jigsaw.maxDistanceFromCenter(), 80);
                radius = Math.max(radius, span / 16 + 2);
            } else if (structure instanceof EndCityStructure) {
                radius = Math.max(radius, END_CITY_SCAN_RADIUS);
            }
        }

        this.scanRadii.put(structureSetId, radius);
        return radius;
    }

    private StructureStart startAt(Key structureSetId, StructureSet structureSet, int chunkX, int chunkZ,
            BiomeZoomer biomeZoomer, NoiseGeneratorSettingsRuntime settings) {
        var startKey = new StartKey(chunkKey(chunkX, chunkZ), structureSetId);
        return this.structureStarts.computeIfAbsent(startKey, unused -> Optional.ofNullable(
                this.computeStart(structureSetId, structureSet, chunkX, chunkZ, biomeZoomer, settings)))
                .orElse(null);
    }

    /**
     * Vanilla {@code ChunkGenerator} structure selection: a single-entry set
     * generates directly, while multi-entry sets draw weighted picks from a
     * per-chunk large-feature random, removing entries whose structure fails
     * (wrong biome, failed assembly) until one succeeds or none remain.
     */
    private StructureStart computeStart(Key structureSetId, StructureSet structureSet, int chunkX, int chunkZ,
            BiomeZoomer biomeZoomer, NoiseGeneratorSettingsRuntime settings) {
        var placement = structureSet.placement();
        if (!placement.isStartChunk(chunkX, chunkZ, settings.randomState().seed(),
                settings.randomState().legacyRandomSource(), biomeZoomer.source(), this.structureLoader.biomeTags())) {
            return null;
        }

        if (structureSet.structures().size() == 1) {
            return this.tryBuildStart(structureSet.structures().getFirst().structure(), chunkX, chunkZ,
                    biomeZoomer, settings);
        }

        var options = new ArrayList<>(structureSet.structures());
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(settings.randomState().seed(), chunkX, chunkZ);
        var total = 0;
        for (var option : options) {
            total += option.weight();
        }

        while (!options.isEmpty() && total > 0) {
            var index = pickWeightedIndex(options, random.nextInt(total));
            var selected = options.get(index);
            if (this.structureLoader.getStructure(selected.structure()) instanceof FortressStructure fortress) {
                // Handled entirely by FortressPlacer, which replicates this
                // same weighted draw. Vanilla's piece-based fortress always
                // succeeds once its (random-independent) biome check passes
                // - it never falls through to the next weighted option the
                // way a failed jigsaw assembly would - so the generic path
                // must stop here rather than spuriously trying e.g. bastion
                // remnant instead. If the biome check fails, fall through to
                // the normal remove-and-retry path like any other candidate.
                if (this.fortressBiomeMatches(fortress, chunkX, chunkZ, biomeZoomer)) {
                    return null;
                }
                options.remove(index);
                total -= selected.weight();
                continue;
            }
            var start = this.tryBuildStart(selected.structure(), chunkX, chunkZ, biomeZoomer, settings);
            if (start != null) {
                return start;
            }
            options.remove(index);
            total -= selected.weight();
        }
        return null;
    }

    /**
     * Vanilla {@code NetherFortressStructure.findGenerationPoint}: a fixed
     * stub position (chunk min X/Z, y=64) independent of any piece geometry
     * or random state.
     */
    private boolean fortressBiomeMatches(FortressStructure fortress, int chunkX, int chunkZ, BiomeZoomer biomeZoomer) {
        var stubX = chunkX << 4;
        var stubZ = chunkZ << 4;
        var biome = biomeZoomer.source().biome(stubX >> 2, 64 >> 2, stubZ >> 2);
        return fortress.biomes().matches(biome, this.structureLoader.biomeTags());
    }

    private static int pickWeightedIndex(List<StructureSet.StructureSelection> options, int choice) {
        var index = 0;
        for (var option : options) {
            choice -= option.weight();
            if (choice < 0) {
                break;
            }
            index++;
        }
        return index;
    }

    private StructureStart tryBuildStart(Key structureKey, int chunkX, int chunkZ,
            BiomeZoomer biomeZoomer, NoiseGeneratorSettingsRuntime settings) {
        var structure = this.structureLoader.getStructure(structureKey);
        if (structure == null) {
            return null;
        }

        if (structure instanceof EndCityStructure endCity) {
            // Vanilla EndCityStructure.findGenerationPoint: the biome check
            // runs at the computed start position (chunk corner + 7, lowest
            // corner height), not the chunk center used by every other
            // structure type here, so this bypasses the generic biome check
            // below entirely.
            return this.buildEndCityStructureStart(endCity, chunkX, chunkZ, settings, biomeZoomer);
        }

        var centerX = (chunkX << 4) + 8;
        var centerZ = (chunkZ << 4) + 8;
        var surfaceY = this.surfaceYAt(chunkX, chunkZ, settings);
        var biomeKey = biomeZoomer.biome(centerX, surfaceY, centerZ);
        if (!structure.biomes().matches(biomeKey, this.structureLoader.biomeTags())) {
            return null;
        }

        if (structure instanceof JigsawStructure jigsaw) {
            return this.buildJigsawStructureStart(jigsaw, chunkX, chunkZ, settings);
        }

        if (structure instanceof SimpleStructure simple) {
            var start = new BlockVec(centerX, surfaceY, centerZ);
            return this.buildSimpleStructureStart(simple, start, settings);
        }

        return null;
    }

    /**
     * Surface height at the candidate start chunk's center, recomputed from
     * the raw noise terrain (memoized). Vanilla creates starts at the
     * STRUCTURE_STARTS stage, before the noise fill, so their height probes
     * see neither carving nor the beardifier - which also breaks the cycle
     * terrainData -> beardifier -> starts -> terrainData.
     */
    private int surfaceYAt(int chunkX, int chunkZ, NoiseGeneratorSettingsRuntime settings) {
        return this.startSurfaceHeights.computeIfAbsent(chunkKey(chunkX, chunkZ), unused -> {
            var surfaceY = new TerrainGenerator(settings).generate(chunkX, chunkZ).surfaceHeights()[8 * 16 + 8];
            return surfaceY == Integer.MIN_VALUE ? settings.seaLevel() : surfaceY;
        });
    }

    private StructureStart buildJigsawStructureStart(JigsawStructure jigsaw, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings) {
        var assembler = new JigsawAssembler(this.structureLoader, settings);
        var pieces = assembler.assemble(jigsaw, chunkX, chunkZ);
        if (pieces.isEmpty()) {
            return null;
        }

        var bounds = copyBounds(pieces.getFirst().bounds());
        for (var pieceIndex = 1; pieceIndex < pieces.size(); pieceIndex++) {
            bounds.encapsulate(pieces.get(pieceIndex).bounds());
        }

        var start = new BlockVec((chunkX << 4) + 8, bounds.minY(), (chunkZ << 4) + 8);
        return new StructureStart(start, pieces, bounds, jigsaw.terrainAdaptation());
    }

    private StructureStart buildSimpleStructureStart(SimpleStructure simple, BlockVec start,
            NoiseGeneratorSettingsRuntime settings) {
        if (simple.templates().isEmpty()) {
            return null;
        }

        var randomFactory = settings.randomState().getOrCreateRandomFactory(Key.key("minecraft:structure"));
        var random = randomFactory.fromHashOf(start.blockX() + ":" + start.blockZ());
        var templateKey = simple.templates().get(random.nextInt(simple.templates().size()));
        var template = this.structureLoader.getTemplate(templateKey);
        if (template == null) {
            return null;
        }

        var rotation = Rotation.values()[random.nextInt(Rotation.values().length)];
        var bounds = template.getBoundingBox(start, rotation);

        var element = new SinglePoolElement(templateKey, StructureProcessorList.EMPTY, Projection.RIGID, null, false);
        var piece = new JigsawAssembler.PlacedPiece(element, start, rotation, bounds, 1,
                LiquidSettings.APPLY_WATERLOGGING);

        return new StructureStart(start, List.of(piece), copyBounds(bounds), simple.terrainAdaptation());
    }

    /**
     * Vanilla {@code EndCityStructure.findGenerationPoint}: draws a random
     * rotation, finds the lowest {@code WORLD_SURFACE_WG} corner of a 5x5
     * chunk box offset 7 blocks into the chunk (in the direction the
     * rotation faces), rejects anything below y=60, then - only if the biome
     * at that position matches - continues drawing from the same random to
     * recursively assemble the piece list ({@link EndCityPieces}).
     */
    private StructureStart buildEndCityStructureStart(EndCityStructure endCity, int chunkX, int chunkZ,
            NoiseGeneratorSettingsRuntime settings, BiomeZoomer biomeZoomer) {
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(settings.randomState().seed(), chunkX, chunkZ);
        var rotation = Rotation.getRandom(random);

        var offsetX = 5;
        var offsetZ = 5;
        switch (rotation) {
            case CLOCKWISE_90 -> offsetX = -5;
            case CLOCKWISE_180 -> {
                offsetX = -5;
                offsetZ = -5;
            }
            case COUNTERCLOCKWISE_90 -> offsetZ = -5;
            case NONE -> {
            }
        }

        var blockX = (chunkX << 4) + 7;
        var blockZ = (chunkZ << 4) + 7;
        var lowestY = this.getLowestY(blockX, blockZ, offsetX, offsetZ, settings);
        if (lowestY < 60) {
            return null;
        }

        var startPos = new BlockVec(blockX, lowestY, blockZ);
        var biome = biomeZoomer.source().biome(startPos.blockX() >> 2, startPos.blockY() >> 2, startPos.blockZ() >> 2);
        if (!endCity.biomes().matches(biome, this.structureLoader.biomeTags())) {
            return null;
        }

        var pieces = EndCityPieces.startHouseTower(this.structureLoader, startPos, rotation, random);
        if (pieces.isEmpty()) {
            return null;
        }

        var bounds = copyBounds(pieces.getFirst().boundingBox);
        var placedPieces = new ArrayList<JigsawAssembler.PlacedPiece>(pieces.size());
        for (var index = 0; index < pieces.size(); index++) {
            var piece = pieces.get(index);
            if (index > 0) {
                bounds.encapsulate(piece.boundingBox);
            }
            var element = new SinglePoolElement(piece.templateKey, StructureProcessorList.EMPTY, Projection.RIGID,
                    null, !piece.overwrite);
            placedPieces.add(new JigsawAssembler.PlacedPiece(element, piece.position, piece.rotation,
                    copyBounds(piece.boundingBox), 0, LiquidSettings.APPLY_WATERLOGGING));
        }

        return new StructureStart(startPos, placedPieces, bounds, endCity.terrainAdaptation());
    }

    /**
     * Vanilla {@code Structure.getLowestY}: the minimum of the four corner
     * {@code WORLD_SURFACE_WG} heights of the box anchored at (blockX,
     * blockZ) with the given (possibly negative) span.
     */
    private int getLowestY(int blockX, int blockZ, int offsetX, int offsetZ, NoiseGeneratorSettingsRuntime settings) {
        var a = this.worldSurfaceHeight(blockX, blockZ, settings);
        var b = this.worldSurfaceHeight(blockX, blockZ + offsetZ, settings);
        var c = this.worldSurfaceHeight(blockX + offsetX, blockZ, settings);
        var d = this.worldSurfaceHeight(blockX + offsetX, blockZ + offsetZ, settings);
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    /**
     * Approximation of vanilla {@code getFirstOccupiedHeight} for
     * {@code WORLD_SURFACE_WG}: one above the highest solid block of the raw
     * noise terrain, matching {@link rocks.minestom.worldgen.structure.scattered.ScatteredFeaturePlacer}'s
     * {@code worldSurfaceHeight}.
     */
    private int worldSurfaceHeight(int blockX, int blockZ, NoiseGeneratorSettingsRuntime settings) {
        var chunkX = Math.floorDiv(blockX, 16);
        var chunkZ = Math.floorDiv(blockZ, 16);
        var terrainData = new TerrainGenerator(settings).generate(chunkX, chunkZ);
        var index = (blockX - (chunkX << 4)) * 16 + (blockZ - (chunkZ << 4));
        var solidTop = terrainData.surfaceHeights()[index];
        return solidTop == Integer.MIN_VALUE ? settings.minY() : solidTop + 1;
    }

    private void placeStart(StructureStart structureStart, BoundingBox chunkBounds, GenerationUnitAdapter adapter,
            NoiseGeneratorSettingsRuntime settings, int[] surfaceHeights, int chunkStartX, int chunkStartZ,
            int chunkSizeX, int chunkSizeZ) {
        var pieces = structureStart.pieces();
        if (pieces.isEmpty()) {
            return;
        }

        // Vanilla StructureStart.placeInChunk: the reference position is the
        // center-x/z, min-y of the first piece's bounding box (vanilla center
        // rounding: min + (span / 2)).
        var firstBounds = pieces.getFirst().bounds();
        var referencePos = new BlockVec(
                firstBounds.minX() + (firstBounds.maxX() - firstBounds.minX() + 1) / 2,
                firstBounds.minY(),
                firstBounds.minZ() + (firstBounds.maxZ() - firstBounds.minZ() + 1) / 2);

        StructureProcessorContext.HeightSampler heightSampler = (x, z) -> {
            var localX = x - chunkStartX;
            var localZ = z - chunkStartZ;
            if (surfaceHeights == null || localX < 0 || localX >= chunkSizeX || localZ < 0 || localZ >= chunkSizeZ) {
                return Integer.MIN_VALUE;
            }
            var surfaceY = surfaceHeights[localX * chunkSizeZ + localZ];
            // Vanilla getHeight returns one above the heightmap surface.
            return surfaceY == Integer.MIN_VALUE ? Integer.MIN_VALUE : surfaceY + 1;
        };

        for (var piece : pieces) {
            if (!piece.bounds().intersects(chunkBounds)) {
                continue;
            }

            this.placePiece(piece, adapter, chunkBounds, referencePos, heightSampler, settings);
        }
    }

    private void placePiece(JigsawAssembler.PlacedPiece piece, GenerationUnitAdapter adapter,
            BoundingBox chunkBounds, BlockVec referencePos, StructureProcessorContext.HeightSampler heightSampler,
            NoiseGeneratorSettingsRuntime settings) {
        switch (piece.element()) {
            case SinglePoolElement single -> this.placeSingleElement(single, piece, adapter, chunkBounds,
                    referencePos, heightSampler, settings);
            case ListPoolElement list -> {
                // Vanilla ListPoolElement.place: every child places at the
                // same position/rotation with its own processors.
                for (var child : list.elements()) {
                    if (child instanceof SinglePoolElement single) {
                        this.placeSingleElement(single, piece, adapter, chunkBounds, referencePos, heightSampler,
                                settings);
                    }
                }
            }
            case FeaturePoolElement feature -> this.placeFeature(feature.feature(), piece.position(), adapter,
                    settings);
            case EmptyPoolElement ignored -> {
            }
        }
    }

    private void placeSingleElement(SinglePoolElement element, JigsawAssembler.PlacedPiece piece,
            GenerationUnitAdapter adapter, BoundingBox chunkBounds, BlockVec referencePos,
            StructureProcessorContext.HeightSampler heightSampler, NoiseGeneratorSettingsRuntime settings) {
        var template = this.structureLoader.getTemplate(element.location());
        if (template == null) {
            LOGGER.warn("Template not found: {}", element.location());
            return;
        }

        var liquidSettings = element.overrideLiquidSettings() != null
                ? element.overrideLiquidSettings()
                : piece.liquidSettings();
        var processorContext = new StructureProcessorContext(
                adapter,
                this.structureLoader.blockTags(),
                settings.randomState().seed(),
                piece.position(),
                referencePos,
                heightSampler);
        // Vanilla SinglePoolElement.getSettings always calls setKnownShape(true):
        // jigsaw pool elements skip the connection shape pass, so the queue is
        // never appended to.
        var placementContext = new StructureTemplate.PlacementContext(
                adapter, chunkBounds, processorContext, List.of());
        template.place(
                placementContext,
                piece.position(),
                piece.rotation(),
                element.processors(),
                element.legacy(),
                element.projection() == Projection.TERRAIN_MATCHING,
                liquidSettings,
                false);
    }

    private void placeFeature(Key featureKey, BlockVec position, GenerationUnitAdapter adapter,
            NoiseGeneratorSettingsRuntime settings) {
        var placedFeatureDefinition = this.featureLoader.getPlacedFeature(featureKey);
        if (placedFeatureDefinition == null) {
            LOGGER.warn("Placed feature not found: {}", featureKey);
            return;
        }

        var configuredFeature = this.featureLoader.getConfiguredFeature(placedFeatureDefinition.feature());
        if (configuredFeature == null) {
            LOGGER.warn("Configured feature not found: {}", placedFeatureDefinition.feature());
            return;
        }

        var randomFactory = settings.randomState().getOrCreateRandomFactory(Key.key("minecraft:structure"));
        var random = randomFactory.at(position.blockX(), position.blockY(), position.blockZ());
        var featureContext = new FeaturePlaceContext<>(
                adapter,
                random,
                position,
                configuredFeature.config(),
                settings.randomState().seed(),
                settings.minY(),
                settings.maxYInclusive(),
                settings.seaLevel());

        var featureImpl = configuredFeature.feature();
        if (featureImpl instanceof RandomSelectorFeature randomSelector) {
            randomSelector.place(featureContext, this.featureLoader);
        } else {
            ((Feature) featureImpl).place(featureContext);
        }
    }

    private static BoundingBox copyBounds(BoundingBox bounds) {
        return new BoundingBox(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private record StartKey(long chunkKey, Key structureSetId) {
    }

    private record StructureStart(
            BlockVec start,
            List<JigsawAssembler.PlacedPiece> pieces,
            BoundingBox bounds,
            TerrainAdjustment terrainAdaptation) {
    }

}
