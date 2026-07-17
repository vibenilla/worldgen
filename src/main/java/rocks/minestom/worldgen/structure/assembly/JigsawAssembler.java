package rocks.minestom.worldgen.structure.assembly;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.worldgen.NoiseGeneratorSettingsRuntime;
import rocks.minestom.worldgen.density.ChunkContext;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.structure.JigsawStructure;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.pool.*;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.structure.template.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Method-by-method port of vanilla's {@code JigsawPlacement}: one sequential
 * {@code WorldgenRandom} (large-feature seeded at the start chunk) threads
 * through start-height sampling, the start rotation, every weighted pool
 * shuffle, every jigsaw-block shuffle and every rotation shuffle. Free space
 * is tracked with exact box-region algebra standing in for vanilla's
 * {@code VoxelShape} joins, and expansion order follows the 26.2
 * placement/selection priorities.
 */
public final class JigsawAssembler {
    private static final Logger LOGGER = LoggerFactory.getLogger(JigsawAssembler.class);
    private static final Key EMPTY_POOL = Key.key("minecraft:empty");

    private final StructureLoader loader;
    private final NoiseGeneratorSettingsRuntime settings;
    private final ChunkContext densityContext;
    private final Map<Long, Integer> firstFreeHeightCache;

    public JigsawAssembler(StructureLoader loader, NoiseGeneratorSettingsRuntime settings) {
        this.loader = loader;
        this.settings = settings;
        this.densityContext = new ChunkContext(settings.cellWidth(), settings.cellHeight());
        this.firstFreeHeightCache = new HashMap<>();
    }

    /**
     * A piece scheduled for placement. The element decides how it places
     * (template with processors, list of templates, or a feature). Junctions
     * accumulate during assembly (vanilla
     * {@code PoolElementStructurePiece.addJunction}) and feed the beardifier.
     */
    public record PlacedPiece(
            PoolElement element,
            BlockVec position,
            Rotation rotation,
            BoundingBox bounds,
            int groundLevelDelta,
            LiquidSettings liquidSettings,
            List<JigsawJunction> junctions
    ) {
        public PlacedPiece(PoolElement element, BlockVec position, Rotation rotation, BoundingBox bounds,
                int groundLevelDelta, LiquidSettings liquidSettings) {
            this(element, position, rotation, bounds, groundLevelDelta, liquidSettings, new ArrayList<>());
        }
    }

    /**
     * Vanilla {@code JigsawJunction}: where two pieces attach, with the ground
     * height the connection expects. The beardifier grows a support beard
     * around junctions of terrain-adapting structures.
     */
    public record JigsawJunction(
            int sourceX,
            int sourceGroundY,
            int sourceZ,
            int deltaY,
            Projection destProjection
    ) {
    }

