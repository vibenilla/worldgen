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

public record FancyTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) implements TrunkPlacer {
    public static final Codec<FancyTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, FancyTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, FancyTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, FancyTrunkPlacer::heightRandB,
            FancyTrunkPlacer::new
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
        var treeHeight = freeTreeHeight + 2;
        var trunkHeight = (int) Math.floor((double) treeHeight * 0.618D);

        TrunkPlacer.setDirtAt(getter, logSetter, random, basePos.sub(0, 1, 0), config);

        var branchCount = Math.min(1, (int) Math.floor(1.382D + Math.pow(1.0D * (double) treeHeight / 13.0D, 2.0D)));
        var trunkTopY = basePos.blockY() + trunkHeight;
        var branchY = treeHeight - 5;

        var foliageCoords = new ArrayList<FoliageCoords>();
        foliageCoords.add(new FoliageCoords(basePos.add(0, branchY, 0), trunkTopY));

        for (; branchY >= 0; branchY--) {
            var shapeRadius = treeShape(treeHeight, branchY);
            if (shapeRadius < 0.0F) {
                continue;
            }

            for (var branchIndex = 0; branchIndex < branchCount; branchIndex++) {
                var branchRadius = 1.0D * (double) shapeRadius * ((double) random.nextFloat() + 0.328D);
                var angle = (double) (random.nextFloat() * 2.0F) * Math.PI;
                var offsetX = branchRadius * Math.sin(angle) + 0.5D;
                var offsetZ = branchRadius * Math.cos(angle) + 0.5D;

                var branchStart = basePos.add((int) Math.floor(offsetX), branchY - 1, (int) Math.floor(offsetZ));
                var branchEnd = branchStart.add(0, 5, 0);

                if (this.makeLimb(getter, logSetter, random, branchStart, branchEnd, false, config)) {
                    var deltaX = basePos.blockX() - branchStart.blockX();
                    var deltaZ = basePos.blockZ() - branchStart.blockZ();
                    var projectedY = (double) branchStart.blockY() - Math.sqrt((double) (deltaX * deltaX + deltaZ * deltaZ)) * 0.381D;
                    var branchBaseY = projectedY > (double) trunkTopY ? trunkTopY : (int) projectedY;

                    var branchBase = new BlockVec(basePos.blockX(), branchBaseY, basePos.blockZ());
                    if (this.makeLimb(getter, logSetter, random, branchBase, branchStart, false, config)) {
                        foliageCoords.add(new FoliageCoords(branchStart, branchBaseY));
                    }
                }
            }
        }

        this.makeLimb(getter, logSetter, random, basePos, basePos.add(0, trunkHeight, 0), true, config);
        this.makeBranches(getter, logSetter, random, treeHeight, basePos, foliageCoords, config);

        var foliageAttachments = new ArrayList<FoliagePlacer.FoliageAttachment>();
        for (var coords : foliageCoords) {
            if (this.trimBranches(treeHeight, coords.branchBaseY - basePos.blockY())) {
                foliageAttachments.add(coords.attachment);
            }
        }

        return foliageAttachments;
    }

    private void makeBranches(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            int treeHeight,
            BlockVec basePos,
            List<FoliageCoords> coords,
            TreeConfiguration config
    ) {
        for (var foliageCoords : coords) {
            var branchBaseY = foliageCoords.branchBaseY;
            var branchStart = new BlockVec(basePos.blockX(), branchBaseY, basePos.blockZ());
            if (!branchStart.equals(foliageCoords.attachment.pos()) && this.trimBranches(treeHeight, branchBaseY - basePos.blockY())) {
                this.makeLimb(getter, logSetter, random, branchStart, foliageCoords.attachment.pos(), true, config);
            }
        }
    }

    private boolean makeLimb(
            Block.Getter getter,
            BiConsumer<BlockVec, Block> logSetter,
            RandomSource random,
            BlockVec start,
            BlockVec end,
            boolean placeLogs,
            TreeConfiguration config
    ) {
        if (!placeLogs && start.equals(end)) {
            return true;
        }

        var delta = end.sub(start);
        var steps = this.getSteps(delta);

        var stepX = (float) delta.blockX() / (float) steps;
        var stepY = (float) delta.blockY() / (float) steps;
        var stepZ = (float) delta.blockZ() / (float) steps;

        for (var stepIndex = 0; stepIndex <= steps; stepIndex++) {
            var limbPos = start.add(
                    (int) Math.floor(0.5F + (float) stepIndex * stepX),
                    (int) Math.floor(0.5F + (float) stepIndex * stepY),
                    (int) Math.floor(0.5F + (float) stepIndex * stepZ)
            );

            if (placeLogs) {
                var axis = this.getLogAxis(start, limbPos);
                this.placeLogWithAxis(getter, logSetter, random, limbPos, config, axis);
            } else if (!this.isFree(getter, limbPos)) {
                return false;
            }
        }

        return true;
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

    private int getSteps(BlockVec delta) {
        var absX = Math.abs(delta.blockX());
        var absY = Math.abs(delta.blockY());
        var absZ = Math.abs(delta.blockZ());
        return Math.max(absX, Math.max(absY, absZ));
    }

    private String getLogAxis(BlockVec start, BlockVec end) {
        var axis = "y";
        var deltaX = Math.abs(end.blockX() - start.blockX());
        var deltaZ = Math.abs(end.blockZ() - start.blockZ());
        var max = Math.max(deltaX, deltaZ);

        if (max > 0) {
            if (deltaX == max) {
                axis = "x";
            } else {
                axis = "z";
            }
        }

        return axis;
    }

    private boolean trimBranches(int treeHeight, int heightFromBase) {
        return (double) heightFromBase >= (double) treeHeight * 0.2D;
    }

    private static float treeShape(int treeHeight, int y) {
        if ((float) y < (float) treeHeight * 0.3F) {
            return -1.0F;
        }

        var half = (float) treeHeight / 2.0F;
        var offset = half - (float) y;
        var radius = (float) Math.sqrt(half * half - offset * offset);

        if (offset == 0.0F) {
            radius = half;
        } else if (Math.abs(offset) >= half) {
            return 0.0F;
        }

        return radius * 0.5F;
    }

    @Override
    public int getTreeHeight(RandomSource random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }

    private record FoliageCoords(FoliagePlacer.FoliageAttachment attachment, int branchBaseY) {
        private FoliageCoords(BlockVec attachmentPos, int branchBaseY) {
            this(new FoliagePlacer.FoliageAttachment(attachmentPos, 0, false), branchBaseY);
        }
    }
}

