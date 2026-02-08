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

public record DarkOakTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) implements TrunkPlacer {
    public static final Codec<DarkOakTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, DarkOakTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, DarkOakTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, DarkOakTrunkPlacer::heightRandB,
            DarkOakTrunkPlacer::new
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

        var below = basePos.sub(0, 1, 0);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below, config);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below.add(1, 0, 0), config);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below.add(0, 0, 1), config);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below.add(1, 0, 1), config);

        var direction = HorizontalDirection.random(random);
        var bendStart = freeTreeHeight - random.nextInt(4);
        var bendLength = 2 - random.nextInt(3);

        var baseX = basePos.blockX();
        var baseY = basePos.blockY();
        var baseZ = basePos.blockZ();

        var trunkX = baseX;
        var trunkZ = baseZ;
        var topY = baseY + freeTreeHeight - 1;

        for (var trunkIndex = 0; trunkIndex < freeTreeHeight; trunkIndex++) {
            if (trunkIndex >= bendStart && bendLength > 0) {
                trunkX += direction.stepX;
                trunkZ += direction.stepZ;
                bendLength--;
            }

            var blockY = baseY + trunkIndex;
            var logPos = new BlockVec(trunkX, blockY, trunkZ);
            this.placeLog(getter, logSetter, random, logPos, config);
            this.placeLog(getter, logSetter, random, logPos.add(1, 0, 0), config);
            this.placeLog(getter, logSetter, random, logPos.add(0, 0, 1), config);
            this.placeLog(getter, logSetter, random, logPos.add(1, 0, 1), config);
        }

        foliageAttachments.add(new FoliagePlacer.FoliageAttachment(new BlockVec(trunkX, topY, trunkZ), 0, true));

        for (var offsetX = -1; offsetX <= 2; offsetX++) {
            for (var offsetZ = -1; offsetZ <= 2; offsetZ++) {
                if ((offsetX < 0 || offsetX > 1 || offsetZ < 0 || offsetZ > 1) && random.nextInt(3) <= 0) {
                    var branchHeight = random.nextInt(3) + 2;
                    for (var branchIndex = 0; branchIndex < branchHeight; branchIndex++) {
                        this.placeLog(getter, logSetter, random, new BlockVec(baseX + offsetX, topY - branchIndex - 1, baseZ + offsetZ), config);
                    }
                    foliageAttachments.add(new FoliagePlacer.FoliageAttachment(new BlockVec(baseX + offsetX, topY, baseZ + offsetZ), 0, false));
                }
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

