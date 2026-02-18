package rocks.minestom.worldgen.structure.assembly;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.worldgen.density.ChunkContext;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.RandomSelectorFeature;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.JigsawStructure;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.structure.pool.*;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.JigsawBlockInfo;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

import java.util.*;

/**
 * Assembles jigsaw structures by connecting template pieces at jigsaw block
 * positions.
 *
 * <p>
 * The assembly algorithm:
 * <ol>
 * <li>Place the starting piece from the start pool
 * <li>Find all jigsaw blocks in placed pieces
 * <li>For each jigsaw, look up its target pool and try to connect a piece
 * <li>Pieces must match by name/target and not collide with existing pieces
 * <li>Continue until max depth is reached or no more pieces fit
 * </ol>
 *
 * <p>
 * Jigsaw blocks define connection points with:
 * <ul>
 * <li>{@code pool} - Which template pool to draw from
 * <li>{@code name} - This connection's identifier
 * <li>{@code target} - What name to connect to on the other piece
 * <li>{@code joint} - Rotation behavior (rollable or aligned)
 * </ul>
 *
 * @see JigsawStructure for the structure type that uses this
 * @see JigsawBlockInfo for jigsaw block data
 */
public final class JigsawAssembler {
    private static final Logger LOGGER = LoggerFactory.getLogger(JigsawAssembler.class);
    private static final int TERRAIN_MATCHING_OFFSET = -1;

    private final StructurePlaceContext context;
    private final int maxDepth;
    private final int maxDistanceFromCenter;
    private final List<PlacedPiece> placedPieces;
    private final List<PlacedFeature> placedFeatures;
    private final DensityFunction densityFunction;
    private final ChunkContext densityContext;
    private final Map<Long, Integer> surfaceHeightCache;
    private BlockVec startCenter;

    public JigsawAssembler(StructurePlaceContext context, int maxDepth, int maxDistanceFromCenter) {
        this.context = context;
        this.maxDepth = maxDepth;
        this.maxDistanceFromCenter = maxDistanceFromCenter;
        this.placedPieces = new ArrayList<>();
        this.placedFeatures = new ArrayList<>();
        this.densityFunction = context.settings().finalDensity();
        this.densityContext = new ChunkContext(context.settings().cellWidth(), context.settings().cellHeight());
        this.surfaceHeightCache = new HashMap<>();
    }

    public void assemble(Key startPoolKey) {
        var result = this.assemblePieces(startPoolKey);
        for (var piece : result.pieces()) {
            this.placePiece(piece);
        }
        for (var feature : result.features()) {
            this.placeFeature(feature);
        }
    }

    public AssemblyResult assemblePieces(Key startPoolKey) {
        this.placedPieces.clear();
        this.placedFeatures.clear();
        var startPool = this.context.structureLoader().getTemplatePool(startPoolKey);
        if (startPool == null) {
            LOGGER.warn("Start pool not found: {}", startPoolKey);
            return AssemblyResult.empty();
        }

        var random = this.context.randomFactory().at(
                this.context.start().blockX(),
                this.context.start().blockY(),
                this.context.start().blockZ());

        var initialElement = startPool.pick(random);
        if (initialElement == null) {
            LOGGER.warn("No element picked from start pool: {}", startPoolKey);
            return AssemblyResult.empty();
        }

        if (!(initialElement.element() instanceof LegacySinglePoolElement(
                var location, var processors, var projection
        ))) {
            LOGGER.warn("Initial element is not LegacySinglePoolElement: {}",
                    initialElement.element().getClass().getSimpleName());
            return AssemblyResult.empty();
        }

        var template = this.context.structureLoader().getTemplate(location);
        if (template == null) {
            LOGGER.warn("Template not found: {}", location);
            return AssemblyResult.empty();
        }

        LOGGER.info("Placing village structure at {}, start template: {}", this.context.start(),
                location);

        var rotation = Rotation.getRandom(random);
        var origin = this.context.start();

        // Debug: check jigsaws in initial template
        var initialJigsaws = template.getJigsaws(origin, rotation);
        LOGGER.info("Initial template has {} jigsaws", initialJigsaws.size());
        for (var jigsaw : initialJigsaws) {
            LOGGER.info("  - pool={}, name={}, target={}", jigsaw.pool(), jigsaw.name(), jigsaw.target());
        }

        var terrainMatching = "terrain_matching".equals(projection);
        var projectionOffset = this.resolveProjectionOffset(terrainMatching);
        if (terrainMatching) {
            var baseBounds = template.getBoundingBox(BlockVec.ZERO, rotation);
            var surfaceY = this.getSurfaceHeight(origin.blockX(), origin.blockZ());
            var adjustedY = surfaceY - baseBounds.minY();
            origin = new BlockVec(origin.blockX(), adjustedY, origin.blockZ());
        }
        var bounds = template.getBoundingBox(origin, rotation);
        var initialPiece = new PlacedPiece(
                template,
                location,
                origin,
                rotation,
                bounds,
                processors,
                0,
                terrainMatching,
                projectionOffset);
        this.startCenter = bounds.getCenter();
        this.placedPieces.add(initialPiece);

        var queue = new ArrayDeque<PlacedPiece>();
        queue.add(initialPiece);

        while (!queue.isEmpty()) {
            var piece = queue.poll();
            if (piece.depth >= this.maxDepth) {
                LOGGER.debug("Max depth {} reached", this.maxDepth);
                continue;
            }

            var children = this.tryPlaceChildren(piece);
            LOGGER.info("Piece at depth {} placed {} children, queue size now: {}",
                    piece.depth, children.size(), queue.size() + children.size());
            queue.addAll(children);
        }

        LOGGER.info("Village assembly complete. Total pieces placed: {}", this.placedPieces.size());
        return new AssemblyResult(List.copyOf(this.placedPieces), List.copyOf(this.placedFeatures));
    }

