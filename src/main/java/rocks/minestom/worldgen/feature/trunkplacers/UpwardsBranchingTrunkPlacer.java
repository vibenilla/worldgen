package rocks.minestom.worldgen.feature.trunkplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public record UpwardsBranchingTrunkPlacer(
        int baseHeight,
        int heightRandA,
        int heightRandB,
        IntProvider extraBranchSteps,
        float placeBranchPerLogProbability,
        IntProvider extraBranchLength,
        String canGrowThrough
) implements TrunkPlacer {
    public static final Codec<UpwardsBranchingTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, UpwardsBranchingTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, UpwardsBranchingTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, UpwardsBranchingTrunkPlacer::heightRandB,
            "extra_branch_steps", IntProvider.CODEC, UpwardsBranchingTrunkPlacer::extraBranchSteps,
            "place_branch_per_log_probability", Codec.FLOAT, UpwardsBranchingTrunkPlacer::placeBranchPerLogProbability,
            "extra_branch_length", IntProvider.CODEC, UpwardsBranchingTrunkPlacer::extraBranchLength,
            "can_grow_through", Codec.STRING.optional(""), UpwardsBranchingTrunkPlacer::canGrowThrough,
            UpwardsBranchingTrunkPlacer::new
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
        var foliageAttachments = new ArrayList<FoliagePlacer.FoliageAttachment>();

        for (var trunkIndex = 0; trunkIndex < freeTreeHeight; trunkIndex++) {
            var logPos = basePos.add(0, trunkIndex, 0);
            if (this.placeLog(getter, logSetter, random, logPos, config) && trunkIndex < freeTreeHeight - 1 && random.nextFloat() < this.placeBranchPerLogProbability) {
                var direction = HorizontalDirection.random(random);
                var branchLength = this.extraBranchLength.sample(random);
                var branchOffset = Math.max(0, branchLength - this.extraBranchLength.sample(random) - 1);
                var branchSteps = this.extraBranchSteps.sample(random);
                this.placeBranch(getter, logSetter, random, freeTreeHeight, config, foliageAttachments, logPos.blockY(), basePos, direction, branchOffset, branchSteps);
            }

            if (trunkIndex == freeTreeHeight - 1) {
                foliageAttachments.add(new FoliagePlacer.FoliageAttachment(basePos.add(0, trunkIndex + 1, 0), 0, false));
            }
        }

        return foliageAttachments;
    }

    private void placeBranch(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            int freeTreeHeight,
            TreeConfiguration config,
            List<FoliagePlacer.FoliageAttachment> foliageAttachments,
            int baseY,
            BlockVec trunkBasePos,
            HorizontalDirection direction,
            int branchOffset,
            int branchSteps
    ) {
        var branchTopY = baseY + branchOffset;
        var branchX = trunkBasePos.blockX();
        var branchZ = trunkBasePos.blockZ();
        var trunkIndex = branchOffset;

        while (trunkIndex < freeTreeHeight && branchSteps > 0) {
            if (trunkIndex >= 1) {
                var blockY = baseY + trunkIndex;
                branchX += direction.stepX;
                branchZ += direction.stepZ;
                branchTopY = blockY;

                if (this.placeLog(getter, logSetter, random, new BlockVec(branchX, blockY, branchZ), config)) {
                    branchTopY = blockY + 1;
                }

                foliageAttachments.add(new FoliagePlacer.FoliageAttachment(new BlockVec(branchX, blockY, branchZ), 0, false));
            }

            trunkIndex++;
            branchSteps--;
        }

        if (branchTopY - baseY > 1) {
            var branchPos = new BlockVec(branchX, branchTopY, branchZ);
            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(branchPos, 0, false));
            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(branchPos.sub(0, 2, 0), 0, false));
        }
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

