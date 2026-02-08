package rocks.minestom.worldgen.feature.trunkplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public record ForkingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) implements TrunkPlacer {
    public static final Codec<ForkingTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, ForkingTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, ForkingTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, ForkingTrunkPlacer::heightRandB,
            ForkingTrunkPlacer::new
    );

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            int freeTreeHeight,
            BlockVec basePos,
            TreeConfiguration config
    ) {
        TrunkPlacer.setDirtAt(getter, logSetter, random, basePos.sub(0, 1, 0), config);

        var foliageAttachments = new ArrayList<FoliagePlacer.FoliageAttachment>();
        var mainDirection = HorizontalDirection.random(random);
        var bendStart = freeTreeHeight - random.nextInt(4) - 1;
        var bendLength = 3 - random.nextInt(3);

        var blockX = basePos.blockX();
        var blockZ = basePos.blockZ();
        Integer lastY = null;

        for (var trunkIndex = 0; trunkIndex < freeTreeHeight; trunkIndex++) {
            var blockY = basePos.blockY() + trunkIndex;

            if (trunkIndex >= bendStart && bendLength > 0) {
                blockX += mainDirection.stepX;
                blockZ += mainDirection.stepZ;
                bendLength--;
            }

            if (this.placeLog(getter, logSetter, random, new BlockVec(blockX, blockY, blockZ), config)) {
                lastY = blockY + 1;
            }
        }

        if (lastY != null) {
            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(new BlockVec(blockX, lastY, blockZ), 1, false));
        }

        blockX = basePos.blockX();
        blockZ = basePos.blockZ();
        var branchDirection = HorizontalDirection.random(random);

        if (!branchDirection.equals(mainDirection)) {
            var branchStart = bendStart - random.nextInt(2) - 1;
            var branchSteps = 1 + random.nextInt(3);
            lastY = null;

            for (var trunkIndex = branchStart; trunkIndex < freeTreeHeight && branchSteps > 0; trunkIndex++) {
                if (trunkIndex >= 1) {
                    var blockY = basePos.blockY() + trunkIndex;
                    blockX += branchDirection.stepX;
                    blockZ += branchDirection.stepZ;
                    if (this.placeLog(getter, logSetter, random, new BlockVec(blockX, blockY, blockZ), config)) {
                        lastY = blockY + 1;
                    }
                }

                branchSteps--;
            }

            if (lastY != null) {
                foliageAttachments.add(new FoliagePlacer.FoliageAttachment(new BlockVec(blockX, lastY, blockZ), 0, false));
            }
        }

        return foliageAttachments;
    }

    @Override
    public int getTreeHeight(RandomSource random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }

    private record HorizontalDirection(int stepX, int stepZ) {
        private static final HorizontalDirection[] VALUES = new HorizontalDirection[]{
                new HorizontalDirection(1, 0),
                new HorizontalDirection(-1, 0),
                new HorizontalDirection(0, 1),
                new HorizontalDirection(0, -1)
        };

        private static HorizontalDirection random(RandomSource random) {
            return VALUES[random.nextInt(VALUES.length)];
        }
    }
}

