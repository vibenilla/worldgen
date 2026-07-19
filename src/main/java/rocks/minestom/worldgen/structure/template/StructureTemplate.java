package rocks.minestom.worldgen.structure.template;

import net.kyori.adventure.nbt.*;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.LegacyRandomSource;
import rocks.minestom.worldgen.structure.StructureRng;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A structure template loaded from an NBT file, ported from vanilla's
 * {@code StructureTemplate}.
 *
 * <p>Vanilla-parity notes:
 * <ul>
 * <li>Blocks are stored per palette in vanilla's category order (full blocks,
 * then partial blocks, then block entities; each sorted by Y, X, Z). Capped
 * processors shuffle indices over this order, so it matters.
 * <li>Jigsaw blocks all carry NBT, so their order is the block-entity segment
 * order: sorted by (Y, X, Z) of the template-local position.
 * <li>Multi-palette templates pick a palette with a fresh legacy random seeded
 * from the piece position hash ({@code StructurePlaceSettings.getRandomPalette}).
 * <li>Processors observe unrotated template states; the piece rotation is
 * applied when the surviving block is placed.
 * </ul>
 *
 * @see StructureLoader for loading templates from data packs
 */
public final class StructureTemplate {
    private static final String TAG_SIZE = "size";
    private static final String TAG_PALETTE = "palette";
    private static final String TAG_PALETTES = "palettes";
    private static final String TAG_BLOCKS = "blocks";
    private static final String TAG_POS = "pos";
    private static final String TAG_STATE = "state";
    private static final String TAG_NBT = "nbt";
    private static final String TAG_NAME = "Name";
    private static final String TAG_PROPERTIES = "Properties";

    private final BlockVec size;
    private final List<Palette> palettes;

    private StructureTemplate(BlockVec size, List<Palette> palettes) {
        this.size = size;
        this.palettes = palettes;
    }

    public BlockVec size() {
        return this.size;
    }

    /** Blocks of the first palette, in vanilla iteration order. */
    public List<StructureBlock> blocks() {
        return this.palettes.getFirst().blocks();
    }

    /**
     * Jigsaw blocks in world coordinates for a piece at {@code origin} with
     * the given rotation. Returns a fresh mutable list (callers shuffle it).
     */
    public List<JigsawBlockInfo> getJigsaws(BlockVec origin, Rotation rotation) {
        var palette = this.palettes.get(this.paletteIndex(origin));
        var jigsaws = palette.jigsaws();
        var result = new ArrayList<JigsawBlockInfo>(jigsaws.size());
        for (var jigsaw : jigsaws) {
            var rotatedLocalPos = rotation.rotate(jigsaw.position(), this.size);
            var worldPos = new BlockVec(
                    origin.blockX() + rotatedLocalPos.blockX(),
                    origin.blockY() + rotatedLocalPos.blockY(),
                    origin.blockZ() + rotatedLocalPos.blockZ());
            result.add(jigsaw.withRotation(rotation, worldPos));
        }
        return result;
    }

    /** A {@code structure_block} DATA-mode entry: its world position and metadata string. */
    public record DataMarker(BlockVec position, String metadata) {
    }

    /**
     * The DATA-mode structure blocks of the palette a piece at {@code origin}
     * uses, in block-entity segment order, with positions transformed like
     * placement. Vanilla {@code TemplateStructurePiece.postProcess} feeds
     * these to {@code handleDataMarker} after placing the piece.
     */
    public List<DataMarker> dataMarkers(BlockVec origin, Rotation rotation, Mirror mirror) {
        var palette = this.palettes.get(this.paletteIndex(origin));
        var result = new ArrayList<DataMarker>();
        for (var entry : palette.blocks()) {
            if (!entry.block().key().value().equals("structure_block") || entry.nbt() == null) {
                continue;
            }
            if (!entry.nbt().getString("mode").equals("DATA")) {
                continue;
            }
            var rotatedPos = rotation.rotate(mirror.mirror(entry.position()), this.size);
            result.add(new DataMarker(new BlockVec(
                    origin.blockX() + rotatedPos.blockX(),
                    origin.blockY() + rotatedPos.blockY(),
                    origin.blockZ() + rotatedPos.blockZ()),
                    entry.nbt().getString("metadata")));
        }
        return result;
    }

