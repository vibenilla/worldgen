package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.FossilFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.structure.processor.BlockRotProcessor;
import rocks.minestom.worldgen.structure.processor.StructureBlockInfo;
import rocks.minestom.worldgen.structure.processor.StructureProcessor;
import rocks.minestom.worldgen.structure.processor.StructureProcessorContext;
import rocks.minestom.worldgen.structure.template.BoundingBox;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla {@code FossilFeature}: picks a random rotation and a random
 * base/overlay structure template pair, buries it below the local surface
 * (jittered further down by a random offset), rejects the placement when too
 * many corners of its bounding box are empty or liquid, and otherwise places
 * the rot-processed base template followed by the ore overlay template at the
 * same position and rotation.
 */
public final class FossilFeature implements Feature<FossilFeatureConfiguration> {

    /** Level types that can answer OCEAN_FLOOR_WG queries in tests. */
    public interface WorldSurface {
        int worldSurfaceHeight(int x, int z);
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<FossilFeatureConfiguration, T> context) {
        var random = context.random();
        var level = context.accessor();
        var origin = context.origin();
        var config = context.config();
        var rotation = Rotation.getRandom(random);
        var fossilIndex = random.nextInt(config.fossilStructures().size());
        var fossilBase = config.fossilStructures().get(fossilIndex);
        var fossilOverlay = config.overlayStructures().get(fossilIndex);

        var rotatedSize = rotation.rotateSize(fossilBase.size());
        var lowCorner = origin.add(-rotatedSize.blockX() / 2, 0, -rotatedSize.blockZ() / 2);
        var lowestSurfaceY = origin.blockY();

        for (var xscan = 0; xscan < rotatedSize.blockX(); xscan++) {
            for (var zscan = 0; zscan < rotatedSize.blockZ(); zscan++) {
                lowestSurfaceY = Math.min(lowestSurfaceY,
                        oceanFloorHeight(level, lowCorner.blockX() + xscan, lowCorner.blockZ() + zscan));
            }
        }

        var targetY = Math.max(lowestSurfaceY - 15 - random.nextInt(10), context.minY() + 10);
        var targetPos = zeroPositionWithTransform(lowCorner.withBlockY(targetY), rotation, fossilBase.size());
        var cornerBox = fossilBase.getBoundingBox(targetPos, rotation);
        if (countEmptyCorners(level, cornerBox) > config.maxEmptyCornersAllowed()) {
            return false;
        }

        var processorContext = new StructureProcessorContext(level, config.blockTags(), context.worldSeed(),
                targetPos, targetPos, null);
        placeTemplate(level, fossilBase, targetPos, rotation, config.fossilProcessors().processors(),
                processorContext, random, config.blockTags());
        placeTemplate(level, fossilOverlay, targetPos, rotation, config.overlayProcessors().processors(),
                processorContext, random, config.blockTags());
        return true;
    }

    /**
     * Vanilla {@code StructureTemplate.getZeroPositionWithTransform} with the
     * mirror always {@code NONE}: the world position of the template's own
     * (unrotated) zero corner such that the rotated template occupies the
     * intended footprint.
     */
    private static BlockVec zeroPositionWithTransform(BlockVec zeroPos, Rotation rotation, BlockVec templateSize) {
        var sizeX = templateSize.blockX() - 1;
        var sizeZ = templateSize.blockZ() - 1;
        return switch (rotation) {
            case CLOCKWISE_90 -> zeroPos.add(sizeZ, 0, 0);
            case CLOCKWISE_180 -> zeroPos.add(sizeX, 0, sizeZ);
            case COUNTERCLOCKWISE_90 -> zeroPos.add(0, 0, sizeX);
            case NONE -> zeroPos;
        };
    }

