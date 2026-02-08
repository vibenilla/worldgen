package rocks.minestom.worldgen.feature.trunkplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.feature.valueproviders.UniformIntProvider;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class CherryTrunkPlacer implements TrunkPlacer {
    public static final Codec<CherryTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, CherryTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, CherryTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, CherryTrunkPlacer::heightRandB,
            "branch_count", IntProvider.CODEC, CherryTrunkPlacer::branchCount,
            "branch_horizontal_length", IntProvider.CODEC, CherryTrunkPlacer::branchHorizontalLength,
            "branch_start_offset_from_top", UniformIntProvider.CODEC, CherryTrunkPlacer::branchStartOffsetFromTop,
            "branch_end_offset_from_top", IntProvider.CODEC, CherryTrunkPlacer::branchEndOffsetFromTop,
            CherryTrunkPlacer::new
    );

    private final int baseHeight;
    private final int heightRandA;
    private final int heightRandB;
    private final IntProvider branchCount;
    private final IntProvider branchHorizontalLength;
    private final UniformIntProvider branchStartOffsetFromTop;
    private final UniformIntProvider secondBranchStartOffsetFromTop;
    private final IntProvider branchEndOffsetFromTop;

    public CherryTrunkPlacer(
            int baseHeight,
            int heightRandA,
            int heightRandB,
            IntProvider branchCount,
            IntProvider branchHorizontalLength,
            UniformIntProvider branchStartOffsetFromTop,
            IntProvider branchEndOffsetFromTop
    ) {
        this.baseHeight = baseHeight;
        this.heightRandA = heightRandA;
        this.heightRandB = heightRandB;
        this.branchCount = branchCount;
        this.branchHorizontalLength = branchHorizontalLength;
        this.branchStartOffsetFromTop = branchStartOffsetFromTop;
        this.secondBranchStartOffsetFromTop = new UniformIntProvider(branchStartOffsetFromTop.minInclusive(), branchStartOffsetFromTop.maxInclusive() - 1);
        this.branchEndOffsetFromTop = branchEndOffsetFromTop;
    }

    public int baseHeight() {
        return this.baseHeight;
    }

    public int heightRandA() {
        return this.heightRandA;
    }

    public int heightRandB() {
        return this.heightRandB;
    }

    public IntProvider branchCount() {
        return this.branchCount;
    }

    public IntProvider branchHorizontalLength() {
        return this.branchHorizontalLength;
    }

    public UniformIntProvider branchStartOffsetFromTop() {
        return this.branchStartOffsetFromTop;
    }

    public IntProvider branchEndOffsetFromTop() {
        return this.branchEndOffsetFromTop;
    }

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

        var firstBranchStart = Math.max(0, freeTreeHeight - 1 + this.branchStartOffsetFromTop.sample(random));
        var secondBranchStart = Math.max(0, freeTreeHeight - 1 + this.secondBranchStartOffsetFromTop.sample(random));
        if (secondBranchStart >= firstBranchStart) {
            secondBranchStart++;
        }

        var branchCount = this.branchCount.sample(random);
        var threeBranches = branchCount == 3;
        var atLeastTwoBranches = branchCount >= 2;

        int trunkHeight;
        if (threeBranches) {
            trunkHeight = freeTreeHeight;
        } else if (atLeastTwoBranches) {
            trunkHeight = Math.max(firstBranchStart, secondBranchStart) + 1;
        } else {
            trunkHeight = firstBranchStart + 1;
        }

        for (var trunkIndex = 0; trunkIndex < trunkHeight; trunkIndex++) {
            this.placeLog(getter, logSetter, random, basePos.add(0, trunkIndex, 0), config);
        }

        var foliageAttachments = new ArrayList<FoliagePlacer.FoliageAttachment>();
        if (threeBranches) {
            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(basePos.add(0, trunkHeight, 0), 0, false));
        }

        var direction = HorizontalDirection.random(random);

        foliageAttachments.add(this.generateBranch(getter, logSetter, random, freeTreeHeight, basePos, config, direction, firstBranchStart, firstBranchStart < trunkHeight - 1));
        if (atLeastTwoBranches) {
            foliageAttachments.add(this.generateBranch(getter, logSetter, random, freeTreeHeight, basePos, config, direction.opposite(), secondBranchStart, secondBranchStart < trunkHeight - 1));
        }

        return foliageAttachments;
    }

    private FoliagePlacer.FoliageAttachment generateBranch(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            int freeTreeHeight,
            BlockVec basePos,
            TreeConfiguration config,
            HorizontalDirection direction,
            int branchStart,
            boolean trunkContinuesAboveBranch
    ) {
        var currentPos = basePos.add(0, branchStart, 0);

        var branchEndOffset = freeTreeHeight - 1 + this.branchEndOffsetFromTop.sample(random);
        var shortOrContinues = trunkContinuesAboveBranch || branchEndOffset < branchStart;
        var horizontalLength = this.branchHorizontalLength.sample(random) + (shortOrContinues ? 1 : 0);

        var targetPos = basePos.add(direction.stepX * horizontalLength, branchEndOffset, direction.stepZ * horizontalLength);
        var initialSteps = shortOrContinues ? 2 : 1;

        for (var branchIndex = 0; branchIndex < initialSteps; branchIndex++) {
            currentPos = currentPos.add(direction.stepX, 0, direction.stepZ);
            this.placeLogWithAxis(getter, logSetter, random, currentPos, config, direction.axisProperty());
        }

        var verticalStep = targetPos.blockY() > currentPos.blockY() ? 1 : -1;

        while (true) {
            var manhattan = Math.abs(targetPos.blockX() - currentPos.blockX())
                    + Math.abs(targetPos.blockY() - currentPos.blockY())
                    + Math.abs(targetPos.blockZ() - currentPos.blockZ());

            if (manhattan == 0) {
                return new FoliagePlacer.FoliageAttachment(targetPos.add(0, 1, 0), 0, false);
            }

            var verticalDistance = Math.abs(targetPos.blockY() - currentPos.blockY());
            var verticalChance = (float) verticalDistance / (float) manhattan;
            var moveVertical = random.nextFloat() < verticalChance;

            if (moveVertical) {
                currentPos = currentPos.add(0, verticalStep, 0);
                this.placeLog(getter, logSetter, random, currentPos, config);
            } else {
                currentPos = currentPos.add(direction.stepX, 0, direction.stepZ);
                this.placeLogWithAxis(getter, logSetter, random, currentPos, config, direction.axisProperty());
            }
        }
    }

    private void placeLogWithAxis(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            BlockVec pos,
            TreeConfiguration config,
            String axis
    ) {
        if (!this.isValidTreePosition(getter, pos)) {
            return;
        }

        var log = config.trunkProvider().getState(random, pos);
        try {
            logSetter.accept(pos, log.withProperty("axis", axis));
        } catch (IllegalArgumentException exception) {
            logSetter.accept(pos, log);
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

        private HorizontalDirection opposite() {
            return new HorizontalDirection(-this.stepX, -this.stepZ);
        }

        private String axisProperty() {
            if (this.stepX != 0) {
                return "x";
            }
            return "z";
        }
    }
}