    public BoundingBox getBoundingBox(BlockVec origin, Rotation rotation) {
        return this.getBoundingBox(origin, rotation, Mirror.NONE);
    }

    public BoundingBox getBoundingBox(BlockVec origin, Rotation rotation, Mirror mirror) {
        var maxCorner = new BlockVec(
                this.size.blockX() - 1,
                this.size.blockY() - 1,
                this.size.blockZ() - 1);
        var corner1 = rotation.rotate(mirror.mirror(BlockVec.ZERO), this.size);
        var corner2 = rotation.rotate(mirror.mirror(maxCorner), this.size);
        return BoundingBox.fromCorners(corner1, corner2).moved(origin.blockX(), origin.blockY(), origin.blockZ());
    }

    /**
     * Vanilla {@code StructureTemplate.getZeroPositionWithTransform}: the
     * world offset of the template's local origin after mirroring across a
     * {@code (sizeX, sizeZ)} footprint and rotating.
     */
    public static BlockVec getZeroPositionWithTransform(BlockVec zeroPos, Mirror mirror, Rotation rotation, int sizeX, int sizeZ) {
        sizeX--;
        sizeZ--;
        var mirrorDeltaX = mirror == Mirror.FRONT_BACK ? sizeX : 0;
        var mirrorDeltaZ = mirror == Mirror.LEFT_RIGHT ? sizeZ : 0;

        return switch (rotation) {
            case COUNTERCLOCKWISE_90 -> zeroPos.add(mirrorDeltaZ, 0, sizeX - mirrorDeltaX);
            case CLOCKWISE_90 -> zeroPos.add(sizeZ - mirrorDeltaZ, 0, mirrorDeltaX);
            case CLOCKWISE_180 -> zeroPos.add(sizeX - mirrorDeltaX, 0, sizeZ - mirrorDeltaZ);
            case NONE -> zeroPos.add(mirrorDeltaX, 0, mirrorDeltaZ);
        };
    }

    /**
     * Vanilla {@code StructurePlaceSettings.getRandomPalette}: multi-palette
     * templates pick via a fresh legacy random seeded from the position hash.
     */
    private int paletteIndex(BlockVec position) {
        if (this.palettes.size() == 1) {
            return 0;
        }
        var random = new LegacyRandomSource(StructureRng.getSeed(
                position.blockX(), position.blockY(), position.blockZ()));
        return random.nextInt(this.palettes.size());
    }

    /**
     * Everything piece placement needs beyond the piece itself.
     *
     * @param level                  block reads and writes
     * @param chunkBounds            clip placed blocks (and, without whole-piece
     *                               processors, processed blocks) to this box; null for
     *                               unbounded placement
     * @param processorContext       context shared by all pieces of a start
     * @param connectionShapeUpdates positions placed with {@code updateConnectionShapes}
     *                               true accumulate here; the caller runs
     *                               {@link StructureShapeUpdater#update} once every
     *                               piece sharing this context has been placed, so
     *                               connections resolve against final neighbors
     *                               instead of each piece's placement order
     */
    public record PlacementContext(
            GenerationUnitAdapter level,
            BoundingBox chunkBounds,
            StructureProcessorContext processorContext,
            List<BlockVec> connectionShapeUpdates,
            rocks.minestom.worldgen.random.RandomSource lootRandom
    ) {
        public PlacementContext(GenerationUnitAdapter level, BoundingBox chunkBounds,
                StructureProcessorContext processorContext, List<BlockVec> connectionShapeUpdates) {
            this(level, chunkBounds, processorContext, connectionShapeUpdates, null);
        }
    }

    /**
     * Vanilla {@code StructureTemplate.placeInWorld} + {@code processBlockInfos}
     * for worldgen purposes (no entities, no block-entity loading).
     */
    public void place(
            PlacementContext context,
            BlockVec position,
            Rotation rotation,
            StructureProcessorList processors,
            boolean legacy,
            boolean terrainMatching,
            LiquidSettings liquidSettings,
            boolean updateConnectionShapes) {
        this.place(context, position, rotation, Mirror.NONE, processors, legacy, terrainMatching, liquidSettings,
                updateConnectionShapes);
    }