    private List<PlacedPiece> tryPlaceChildren(PlacedPiece parentPiece) {
        var children = new ArrayList<PlacedPiece>();
        var jigsaws = parentPiece.template.getJigsaws(parentPiece.origin, parentPiece.rotation);

        LOGGER.debug("tryPlaceChildren: piece has {} jigsaws, depth={}", jigsaws.size(), parentPiece.depth);

        for (var jigsaw : jigsaws) {
            var isHousePool = jigsaw.pool().asString().contains("house");
            if (isHousePool) {
                LOGGER.info("  HOUSE Jigsaw: pool={}, name={}, target={}, front={}",
                        jigsaw.pool(), jigsaw.name(), jigsaw.target(), jigsaw.front());
            } else {
                LOGGER.debug("  Jigsaw: pool={}, name={}, target={}, front={}",
                        jigsaw.pool(), jigsaw.name(), jigsaw.target(), jigsaw.front());
            }

            var targetPool = this.context.structureLoader().getTemplatePool(jigsaw.pool());
            if (targetPool == null) {
                LOGGER.warn("    Pool not found: {}", jigsaw.pool());
                continue;
            }

            if (isHousePool) {
                LOGGER.info("    Houses pool has {} elements", targetPool.elements().size());
            } else {
                LOGGER.debug("    Pool has {} elements", targetPool.elements().size());
            }

            var front = jigsaw.front();
            var connectionPos = jigsaw.position().add(front.normalX(), front.normalY(), front.normalZ());

            var random = this.context.randomFactory().at(
                    jigsaw.position().blockX(),
                    jigsaw.position().blockY(),
                    jigsaw.position().blockZ());

            var shuffledElements = shuffleElementsWeighted(targetPool.elements(), random);

            var candidateTried = 0;
            var candidateFailed = 0;
            for (var candidateEntry : shuffledElements) {
                var candidateElement = candidateEntry.element();
                if (candidateElement instanceof EmptyPoolElement) {
                    if (isHousePool) {
                        LOGGER.info("    Hit empty pool element for houses, stopping");
                    }
                    break;
                }

                if (candidateElement instanceof FeaturePoolElement featureElement) {
                    candidateTried++;
                    var placedFeature = this.tryPlaceFeature(featureElement, connectionPos);
                    if (placedFeature != null) {
                        this.placedFeatures.add(placedFeature);
                        if (isHousePool) {
                            LOGGER.info("    Successfully placed HOUSE feature: {}", featureElement.feature());
                        }
                        break;
                    }
                    candidateFailed++;
                    continue;
                }

                var legacyElements = extractLegacyElements(candidateElement);
                if (legacyElements.isEmpty()) {
                    if (isHousePool) {
                        LOGGER.info("    Skipping non-legacy element for houses: {}",
                                candidateEntry.element().getClass().getSimpleName());
                    }
                    continue;
                }

                var firstElement = legacyElements.getFirst();
                candidateTried++;
                var candidateTemplate = this.context.structureLoader().getTemplate(firstElement.location());
                if (candidateTemplate == null) {
                    LOGGER.warn("    Template not found: {}", firstElement.location());
                    continue;
                }

                var firstTerrainMatching = "terrain_matching".equals(firstElement.projection());
                var placed = this.tryPlaceCandidate(
                        parentPiece,
                        jigsaw,
                        connectionPos,
                        candidateTemplate,
                        firstElement.location(),
                        firstElement.processors(),
                        firstTerrainMatching,
                        parentPiece.depth + 1,
                        random,
                        isHousePool);

                if (placed != null) {
                    if (isHousePool) {
                        LOGGER.info("    Successfully placed HOUSE: {}", firstElement.location());
                    } else {
                        LOGGER.debug("    Successfully placed: {}", firstElement.location());
                    }
                    children.add(placed);

                    for (var elementIndex = 1; elementIndex < legacyElements.size(); elementIndex++) {
                        var additionalElement = legacyElements.get(elementIndex);
                        var additionalTemplate = this.context.structureLoader()
                                .getTemplate(additionalElement.location());
                        if (additionalTemplate != null) {
                            var additionalTerrainMatching = "terrain_matching".equals(additionalElement.projection());
                            var additionalProjectionOffset = this.resolveProjectionOffset(additionalTerrainMatching);
                            var additionalPiece = new PlacedPiece(
                                    additionalTemplate,
                                    additionalElement.location(),
                                    placed.origin,
                                    placed.rotation,
                                    placed.bounds,
                                    additionalElement.processors(),
                                    placed.depth,
                                    additionalTerrainMatching,
                                    additionalProjectionOffset);
                            this.placedPieces.add(additionalPiece);
                        }
                    }
                    break;
                } else {
                    candidateFailed++;
                }
            }

            if (isHousePool && candidateTried > 0) {
                LOGGER.info("    Tried {} house candidates, {} failed", candidateTried, candidateFailed);
            } else if (candidateTried > 0) {
                LOGGER.debug("    Tried {} candidates", candidateTried);
            }
        }

        return children;
    }

