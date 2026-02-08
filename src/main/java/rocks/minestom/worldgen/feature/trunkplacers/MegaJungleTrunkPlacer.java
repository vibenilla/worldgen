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

public record MegaJungleTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) implements TrunkPlacer {
    public static final Codec<MegaJungleTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, MegaJungleTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, MegaJungleTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, MegaJungleTrunkPlacer::heightRandB,
            MegaJungleTrunkPlacer::new
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
        foliageAttachments.addAll(new GiantTrunkPlacer(this.baseHeight, this.heightRandA, this.heightRandB)
                .placeTrunk(getter, logSetter, random, freeTreeHeight, basePos, config));

        for (var branchBase = freeTreeHeight - 2 - random.nextInt(4); branchBase > freeTreeHeight / 2; branchBase -= 2 + random.nextInt(4)) {
            var angle = random.nextFloat() * (float) (Math.PI * 2.0D);
            var offsetX = 0;
            var offsetZ = 0;

            for (var branchStep = 0; branchStep < 5; branchStep++) {
                offsetX = (int) (1.5F + Math.cos(angle) * (float) branchStep);
                offsetZ = (int) (1.5F + Math.sin(angle) * (float) branchStep);
                var logPos = basePos.add(offsetX, branchBase - 3 + branchStep / 2, offsetZ);
                this.placeLog(getter, logSetter, random, logPos, config);
            }

            foliageAttachments.add(new FoliagePlacer.FoliageAttachment(basePos.add(offsetX, branchBase, offsetZ), -2, false));
        }

        return foliageAttachments;
    }

    @Override
    public int getTreeHeight(RandomSource random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }
}