    /** Vanilla {@code JigsawPlacement.addPieces}. */
    public List<PlacedPiece> assemble(JigsawStructure config, int chunkX, int chunkZ) {
        var random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(this.settings.randomState().seed(), chunkX, chunkZ);

        var height = config.startHeight().sample(random);
        var position = new BlockVec(chunkX << 4, height, chunkZ << 4);
        var aliasLookup = PoolAliasLookup.create(config.poolAliases(), position, this.settings.randomState().seed());

        var centerRotation = Rotation.getRandom(random);
        var centerPool = this.loader.getTemplatePool(aliasLookup.lookup(config.startPool()));
        if (centerPool == null) {
            centerPool = this.loader.getTemplatePool(config.startPool());
        }
        if (centerPool == null) {
            LOGGER.warn("Start pool not found: {}", config.startPool());
            return List.of();
        }

        var centerElement = centerPool.getRandomTemplate(random);
        if (centerElement == EmptyPoolElement.INSTANCE) {
            return List.of();
        }

        BlockVec anchoredPosition;
        if (config.startJigsawName() != null) {
            anchoredPosition = getRandomNamedJigsaw(centerElement, config.startJigsawName(), position, centerRotation,
                    random);
            if (anchoredPosition == null) {
                LOGGER.warn("No starting jigsaw {} found in start pool {}", config.startJigsawName(),
                        config.startPool());
                return List.of();
            }
        } else {
            anchoredPosition = position;
        }

        var localAnchorX = anchoredPosition.blockX() - position.blockX();
        var localAnchorY = anchoredPosition.blockY() - position.blockY();
        var localAnchorZ = anchoredPosition.blockZ() - position.blockZ();
        var adjustedPosition = new BlockVec(
                position.blockX() - localAnchorX,
                position.blockY() - localAnchorY,
                position.blockZ() - localAnchorZ);

        var groundLevelDelta = centerElement.groundLevelDelta();
        var box = centerElement.getBoundingBox(this.loader, adjustedPosition, centerRotation);
        var centerX = (box.maxX() + box.minX()) / 2;
        var centerZ = (box.maxZ() + box.minZ()) / 2;
        int bottomY;
        if (config.projectToHeightmap()) {
            bottomY = position.blockY() + this.firstFreeHeight(centerX, centerZ);
        } else {
            bottomY = adjustedPosition.blockY();
        }

        var yShift = bottomY - (box.minY() + groundLevelDelta);
        box = box.moved(0, yShift, 0);
        var centerPosition = new BlockVec(
                adjustedPosition.blockX(),
                adjustedPosition.blockY() + yShift,
                adjustedPosition.blockZ());

        if (this.isStartTooCloseToWorldHeightLimits(config, box)) {
            LOGGER.debug("Center piece with bounding box {} does not fit dimension padding", box);
            return List.of();
        }

        var centerY = bottomY + localAnchorY;
        var centerPiece = new PlacedPiece(centerElement, centerPosition, centerRotation, box, groundLevelDelta,
                config.liquidSettings());
        var pieces = new ArrayList<PlacedPiece>();
        pieces.add(centerPiece);

        if (config.size() <= 0) {
            return pieces;
        }

        var minY = this.settings.minY();
        var maxY = this.settings.maxYInclusive();
        var maxDistance = config.maxDistanceFromCenter();
        var aabb = new double[]{
                centerX - maxDistance,
                Math.max(centerY - maxDistance, minY + config.dimensionPaddingBottom()),
                centerZ - maxDistance,
                centerX + maxDistance + 1,
                Math.min(centerY + maxDistance + 1, maxY + 1 - config.dimensionPaddingTop()),
                centerZ + maxDistance + 1
        };
        var free = VoxelRegion.of(aabb);
        free.subtract(VoxelRegion.aabbOf(box));

        var placer = new Placer(this, config, aliasLookup, random, pieces);
        placer.tryPlacingChildren(centerPiece, new ShapeRef(free), 0);
        while (placer.placing.hasNext()) {
            var state = placer.placing.next();
            placer.tryPlacingChildren(state.piece(), state.free(), state.depth());
        }

        return pieces;
    }

    private boolean isStartTooCloseToWorldHeightLimits(JigsawStructure config, BoundingBox centerBounds) {
        if (config.dimensionPaddingBottom() == 0 && config.dimensionPaddingTop() == 0) {
            return false;
        }

        var minYWithPadding = this.settings.minY() + config.dimensionPaddingBottom();
        var maxYWithPadding = this.settings.maxYInclusive() - config.dimensionPaddingTop();
        return centerBounds.minY() < minYWithPadding || centerBounds.maxY() > maxYWithPadding;
    }

    private BlockVec getRandomNamedJigsaw(PoolElement element, Key startJigsawName, BlockVec position,
            Rotation rotation, RandomSource random) {
        var name = startJigsawName.asString();
        for (var jigsaw : element.getShuffledJigsawBlocks(this.loader, position, rotation, random)) {
            if (name.equals(jigsaw.name())) {
                return jigsaw.position();
            }
        }
        return null;
    }

    /**
     * Vanilla's {@code getFirstFreeHeight} with WORLD_SURFACE_WG semantics:
     * one above the highest solid noise block, never below sea level (vanilla
     * columns fill with water up to the sea).
     */
    int firstFreeHeight(int x, int z) {
        var key = (((long) x) << 32) ^ (z & 0xffffffffL);
        var cached = this.firstFreeHeightCache.get(key);
        if (cached != null) {
            return cached;
        }

        var minY = this.settings.minY();
        var maxY = this.settings.maxYInclusive();
        var density = this.settings.finalDensity();
        var surfaceY = Integer.MIN_VALUE;
        for (var blockY = maxY; blockY >= minY; blockY--) {
            this.densityContext.setBlock(x, blockY, z);
            if (density.compute(this.densityContext) > 0.0D) {
                surfaceY = blockY;
                break;
            }
        }

        var seaLevel = this.settings.seaLevel();
        var result = surfaceY == Integer.MIN_VALUE ? seaLevel : Math.max(surfaceY + 1, seaLevel);
        this.firstFreeHeightCache.put(key, result);
        return result;
    }

    private record PieceState(PlacedPiece piece, ShapeRef free, int depth) {
    }

    /** Vanilla's {@code MutableObject<VoxelShape>}. */
    private static final class ShapeRef {
        private VoxelRegion value;

        private ShapeRef(VoxelRegion value) {
            this.value = value;
        }
    }

    /** Vanilla {@code JigsawPlacement.Placer}. */
    private static final class Placer {
        private final JigsawAssembler assembler;
        private final JigsawStructure config;
        private final PoolAliasLookup aliasLookup;
        private final RandomSource random;
        private final List<PlacedPiece> pieces;
        private final SequencedPriorityIterator<PieceState> placing = new SequencedPriorityIterator<>();