    private PlacedFeature tryPlaceFeature(FeaturePoolElement element, BlockVec position) {
        var bounds = new BoundingBox(position);
        if (this.maxDistanceFromCenter > 0 && !this.isWithinMaxDistance(bounds)) {
            return null;
        }

        if (this.intersectsAnyPlaced(bounds, false)) {
            return null;
        }

        return new PlacedFeature(element.feature(), position, bounds);
    }

    private PlacedPiece tryPlaceCandidate(
            PlacedPiece parentPiece,
            JigsawBlockInfo parentJigsaw,
            BlockVec connectionPos,
            StructureTemplate candidateTemplate,
            Key candidateKey,
            StructureProcessorList processors,
            boolean terrainMatching,
            int depth,
            RandomSource random,
            boolean isHousePool) {
        var rotations = Rotation.getShuffled(random);
        var totalJigsawsTried = 0;
        var attachFailed = 0;
        var collisionFailed = 0;

        for (var rotation : rotations) {
            var candidateJigsaws = candidateTemplate.getJigsaws(BlockVec.ZERO, rotation);

            if (isHousePool) {
                LOGGER.info("      House template rotation {}: {} jigsaws found", rotation, candidateJigsaws.size());
                for (var cj : candidateJigsaws) {
                    LOGGER.info("        - name={}, front={}, top={}", cj.name(), cj.front(), cj.top());
                }
            }

            for (var candidateJigsaw : candidateJigsaws) {
                totalJigsawsTried++;
                if (!parentJigsaw.canAttach(candidateJigsaw)) {
                    attachFailed++;
                    if (isHousePool) {
                        LOGGER.info(
                                "      House canAttach failed: parent(target={}, front={}, top={}, joint={}) vs candidate(name={}, front={}, top={})",
                                parentJigsaw.target(), parentJigsaw.front(), parentJigsaw.top(),
                                parentJigsaw.jointType(),
                                candidateJigsaw.name(), candidateJigsaw.front(), candidateJigsaw.top());
                    }
                    continue;
                }

                var candidateLocalPos = candidateJigsaw.position();
                var originX = connectionPos.blockX() - candidateLocalPos.blockX();
                var originZ = connectionPos.blockZ() - candidateLocalPos.blockZ();
                var projectionOffset = this.resolveProjectionOffset(terrainMatching);

                var parentMinY = parentPiece.bounds.minY();
                var candidateBoundsAtOrigin = candidateTemplate.getBoundingBox(BlockVec.ZERO, rotation);
                var candidateMinY = candidateBoundsAtOrigin.minY();
                var parentJigsawPos = parentJigsaw.position();
                var k = parentJigsawPos.blockY() - parentMinY;
                var p = candidateLocalPos.blockY();
                var q = k - p + parentJigsaw.front().normalY();

                int r;
                if (!parentPiece.terrainMatching() && !terrainMatching) {
                    r = parentMinY + q;
                } else {
                    var surfaceY = this.getSurfaceHeight(parentJigsawPos.blockX(), parentJigsawPos.blockZ());
                    r = surfaceY - p;
                }

                var originY = r - candidateMinY;
                var origin = new BlockVec(originX, originY, originZ);

                var bounds = candidateTemplate.getBoundingBox(origin, rotation);
                if (this.maxDistanceFromCenter > 0 && !this.isWithinMaxDistance(bounds)) {
                    continue;
                }

                var isStreetPiece = isStreetPiece(candidateKey);
                if (this.intersectsAnyPlaced(bounds, isStreetPiece)) {
                    collisionFailed++;
                    if (isHousePool) {
                        LOGGER.info("      House collision at {}, bounds={}", origin, bounds);
                    }
                    continue;
                }

                var placedPiece = new PlacedPiece(
                        candidateTemplate,
                        candidateKey,
                        origin,
                        rotation,
                        bounds,
                        processors,
                        depth,
                        terrainMatching,
                        projectionOffset);
                this.placedPieces.add(placedPiece);

                return placedPiece;
            }
        }

        if (isHousePool) {
            if (totalJigsawsTried == 0) {
                LOGGER.warn("      House placement failed: NO jigsaws found in any rotation!");
            } else {
                LOGGER.info("      House placement failed: tried {} jigsaws, {} attach failed, {} collision failed",
                        totalJigsawsTried, attachFailed, collisionFailed);
            }
        }

        return null;
    }

