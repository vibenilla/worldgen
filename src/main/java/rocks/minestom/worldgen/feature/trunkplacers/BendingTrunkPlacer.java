package rocks.minestom.worldgen.feature.trunkplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public record BendingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, int minHeightForLeaves, IntProvider bendLength) implements TrunkPlacer {
    public static final Codec<BendingTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, BendingTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, BendingTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, BendingTrunkPlacer::heightRandB,
            "min_height_for_leaves", Codec.INT.optional(1), BendingTrunkPlacer::minHeightForLeaves,
            "bend_length", IntProvider.CODEC, BendingTrunkPlacer::bendLength,
            BendingTrunkPlacer::new
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
        var direction = HorizontalDirection.random(random);
        var topIndex = freeTreeHeight - 1;
        var currentPos = basePos;

        TrunkPlacer.setDirtAt(getter, logSetter, random, basePos.sub(0, 1, 0), config);

        var foliageAttachments = new ArrayList<FoliagePlacer.FoliageAttachment>();

        for (var trunkIndex = 0; trunkIndex <= topIndex; trunkIndex++) {
            if (trunkIndex + 1 >= topIndex + random.nextInt(2)) {
                currentPos = currentPos.add(direction.stepX, 0, direction.stepZ);
            }

            if (Feature.isValidTreePosition(getter, currentPos)) {
                this.placeLog(getter, logSetter, random, currentPos, config);
            }

            if (trunkIndex >= this.minHeightForLeaves) {
                foliageAttachments.add(new FoliagePlacer.FoliageAttachment(currentPos, 0, false));
            }

            currentPos = currentPos.add(0, 1, 0);
        }

        var bendLength = this.bendLength.sample(random);
        for (var bendIndex = 0; bendIndex <= bendLength; bendIndex++) {
            if (Feature.isValidTreePosition(getter, currentPos)) {
                this.placeLog(getter, logSetter, random, currentPos, config);
            }

            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(currentPos, 0, false));
            currentPos = currentPos.add(direction.stepX, 0, direction.stepZ);
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