        private Placer(JigsawAssembler assembler, JigsawStructure config, PoolAliasLookup aliasLookup,
                RandomSource random, List<PlacedPiece> pieces) {
            this.assembler = assembler;
            this.config = config;
            this.aliasLookup = aliasLookup;
            this.random = random;
            this.pieces = pieces;
        }

        private void tryPlacingChildren(PlacedPiece sourcePiece, ShapeRef contextFree, int depth) {
            var loader = this.assembler.loader;
            var sourceElement = sourcePiece.element();
            var sourcePosition = sourcePiece.position();
            var sourceRotation = sourcePiece.rotation();
            var sourceProjection = sourceElement.projection();
            var sourceRigid = sourceProjection == Projection.RIGID;
            var sourceFree = new ShapeRef(null);
            var sourceBounds = sourcePiece.bounds();
            var sourceBoxY = sourceBounds.minY();

            sourceJigsaws:
            for (var sourceJigsaw : sourceElement.getShuffledJigsawBlocks(loader, sourcePosition, sourceRotation,
                    this.random)) {
                var sourceDirection = sourceJigsaw.front();
                var sourceJigsawPos = sourceJigsaw.position();
                var targetJigsawPos = new BlockVec(
                        sourceJigsawPos.blockX() + sourceDirection.normalX(),
                        sourceJigsawPos.blockY() + sourceDirection.normalY(),
                        sourceJigsawPos.blockZ() + sourceDirection.normalZ());
                var sourceJigsawLocalY = sourceJigsawPos.blockY() - sourceBoxY;
                var sourceJigsawBaseHeight = Integer.MIN_VALUE;
                var poolName = this.aliasLookup.lookup(sourceJigsaw.pool());
                var targetPool = loader.getTemplatePool(poolName);
                if (targetPool == null) {
                    LOGGER.warn("Empty or non-existent pool: {}", poolName);
                    continue;
                }

                if (targetPool.size() == 0 && !poolName.equals(EMPTY_POOL)) {
                    LOGGER.warn("Empty or non-existent pool: {}", poolName);
                    continue;
                }

                var fallbackName = targetPool.fallback();
                var fallback = loader.getTemplatePool(fallbackName);
                if (fallback == null || (fallback.size() == 0 && !fallbackName.equals(EMPTY_POOL))) {
                    LOGGER.warn("Empty or non-existent fallback pool: {}", fallbackName);
                    continue;
                }

                var attachInsideSource = sourceBounds.isInside(targetJigsawPos);
                ShapeRef childrenFree;
                if (attachInsideSource) {
                    childrenFree = sourceFree;
                    if (sourceFree.value == null) {
                        sourceFree.value = VoxelRegion.of(VoxelRegion.aabbOf(sourceBounds));
                    }
                } else {
                    childrenFree = contextFree;
                }

                var targetPieces = new ArrayList<PoolElement>();
                if (depth != this.config.size()) {
                    targetPieces.addAll(targetPool.getShuffledTemplates(this.random));
                }
                targetPieces.addAll(fallback.getShuffledTemplates(this.random));
                var placementPriority = sourceJigsaw.placementPriority();

                for (var targetElement : targetPieces) {
                    if (targetElement == EmptyPoolElement.INSTANCE) {
                        break;
                    }

                    for (var targetRotation : Rotation.getShuffled(this.random)) {
                        var targetJigsaws = targetElement.getShuffledJigsawBlocks(loader, BlockVec.ZERO,
                                targetRotation, this.random);
                        var hackBox = targetElement.getBoundingBox(loader, BlockVec.ZERO, targetRotation);
                        int expandTo;
                        if (this.config.useExpansionHack() && hackBox.getYSpan() <= 16) {
                            expandTo = 0;
                            for (var targetJigsaw : targetJigsaws) {
                                var front = targetJigsaw.front();
                                var frontPos = new BlockVec(
                                        targetJigsaw.position().blockX() + front.normalX(),
                                        targetJigsaw.position().blockY() + front.normalY(),
                                        targetJigsaw.position().blockZ() + front.normalZ());
                                if (!hackBox.isInside(frontPos)) {
                                    continue;
                                }

                                var childPoolName = this.aliasLookup.lookup(targetJigsaw.pool());
                                var childPool = loader.getTemplatePool(childPoolName);
                                var childPoolSize = childPool != null ? childPool.getMaxSize(loader) : 0;
                                var childFallback = childPool != null
                                        ? loader.getTemplatePool(childPool.fallback())
                                        : null;
                                var childFallbackSize = childFallback != null ? childFallback.getMaxSize(loader) : 0;
                                expandTo = Math.max(expandTo, Math.max(childPoolSize, childFallbackSize));
                            }
                        } else {
                            expandTo = 0;
                        }

                        for (var targetJigsaw : targetJigsaws) {
                            if (!sourceJigsaw.canAttach(targetJigsaw)) {
                                continue;
                            }

                            var targetJigsawLocalPos = targetJigsaw.position();
                            var rawTargetBoxPos = new BlockVec(
                                    targetJigsawPos.blockX() - targetJigsawLocalPos.blockX(),
                                    targetJigsawPos.blockY() - targetJigsawLocalPos.blockY(),
                                    targetJigsawPos.blockZ() - targetJigsawLocalPos.blockZ());
                            var rawTargetBounds = targetElement.getBoundingBox(loader, rawTargetBoxPos,
                                    targetRotation);
                            var rawTargetY = rawTargetBounds.minY();
                            var targetProjection = targetElement.projection();
                            var targetRigid = targetProjection == Projection.RIGID;
                            var targetJigsawLocalY = targetJigsawLocalPos.blockY();
                            var deltaY = sourceJigsawLocalY - targetJigsawLocalY + sourceDirection.normalY();
                            int targetBoxY;
                            if (sourceRigid && targetRigid) {
                                targetBoxY = sourceBoxY + deltaY;
                            } else {
                                if (sourceJigsawBaseHeight == Integer.MIN_VALUE) {
                                    sourceJigsawBaseHeight = this.assembler.firstFreeHeight(
                                            sourceJigsawPos.blockX(), sourceJigsawPos.blockZ());
                                }

                                targetBoxY = sourceJigsawBaseHeight - targetJigsawLocalY;
                            }

                            var yOffset = targetBoxY - rawTargetY;
                            var targetBounds = rawTargetBounds.moved(0, yOffset, 0);
                            var targetBoxPosition = new BlockVec(
                                    rawTargetBoxPos.blockX(),
                                    rawTargetBoxPos.blockY() + yOffset,
                                    rawTargetBoxPos.blockZ());
                            if (expandTo > 0) {
                                var newSize = Math.max(expandTo + 1, targetBounds.maxY() - targetBounds.minY());
                                targetBounds.encapsulate(new BlockVec(
                                        targetBounds.minX(), targetBounds.minY() + newSize, targetBounds.minZ()));
                            }

                            var targetAabb = VoxelRegion.aabbOf(targetBounds);
                            if (!childrenFree.value.contains(VoxelRegion.deflate(targetAabb, 0.25))) {
                                continue;
                            }

                            childrenFree.value.subtract(targetAabb);
                            var sourceGroundLevelDelta = sourcePiece.groundLevelDelta();
                            int targetGroundLevelDelta;
                            if (targetRigid) {
                                targetGroundLevelDelta = sourceGroundLevelDelta - deltaY;
                            } else {
                                targetGroundLevelDelta = targetElement.groundLevelDelta();
                            }

                            var targetPiece = new PlacedPiece(targetElement, targetBoxPosition, targetRotation,
                                    targetBounds, targetGroundLevelDelta, this.config.liquidSettings());

                            // Vanilla records the junction on both pieces so the
                            // beardifier can grow support terrain at attachments.
                            int junctionY;
                            if (sourceRigid) {
                                junctionY = sourceBoxY + sourceJigsawLocalY;
                            } else if (targetRigid) {
                                junctionY = targetBoxY + targetJigsawLocalY;
                            } else {
                                if (sourceJigsawBaseHeight == Integer.MIN_VALUE) {
                                    sourceJigsawBaseHeight = this.assembler.firstFreeHeight(
                                            sourceJigsawPos.blockX(), sourceJigsawPos.blockZ());
                                }

                                junctionY = sourceJigsawBaseHeight + deltaY / 2;
                            }

                            sourcePiece.junctions().add(new JigsawJunction(
                                    targetJigsawPos.blockX(),
                                    junctionY - sourceJigsawLocalY + sourceGroundLevelDelta,
                                    targetJigsawPos.blockZ(),
                                    deltaY,
                                    targetProjection));
                            targetPiece.junctions().add(new JigsawJunction(
                                    sourceJigsawPos.blockX(),
                                    junctionY - targetJigsawLocalY + targetGroundLevelDelta,
                                    sourceJigsawPos.blockZ(),
                                    -deltaY,
                                    sourceProjection));

                            this.pieces.add(targetPiece);
                            if (depth + 1 <= this.config.size()) {
                                this.placing.add(new PieceState(targetPiece, childrenFree, depth + 1),
                                        placementPriority);
                            }
                            continue sourceJigsaws;
                        }
                    }
                }
            }
        }
    }
}