    /**
     * Vanilla {@code FossilFeature.countEmptyCorners}: how many of the eight
     * corners of the structure's bounding box are air, lava or water.
     */
    private static <T extends Block.Getter & Block.Setter> int countEmptyCorners(T level, BoundingBox box) {
        var count = 0;
        for (var x : new int[]{box.minX(), box.maxX()}) {
            for (var y : new int[]{box.minY(), box.maxY()}) {
                for (var z : new int[]{box.minZ(), box.maxZ()}) {
                    var state = level.getBlock(x, y, z);
                    if (state.isAir() || state.compare(Block.LAVA) || state.compare(Block.WATER)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * Vanilla {@code StructureTemplate.placeInWorld} as invoked directly by
     * {@code FossilFeature} (no jigsaw wrapper processors, no gravity or
     * legacy passes): every template block runs through the given processor
     * list only, then survivors are rotated and written.
     *
     * <p>Vanilla's fossil placement calls {@code StructurePlaceSettings.setRandom}
     * with the feature's shared random, so two of that class's internals draw
     * from it directly instead of from a disposable per-call random: {@code
     * StructurePlaceSettings.getRandomPalette} always draws {@code
     * nextInt(paletteSize)} once per placement (even for the fossils' single
     * palette), and {@code BlockRotProcessor} draws from {@code
     * StructurePlaceSettings.getRandom} (the shared random when set, unlike
     * its usual per-position draw) once per block it processes; every
     * block_rot entry in the fossil processor lists is therefore driven from
     * the shared random here instead of going through the generic
     * per-position processor pipeline.
     */
    private static <T extends Block.Getter & Block.Setter> void placeTemplate(
            T level, StructureTemplate template, BlockVec targetPos, Rotation rotation,
            List<StructureProcessor> processors, StructureProcessorContext processorContext,
            RandomSource random, BlockTagManager blockTags) {
        random.nextInt(1);
        var originalBlockInfoList = new ArrayList<StructureBlockInfo>();
        var processedBlockInfoList = new ArrayList<StructureBlockInfo>();

        for (var blockEntry : template.blocks()) {
            var templatePos = blockEntry.position();
            var rotatedPos = rotation.rotate(templatePos, template.size());
            var worldPos = new BlockVec(
                    targetPos.blockX() + rotatedPos.blockX(),
                    targetPos.blockY() + rotatedPos.blockY(),
                    targetPos.blockZ() + rotatedPos.blockZ());

            StructureBlockInfo processed = new StructureBlockInfo(worldPos, blockEntry.block(), blockEntry.nbt());
            for (var processor : processors) {
                if (processor instanceof BlockRotProcessor blockRot) {
                    processed = applyBlockRot(blockRot, processed, random, blockTags);
                } else {
                    processed = processor.processBlock(processorContext, templatePos, processed);
                }
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
        for (var processor : processors) {
            finalized = processor.finalizeProcessing(processorContext, originalBlockInfoList, finalized);
        }

        for (var blockInfo : finalized) {
            var state = StructureTemplate.rotateBlockState(blockInfo.state(), rotation);
            var blockPos = blockInfo.pos();
            if (state.getProperty("waterlogged") != null && isSourceWater(level.getBlock(blockPos))) {
                state = state.withProperty("waterlogged", "true");
            }
            level.setBlock(blockPos, state);
        }
    }

    /**
     * Vanilla {@code BlockRotProcessor.processBlock} with
     * {@code StructurePlaceSettings.getRandom} resolved to the shared random
     * (see {@link #placeTemplate}): the block survives when it is not gated by
     * a rottable-blocks tag, or is in it, and the shared random's next float
     * falls at or under the integrity.
     */
    private static StructureBlockInfo applyBlockRot(
            BlockRotProcessor processor, StructureBlockInfo processed, RandomSource random, BlockTagManager blockTags) {
        var tag = processor.rottableBlocksTag();
        var gated = tag == null || (blockTags != null && blockTags.blocks(tag).contains(processed.state().key()));
        if (gated && !(random.nextFloat() <= processor.integrity())) {
            return null;
        }
        return processed;
    }

    private static boolean isSourceWater(Block block) {
        if (block.compare(Block.WATER)) {
            var levelProperty = block.getProperty("level");
            return levelProperty == null || levelProperty.equals("0");
        }
        return "true".equals(block.getProperty("waterlogged"));
    }

    private static <T extends Block.Getter & Block.Setter> int oceanFloorHeight(T level, int x, int z) {
        if (level instanceof GenerationUnitAdapter adapter) {
            return adapter.getHeight(x, z);
        }

        if (level instanceof WorldSurface surface) {
            return surface.worldSurfaceHeight(x, z);
        }

        return Integer.MAX_VALUE;
    }
}