    private int resolveProjectionOffset(boolean terrainMatching) {
        return terrainMatching ? TERRAIN_MATCHING_OFFSET : 0;
    }

    private int getSurfaceHeight(int worldX, int worldZ) {
        var key = (((long) worldX) << 32) ^ (worldZ & 0xffffffffL);
        var cached = this.surfaceHeightCache.get(key);
        if (cached != null) {
            return cached;
        }

        var minY = this.context.settings().minY();
        var maxY = this.context.settings().maxYInclusive();
        var surfaceY = Integer.MIN_VALUE;
        for (var blockY = maxY; blockY >= minY; blockY--) {
            this.densityContext.setBlock(worldX, blockY, worldZ);
            var density = this.densityFunction.compute(this.densityContext);
            if (density > 0.0D) {
                surfaceY = blockY;
                break;
            }
        }

        if (surfaceY == Integer.MIN_VALUE) {
            surfaceY = this.context.settings().seaLevel();
        }

        this.surfaceHeightCache.put(key, surfaceY);
        return surfaceY;
    }

    private boolean intersectsAnyPlaced(BoundingBox bounds, boolean isStreetPiece) {
        var adjustedBounds = shrinkBounds(bounds, 1);
        for (var placed : this.placedPieces) {
            if (isStreetPiece && placed.isStreetPiece()) {
                continue;
            }
            if (placed.shrunkenBounds().intersects(adjustedBounds)) {
                return true;
            }
        }
        return false;
    }

    private boolean isWithinMaxDistance(BoundingBox bounds) {
        var center = bounds.getCenter();
        var origin = this.startCenter == null ? this.context.start() : this.startCenter;
        var deltaX = center.blockX() - origin.blockX();
        var deltaZ = center.blockZ() - origin.blockZ();
        var distanceSquared = (long) deltaX * (long) deltaX + (long) deltaZ * (long) deltaZ;
        var maxDistance = this.maxDistanceFromCenter;
        return distanceSquared <= (long) maxDistance * (long) maxDistance;
    }

    private static BoundingBox shrinkBounds(BoundingBox bounds, int amount) {
        if (amount <= 0) {
            return bounds;
        }

        var minX = bounds.minX() + amount;
        var minY = bounds.minY() + amount;
        var minZ = bounds.minZ() + amount;
        var maxX = bounds.maxX() - amount;
        var maxY = bounds.maxY() - amount;
        var maxZ = bounds.maxZ() - amount;
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return bounds;
        }

        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isStreetPiece(Key key) {
        if (key == null) {
            return false;
        }
        var value = key.value();
        return value.contains("/streets/") || value.contains("/terminators/");
    }

