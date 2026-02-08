package rocks.minestom.worldgen.structure.template;

import net.kyori.adventure.nbt.*;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;
import rocks.minestom.worldgen.random.PositionalRandomFactory;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.structure.loader.StructureLoader;
import rocks.minestom.worldgen.structure.processor.StructureProcessor;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * A structure template loaded from an NBT file.
 *
 * <p>
 * Templates are the fundamental building blocks of structures, containing:
 * <ul>
 * <li>Block palette and positions
 * <li>Jigsaw block information for assembly
 * <li>Entity data (not yet implemented)
 * </ul>
 *
 * <p>
 * Templates support rotation during placement via {@link Rotation}, and blocks
 * can be modified by {@link StructureProcessor}s.
 *
 * <p>
 * Two placement modes are available:
 * <ul>
 * <li>{@link #place} - Standard placement at a fixed origin
 * <li>{@link #placeTerrainMatching} - Each column matches terrain surface
 * height
 * </ul>
 *
 * @see StructureLoader for loading templates from data packs
 */
public final class StructureTemplate {
    private static final Logger LOGGER = LoggerFactory.getLogger(StructureTemplate.class);
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
    private final List<StructureBlock> blocks;
    private final List<JigsawBlockInfo> jigsaws;

    private StructureTemplate(BlockVec size, List<StructureBlock> blocks, List<JigsawBlockInfo> jigsaws) {
        this.size = size;
        this.blocks = blocks;
        this.jigsaws = jigsaws;
    }

    public BlockVec size() {
        return this.size;
    }

    public List<StructureBlock> blocks() {
        return this.blocks;
    }

    public List<JigsawBlockInfo> getJigsaws(BlockVec origin, Rotation rotation) {
        var result = new ArrayList<JigsawBlockInfo>(this.jigsaws.size());
        for (var jigsaw : this.jigsaws) {
            var rotatedLocalPos = rotation.rotate(jigsaw.position(), this.size);
            var worldPos = new BlockVec(
                    origin.blockX() + rotatedLocalPos.blockX(),
                    origin.blockY() + rotatedLocalPos.blockY(),
                    origin.blockZ() + rotatedLocalPos.blockZ());
            result.add(jigsaw.withRotation(rotation, worldPos));
        }
        return result;
    }

    public BoundingBox getBoundingBox(BlockVec origin, Rotation rotation) {
        var maxCorner = new BlockVec(
                this.size.blockX() - 1,
                this.size.blockY() - 1,
                this.size.blockZ() - 1);
        var corner1 = rotation.rotate(BlockVec.ZERO, this.size);
        var corner2 = rotation.rotate(maxCorner, this.size);
        return BoundingBox.fromCorners(corner1, corner2).moved(origin.blockX(), origin.blockY(), origin.blockZ());
    }

    public void place(GenerationUnitAdapter level, BlockVec origin, StructureProcessorList processors,
            PositionalRandomFactory randomFactory, BlockTagManager blockTags) {
        this.place(level, origin, Rotation.NONE, processors, randomFactory, blockTags);
    }

    public void place(GenerationUnitAdapter level, BlockVec origin, Rotation rotation,
            StructureProcessorList processors, PositionalRandomFactory randomFactory, BlockTagManager blockTags) {
        for (var blockEntry : this.blocks) {
            var position = blockEntry.position();
            var rotatedPos = rotation.rotate(position, this.size);
            var worldX = origin.blockX() + rotatedPos.blockX();
            var worldY = origin.blockY() + rotatedPos.blockY();
            var worldZ = origin.blockZ() + rotatedPos.blockZ();
            var block = blockEntry.block();

            block = rotateBlockState(block, rotation);

            var random = randomFactory.at(worldX, worldY, worldZ);
            var processed = processors.apply(block, new StructureProcessorContext(random, blockTags));
            if (processed == null) {
                continue;
            }

            var blockKey = processed.key().asString();
            if (blockKey.equals("minecraft:structure_void") || blockKey.equals("minecraft:jigsaw")) {
                continue;
            }

            level.setBlock(new BlockVec(worldX, worldY, worldZ), processed);
        }
    }

    public void placeTerrainMatching(GenerationUnitAdapter level, BlockVec origin, Rotation rotation,
            StructureProcessorList processors, PositionalRandomFactory randomFactory, BlockTagManager blockTags,
            int projectionOffset, int[] surfaceHeights, int chunkStartX, int chunkStartZ, int chunkSizeX,
            int chunkSizeZ) {
        // Use a single reference surface height at the piece origin
        // This keeps the structure cohesive and avoids "block vomit" from per-block
        // adjustments
        var originLocalX = origin.blockX() - chunkStartX;
        var originLocalZ = origin.blockZ() - chunkStartZ;

        var referenceSurfaceY = origin.blockY();
        if (originLocalX >= 0 && originLocalX < chunkSizeX && originLocalZ >= 0 && originLocalZ < chunkSizeZ) {
            var surfaceIndex = originLocalX * chunkSizeZ + originLocalZ;
            var surfaceY = surfaceHeights[surfaceIndex];
            if (surfaceY != Integer.MIN_VALUE) {
                referenceSurfaceY = surfaceY;
            }
        }

        for (var blockEntry : this.blocks) {
            var position = blockEntry.position();
            var rotatedPos = rotation.rotate(position, this.size);
            var worldX = origin.blockX() + rotatedPos.blockX();
            var worldZ = origin.blockZ() + rotatedPos.blockZ();

            var worldY = referenceSurfaceY + projectionOffset + rotatedPos.blockY();
            var block = blockEntry.block();

            block = rotateBlockState(block, rotation);

            var random = randomFactory.at(worldX, worldY, worldZ);
            var processed = processors.apply(block, new StructureProcessorContext(random, blockTags));
            if (processed == null) {
                continue;
            }

            var blockKey = processed.key().asString();
            if (blockKey.equals("minecraft:structure_void") || blockKey.equals("minecraft:jigsaw")) {
                continue;
            }

            level.setBlock(new BlockVec(worldX, worldY, worldZ), processed);
        }
    }

    private static Block rotateBlockState(Block block, Rotation rotation) {
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

    private static Direction parseDirection(String name) {
        return switch (name.toLowerCase()) {
            case "up" -> Direction.UP;
            case "down" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
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

        var palette = readPalette(root);
        var blocksTag = getList(root, TAG_BLOCKS);
        var blocks = new ArrayList<StructureBlock>(blocksTag.size());
        var jigsaws = new ArrayList<JigsawBlockInfo>();

        for (var entry : blocksTag) {
            if (!(entry instanceof CompoundBinaryTag blockTag)) {
                continue;
            }

            var posTag = getList(blockTag, TAG_POS);
            var position = new BlockVec(
                    getInt(posTag, 0),
                    getInt(posTag, 1),
                    getInt(posTag, 2));

            var stateIndex = getInt(blockTag, TAG_STATE);
            if (stateIndex < 0 || stateIndex >= palette.size()) {
                continue;
            }

            var block = palette.get(stateIndex);
            var nbt = blockTag.get(TAG_NBT);
            CompoundBinaryTag nbtCompound = null;
            if (nbt instanceof CompoundBinaryTag compound) {
                nbtCompound = compound;
                block = block.withNbt(compound);
            }

            var blockKey = block.key().asString();
            if (blockKey.equals("minecraft:jigsaw")) {
                var orientation = block.getProperty("orientation");
                LOGGER.debug("Jigsaw block at {} has orientation property: {}", position, orientation);
                var jigsawInfo = JigsawBlockInfo.fromBlock(position, block, nbtCompound);
                jigsaws.add(jigsawInfo);
                LOGGER.debug("Found jigsaw at {}: pool={}, name={}, target={}, front={}, top={}",
                        position, jigsawInfo.pool(), jigsawInfo.name(), jigsawInfo.target(), jigsawInfo.front(),
                        jigsawInfo.top());
            }

            blocks.add(new StructureBlock(position, block));
        }

        if (!jigsaws.isEmpty()) {
            LOGGER.debug("Template loaded: {} blocks, {} jigsaws", blocks.size(), jigsaws.size());
        }

        return new StructureTemplate(size, List.copyOf(blocks), List.copyOf(jigsaws));
    }

    private static List<Block> readPalette(CompoundBinaryTag root) {
        var palettesTag = getList(root, TAG_PALETTES);
        if (!palettesTag.isEmpty()) {
            var firstPalette = palettesTag.get(0);
            if (firstPalette instanceof ListBinaryTag list) {
                return parsePalette(list);
            }
        }

        var paletteTag = getList(root, TAG_PALETTE);
        return parsePalette(paletteTag);
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

    public record StructureBlock(BlockVec position, Block block) {
        public StructureBlock {
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(block, "block");
        }
    }
}