    public void place(
            PlacementContext context,
            BlockVec position,
            Rotation rotation,
            Mirror mirror,
            StructureProcessorList processors,
            boolean legacy,
            boolean terrainMatching,
            LiquidSettings liquidSettings,
            boolean updateConnectionShapes) {
        this.place(context, position, position, rotation, mirror, processors, legacy, terrainMatching,
                liquidSettings, updateConnectionShapes);
    }

    public void place(
            PlacementContext context,
            BlockVec position,
            BlockVec paletteSeedPosition,
            Rotation rotation,
            StructureProcessorList processors,
            boolean legacy,
            boolean terrainMatching,
            LiquidSettings liquidSettings,
            boolean updateConnectionShapes) {
        this.place(context, position, paletteSeedPosition, rotation, Mirror.NONE, processors, legacy,
                terrainMatching, liquidSettings, updateConnectionShapes);
    }

    public void place(
            PlacementContext context,
            BlockVec position,
            BlockVec paletteSeedPosition,
            Rotation rotation,
            Mirror mirror,
            StructureProcessorList processors,
            boolean legacy,
            boolean terrainMatching,
            LiquidSettings liquidSettings,
            boolean updateConnectionShapes) {
        var palette = this.palettes.get(this.paletteIndex(paletteSeedPosition));

        // Vanilla processor chain order (SinglePoolElement.getSettings /
        // LegacySinglePoolElement.getSettings).
        var chain = new ArrayList<StructureProcessor>(processors.processors().size() + 3);
        if (!legacy) {
            chain.add(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }
        chain.add(JigsawReplacementProcessor.INSTANCE);
        chain.addAll(processors.processors());
        if (terrainMatching) {
            chain.add(new GravityProcessor(-1));
        }
        if (legacy) {
            chain.add(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
        }

        var processOnlyInCurrentChunk = true;
        for (var processor : chain) {
            if (processor.evaluatesEntirePieceState()) {
                processOnlyInCurrentChunk = false;
                break;
            }
        }

        var processorContext = context.processorContext();
        var chunkBounds = context.chunkBounds();
        var originalBlockInfoList = new ArrayList<StructureBlockInfo>();
        var processedBlockInfoList = new ArrayList<StructureBlockInfo>();

        for (var blockEntry : palette.blocks()) {
            var templatePos = blockEntry.position();
            var rotatedPos = rotation.rotate(mirror.mirror(templatePos), this.size);
            var worldPos = new BlockVec(
                    position.blockX() + rotatedPos.blockX(),
                    position.blockY() + rotatedPos.blockY(),
                    position.blockZ() + rotatedPos.blockZ());
            if (processOnlyInCurrentChunk && chunkBounds != null && !chunkBounds.isInside(worldPos)) {
                continue;
            }

            var processed = new StructureBlockInfo(worldPos, blockEntry.block(), blockEntry.nbt());
            for (var processor : chain) {
                processed = processor.processBlock(processorContext, templatePos, processed);
                if (processed == null) {
                    break;
                }
            }

            if (processed != null) {
                processedBlockInfoList.add(processed);
                originalBlockInfoList.add(new StructureBlockInfo(templatePos, blockEntry.block(), blockEntry.nbt()));
            }
        }

        List<StructureBlockInfo> finalized = processedBlockInfoList;
        for (var processor : chain) {
            finalized = processor.finalizeProcessing(processorContext, originalBlockInfoList, finalized);
        }

        var applyWaterlogging = liquidSettings == LiquidSettings.APPLY_WATERLOGGING;
        var level = context.level();
        var placedPositions = new ArrayList<BlockVec>(finalized.size());
        var lockedFluids = new ArrayList<BlockVec>();
        var toFill = new ArrayList<BlockVec>();
        for (var blockInfo : finalized) {
            var blockPos = blockInfo.pos();
            if (chunkBounds != null && !chunkBounds.isInside(blockPos)) {
                continue;
            }

            var state = rotateBlockState(mirrorBlockState(blockInfo.state(), mirror), rotation);
            var blockKey = state.key().asString();
            if (blockKey.equals("minecraft:structure_void") || blockKey.equals("minecraft:jigsaw")) {
                continue;
            }

            if (applyWaterlogging) {
                if (isSourceWater(state)) {
                    lockedFluids.add(blockPos);
                } else if (state.getProperty("waterlogged") != null) {
                    if (isSourceWater(level.getBlock(blockPos.blockX(), blockPos.blockY(), blockPos.blockZ()))) {
                        if (rocks.minestom.worldgen.feature.WaterStates.canBeWaterlogged(state)) {
                            state = state.withProperty("waterlogged", "true");
                        }
                    } else {
                        toFill.add(blockPos);
                    }
                }
            }

            if (blockInfo.nbt() != null && blockInfo.nbt() != state.nbt()) {
                state = state.withNbt(blockInfo.nbt());
            }

            level.setBlock(blockPos, state);
            placedPositions.add(blockPos);

            if (context.lootRandom() != null && blockInfo.nbt() != null && isRandomizableContainer(state)) {
                // Vanilla placeInWorld seeds every placed loot container with
                // random.nextLong() ("LootTableSeed"); the draw must be
                // consumed even though loot NBT is out of scope.
                context.lootRandom().nextLong();
            }
        }

        fillFromNeighborSources(level, lockedFluids, toFill);

        // Vanilla StructureTemplate.placeInWorld tail: recompute connection
        // shapes (fences, walls, leaves) against the final placed neighbors,
        // unless the caller already knows the palette states are correct
        // (StructurePlaceSettings.knownShape, e.g. jigsaw pool elements). The
        // actual recompute is deferred to the caller, once every piece
        // sharing this context has been placed.
        if (updateConnectionShapes) {
            context.connectionShapeUpdates().addAll(placedPositions);
        }
    }


    /** The blocks whose block entity is a vanilla {@code RandomizableContainer}. */
    private static boolean isRandomizableContainer(Block state) {
        var key = state.key().value();
        return key.equals("chest") || key.equals("trapped_chest") || key.equals("barrel")
                || key.equals("dispenser") || key.equals("dropper") || key.equals("hopper")
                || key.equals("crafter") || key.equals("decorated_pot") || key.endsWith("shulker_box");
    }

    private static boolean isSourceWater(Block block) {
        var key = block.key().asString();
        if (key.equals("minecraft:water")) {
            var levelProperty = block.getProperty("level");
            return levelProperty == null || levelProperty.equals("0");
        }
        if (key.equals("minecraft:seagrass") || key.equals("minecraft:tall_seagrass")
                || key.equals("minecraft:kelp") || key.equals("minecraft:kelp_plant")
                || key.equals("minecraft:bubble_column")) {
            return true;
        }
        // Waterlogged blocks expose a source water fluid state.
        return "true".equals(block.getProperty("waterlogged"));
    }

    /**
     * The tail of vanilla {@code StructureTemplate.placeInWorld}'s liquid
     * handling: every waterloggable block placed over a non-source fluid cell
     * was recorded, and any of them adjacent (up or horizontally) to a source
     * water cell fills with it - excluding cells whose source water came from
     * the palette itself ({@code lockedFluids}) - repeating until no block
     * fills.
     */
    private static void fillFromNeighborSources(GenerationUnitAdapter level,
            List<BlockVec> lockedFluids, List<BlockVec> toFill) {
        var offsets = new int[][] {{0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
        var filled = true;
        while (filled && !toFill.isEmpty()) {
            filled = false;
            var iterator = toFill.iterator();
            while (iterator.hasNext()) {
                var position = iterator.next();
                var foundSource = false;
                for (var offset : offsets) {
                    var neighborPos = new BlockVec(
                            position.blockX() + offset[0],
                            position.blockY() + offset[1],
                            position.blockZ() + offset[2]);
                    if (lockedFluids.contains(neighborPos)) {
                        continue;
                    }
                    if (isSourceWater(level.getBlock(neighborPos.blockX(), neighborPos.blockY(), neighborPos.blockZ()))) {
                        foundSource = true;
                        break;
                    }
                }

                if (!foundSource) {
                    continue;
                }

                var current = level.getBlock(position.blockX(), position.blockY(), position.blockZ());
                if (rocks.minestom.worldgen.feature.WaterStates.canBeWaterlogged(current)) {
                    if ("false".equals(current.getProperty("waterlogged"))) {
                        level.setBlock(position, current.withProperty("waterlogged", "true"));
                    }
                    filled = true;
                    iterator.remove();
                }
            }
        }
    }

    /**
     * Vanilla {@code BlockState.mirror(Mirror)}: composed of a facing swap
     * (including the stairs {@code shape} adjustment from
     * {@code StairBlock.mirror}) and the four cardinal cross-collision
     * properties, applied only when the block's facing axis matches the
     * mirror's flip axis.
     */
    public static Block mirrorBlockState(Block block, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return block;
        }

        var facing = block.getProperty("facing");
        if (facing != null) {
            var direction = parseDirection(facing);
            if (direction == null) {
                return block;
            }
            var flips = (mirror == Mirror.LEFT_RIGHT && (direction == net.minestom.server.utils.Direction.NORTH || direction == net.minestom.server.utils.Direction.SOUTH))
                    || (mirror == Mirror.FRONT_BACK && (direction == net.minestom.server.utils.Direction.EAST || direction == net.minestom.server.utils.Direction.WEST));
            if (!flips) {
                return block;
            }

            block = block.withProperty("facing", direction.opposite().name().toLowerCase());
            var shape = block.getProperty("shape");
            if (shape != null) {
                block = block.withProperty("shape", mirrorStairsShape(shape));
            }
            return block;
        }

        return mirrorHorizontalConnections(block, mirror);
    }

    private static String mirrorStairsShape(String shape) {
        return switch (shape) {
            case "outer_left" -> "outer_right";
            case "outer_right" -> "outer_left";
            case "inner_left" -> "inner_right";
            case "inner_right" -> "inner_left";
            default -> shape;
        };
    }

    private static Block mirrorHorizontalConnections(Block block, Mirror mirror) {
        var northProperty = block.getProperty("north");
        var eastProperty = block.getProperty("east");
        var southProperty = block.getProperty("south");
        var westProperty = block.getProperty("west");
        if (northProperty == null || eastProperty == null || southProperty == null || westProperty == null) {
            return block;
        }

        return switch (mirror) {
            case LEFT_RIGHT -> block.withProperties(Map.of("north", southProperty, "south", northProperty));
            case FRONT_BACK -> block.withProperties(Map.of("east", westProperty, "west", eastProperty));
            case NONE -> block;
        };
    }

    public static Block rotateBlockState(Block block, Rotation rotation) {
        if (rotation == Rotation.NONE) {
            return block;
        }

        var facing = block.getProperty("facing");
        if (facing != null) {
            var direction = parseDirection(facing);
            if (direction != null) {
                var rotated = rotation.rotate(direction);
                block = block.withProperty("facing", rotated.name().toLowerCase());
            }
        }

        var orientation = block.getProperty("orientation");
        if (orientation != null) {
            block = rotateOrientation(block, orientation, rotation);
        }

        var rotationProperty = block.getProperty("rotation");
        if (rotationProperty != null) {
            block = rotateSixteenStep(block, rotationProperty, rotation);
        }

        var axis = block.getProperty("axis");
        if (axis != null && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
            block = switch (axis) {
                case "x" -> block.withProperty("axis", "z");
                case "z" -> block.withProperty("axis", "x");
                default -> block;
            };
        }

        block = rotateHorizontalConnections(block, rotation);

        return block;
    }

    private static Block rotateOrientation(Block block, String orientation, Rotation rotation) {
        var parts = orientation.split("_");
        if (parts.length != 2) {
            return block;
        }

        var front = rotateDirectionName(parts[0], rotation);
        var top = rotateDirectionName(parts[1], rotation);
        try {
            return block.withProperty("orientation", front + "_" + top);
        } catch (Exception exception) {
            return block;
        }
    }

    private static String rotateDirectionName(String name, Rotation rotation) {
        var direction = parseDirection(name);
        if (direction == null || direction.normalY() != 0) {
            return name;
        }
        return rotation.rotate(direction).name().toLowerCase();
    }

    /** Banners and similar 16-step "rotation" properties. */
    private static Block rotateSixteenStep(Block block, String value, Rotation rotation) {
        int steps;
        try {
            steps = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return block;
        }

        var offset = switch (rotation) {
            case CLOCKWISE_90 -> 4;
            case CLOCKWISE_180 -> 8;
            case COUNTERCLOCKWISE_90 -> 12;
            case NONE -> 0;
        };
        return block.withProperty("rotation", Integer.toString((steps + offset) & 15));
    }

    private static Block rotateHorizontalConnections(Block block, Rotation rotation) {
        var northProperty = block.getProperty("north");
        var eastProperty = block.getProperty("east");
        var southProperty = block.getProperty("south");
        var westProperty = block.getProperty("west");
        if (northProperty == null || eastProperty == null || southProperty == null || westProperty == null) {
            return block;
        }

        return switch (rotation) {
            case CLOCKWISE_90 -> block.withProperties(Map.of(
                    "north", westProperty,
                    "east", northProperty,
                    "south", eastProperty,
                    "west", southProperty));
            case CLOCKWISE_180 -> block.withProperties(Map.of(
                    "north", southProperty,
                    "east", westProperty,
                    "south", northProperty,
                    "west", eastProperty));
            case COUNTERCLOCKWISE_90 -> block.withProperties(Map.of(
                    "north", eastProperty,
                    "east", southProperty,
                    "south", westProperty,
                    "west", northProperty));
            case NONE -> block;
        };
    }

    private static net.minestom.server.utils.Direction parseDirection(String name) {
        return switch (name.toLowerCase()) {
            case "up" -> net.minestom.server.utils.Direction.UP;
            case "down" -> net.minestom.server.utils.Direction.DOWN;
            case "north" -> net.minestom.server.utils.Direction.NORTH;
            case "south" -> net.minestom.server.utils.Direction.SOUTH;
            case "east" -> net.minestom.server.utils.Direction.EAST;
            case "west" -> net.minestom.server.utils.Direction.WEST;
            default -> null;
        };
    }

    public static StructureTemplate load(Path path) {
        try (var input = new BufferedInputStream(Files.newInputStream(path))) {
            var compression = resolveCompression(input);
            var root = BinaryTagIO.reader().readNamed(input, compression).getValue();
            return read(root);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read structure template: " + path, exception);
        }
    }

    private static BinaryTagIO.Compression resolveCompression(BufferedInputStream input) throws IOException {
        input.mark(2);
        var first = input.read();
        var second = input.read();
        input.reset();
        if (first == 0x1F && second == 0x8B) {
            return BinaryTagIO.Compression.GZIP;
        }
        return BinaryTagIO.Compression.NONE;
    }

    private static StructureTemplate read(CompoundBinaryTag root) {
        var sizeTag = getList(root, TAG_SIZE);
        var size = new BlockVec(
                getInt(sizeTag, 0),
                getInt(sizeTag, 1),
                getInt(sizeTag, 2));

        var paletteStates = readPalettes(root);
        var blocksTag = getList(root, TAG_BLOCKS);

        var rawBlocks = new ArrayList<RawBlock>(blocksTag.size());
        for (var entry : blocksTag) {
            if (!(entry instanceof CompoundBinaryTag blockTag)) {
                continue;
            }

            var posTag = getList(blockTag, TAG_POS);
            var blockPosition = new BlockVec(
                    getInt(posTag, 0),
                    getInt(posTag, 1),
                    getInt(posTag, 2));

            var stateIndex = getInt(blockTag, TAG_STATE);
            CompoundBinaryTag nbtCompound = null;
            if (blockTag.get(TAG_NBT) instanceof CompoundBinaryTag compound) {
                nbtCompound = compound;
            }

            rawBlocks.add(new RawBlock(blockPosition, stateIndex, nbtCompound));
        }

        var palettes = new ArrayList<Palette>(paletteStates.size());
        for (var states : paletteStates) {
            palettes.add(Palette.build(rawBlocks, states));
        }

        return new StructureTemplate(size, List.copyOf(palettes));
    }

    private static List<List<Block>> readPalettes(CompoundBinaryTag root) {
        var palettesTag = getList(root, TAG_PALETTES);
        if (!palettesTag.isEmpty()) {
            var palettes = new ArrayList<List<Block>>(palettesTag.size());
            for (var entry : palettesTag) {
                if (entry instanceof ListBinaryTag list) {
                    palettes.add(parsePalette(list));
                }
            }
            if (!palettes.isEmpty()) {
                return palettes;
            }
        }

        return List.of(parsePalette(getList(root, TAG_PALETTE)));
    }

    private static List<Block> parsePalette(ListBinaryTag paletteTag) {
        var palette = new ArrayList<Block>(paletteTag.size());
        for (var entry : paletteTag) {
            if (!(entry instanceof CompoundBinaryTag stateTag)) {
                continue;
            }

            var nameTag = stateTag.get(TAG_NAME);
            if (!(nameTag instanceof StringBinaryTag name)) {
                continue;
            }

            var block = Block.fromKey(name.value());
            if (block == null) {
                throw new IllegalStateException("Unknown block key in structure palette: " + name.value());
            }

            var properties = stateTag.get(TAG_PROPERTIES);
            if (properties instanceof CompoundBinaryTag propertyTag && !propertyTag.isEmpty()) {
                var propertyMap = new HashMap<String, String>();
                for (var propertyEntry : propertyTag) {
                    var valueTag = propertyEntry.getValue();
                    if (valueTag instanceof StringBinaryTag stringValue) {
                        propertyMap.put(propertyEntry.getKey(), stringValue.value());
                    }
                }
                if (!propertyMap.isEmpty()) {
                    block = block.withProperties(Map.copyOf(propertyMap));
                }
            }

            palette.add(block);
        }

        return List.copyOf(palette);
    }

    private static int getInt(ListBinaryTag list, int index) {
        if (index < 0 || index >= list.size()) {
            return 0;
        }

        var tag = list.get(index);
        if (tag instanceof IntBinaryTag intTag) {
            return intTag.value();
        }

        return 0;
    }

    private static int getInt(CompoundBinaryTag tag, String key) {
        var value = tag.get(key);
        if (value instanceof IntBinaryTag intTag) {
            return intTag.value();
        }

        return 0;
    }

    private static ListBinaryTag getList(CompoundBinaryTag tag, String key) {
        var value = tag.get(key);
        if (value instanceof ListBinaryTag list) {
            return list;
        }
        return ListBinaryTag.empty();
    }

    private record RawBlock(BlockVec position, int stateIndex, CompoundBinaryTag nbt) {
    }

    public record StructureBlock(BlockVec position, Block block, CompoundBinaryTag nbt) {
        public StructureBlock {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(block, "block");
        }
    }

    /**
     * One resolved palette: blocks in vanilla iteration order (full blocks,
     * partial blocks, block entities; each sorted by Y, X, Z) plus its jigsaw
     * block infos.
     */
    private record Palette(List<StructureBlock> blocks, List<JigsawBlockInfo> jigsaws) {
        private static final Comparator<StructureBlock> VANILLA_ORDER =
                Comparator.<StructureBlock>comparingInt(block -> block.position().blockY())
                        .thenComparingInt(block -> block.position().blockX())
                        .thenComparingInt(block -> block.position().blockZ());

        static Palette build(List<RawBlock> rawBlocks, List<Block> states) {
            var fullBlocks = new ArrayList<StructureBlock>();
            var otherBlocks = new ArrayList<StructureBlock>();
            var blockEntities = new ArrayList<StructureBlock>();

            for (var raw : rawBlocks) {
                if (raw.stateIndex() < 0 || raw.stateIndex() >= states.size()) {
                    continue;
                }

                var state = states.get(raw.stateIndex());
                var structureBlock = new StructureBlock(raw.position(), state, raw.nbt());
                if (raw.nbt() != null) {
                    blockEntities.add(structureBlock);
                } else if (isFullCollisionBlock(state)) {
                    fullBlocks.add(structureBlock);
                } else {
                    otherBlocks.add(structureBlock);
                }
            }

            fullBlocks.sort(VANILLA_ORDER);
            otherBlocks.sort(VANILLA_ORDER);
            blockEntities.sort(VANILLA_ORDER);

            var blocks = new ArrayList<StructureBlock>(rawBlocks.size());
            blocks.addAll(fullBlocks);
            blocks.addAll(otherBlocks);
            blocks.addAll(blockEntities);

            var jigsaws = new ArrayList<JigsawBlockInfo>();
            for (var block : blocks) {
                if (block.block().key().asString().equals("minecraft:jigsaw")) {
                    jigsaws.add(JigsawBlockInfo.fromBlock(block.position(), block.block(), block.nbt()));
                }
            }

            return new Palette(List.copyOf(blocks), List.copyOf(jigsaws));
        }

        private static boolean isFullCollisionBlock(Block state) {
            var shape = state.registry().collisionShape();
            var start = shape.relativeStart();
            var end = shape.relativeEnd();
            return start.x() == 0.0 && start.y() == 0.0 && start.z() == 0.0
                    && end.x() == 1.0 && end.y() == 1.0 && end.z() == 1.0;
        }
    }
}