    private void placePiece(PlacedPiece piece) {
        piece.template.place(
                this.context.level(),
                piece.origin,
                piece.rotation,
                piece.processors,
                this.context.randomFactory(),
                this.context.structureLoader().blockTags());
    }

    private void placeFeature(PlacedFeature placedFeature) {
        placeFeature(this.context, placedFeature);
    }

    public static void placeFeature(StructurePlaceContext context, PlacedFeature placedFeature) {
        var placedFeatureDefinition = context.featureLoader().getPlacedFeature(placedFeature.feature());
        if (placedFeatureDefinition == null) {
            LOGGER.warn("Placed feature not found: {}", placedFeature.feature());
            return;
        }

        var configuredFeature = context.featureLoader().getConfiguredFeature(placedFeatureDefinition.feature());
        if (configuredFeature == null) {
            LOGGER.warn("Configured feature not found: {}", placedFeatureDefinition.feature());
            return;
        }

        var position = placedFeature.position();
        var random = context.randomFactory().at(position.blockX(), position.blockY(), position.blockZ());
        var featureContext = new FeaturePlaceContext<>(
                context.level(),
                random,
                position,
                configuredFeature.config(),
                context.settings().randomState().seed(),
                context.settings().minY(),
                context.settings().maxYInclusive());

        var featureImpl = configuredFeature.feature();
        if (featureImpl instanceof RandomSelectorFeature randomSelector) {
            randomSelector.place(featureContext, context.featureLoader());
        } else {
            ((Feature) featureImpl).place(featureContext);
        }
    }

    private static List<TemplatePool.PoolElementEntry> shuffleElementsWeighted(
            List<TemplatePool.PoolElementEntry> elements, RandomSource random) {
        var weightedEntries = new ArrayList<WeightedEntry>(elements.size());
        for (var entry : elements) {
            if (entry.weight() <= 0) {
                continue;
            }
            var randomValue = random.nextDouble();
            if (randomValue <= 0.0D) {
                randomValue = Double.MIN_VALUE;
            }
            var score = -Math.log(randomValue) / (double) entry.weight();
            weightedEntries.add(new WeightedEntry(entry, score));
        }

        weightedEntries.sort(Comparator.comparingDouble(WeightedEntry::score));

        var shuffled = new ArrayList<TemplatePool.PoolElementEntry>(weightedEntries.size());
        for (var entry : weightedEntries) {
            shuffled.add(entry.entry());
        }
        return shuffled;
    }

    private static List<LegacySinglePoolElement> extractLegacyElements(PoolElement element) {
        if (element instanceof LegacySinglePoolElement legacy) {
            return List.of(legacy);
        }
        if (element instanceof ListPoolElement) {
            var result = new ArrayList<LegacySinglePoolElement>();
            extractLegacyElementsHelper(element, result);
            return result;
        }
        return List.of();
    }

    private static void extractLegacyElementsHelper(PoolElement element, List<LegacySinglePoolElement> collector) {
        if (element instanceof LegacySinglePoolElement legacy) {
            collector.add(legacy);
        } else if (element instanceof ListPoolElement list) {
            for (var child : list.elements()) {
                extractLegacyElementsHelper(child, collector);
            }
        }
    }

    public record AssemblyResult(List<PlacedPiece> pieces, List<PlacedFeature> features) {
        private static final AssemblyResult EMPTY = new AssemblyResult(List.of(), List.of());

        public static AssemblyResult empty() {
            return EMPTY;
        }
    }

    private record WeightedEntry(TemplatePool.PoolElementEntry entry, double score) {
    }

    public record PlacedFeature(Key feature, BlockVec position, BoundingBox bounds) {
    }

    public record PlacedPiece(
            StructureTemplate template,
            Key templateKey,
            BlockVec origin,
            Rotation rotation,
            BoundingBox bounds,
            BoundingBox shrunkenBounds,
            StructureProcessorList processors,
            int depth,
            boolean terrainMatching,
            int projectionOffset) {

        public PlacedPiece(
                StructureTemplate template,
                Key templateKey,
                BlockVec origin,
                Rotation rotation,
                BoundingBox bounds,
                StructureProcessorList processors,
                int depth,
                boolean terrainMatching,
                int projectionOffset) {
            this(template, templateKey, origin, rotation, bounds, JigsawAssembler.shrinkBounds(bounds, 1), processors,
                    depth, terrainMatching, projectionOffset);
        }

        private boolean isStreetPiece() {
            return JigsawAssembler.isStreetPiece(this.templateKey);
        }
    }
}
