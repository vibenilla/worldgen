package rocks.minestom.worldgen.feature.trunkplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.foliageplacers.FoliagePlacer;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;
import java.util.function.BiConsumer;

public record GiantTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) implements TrunkPlacer {
    public static final Codec<GiantTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, GiantTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, GiantTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, GiantTrunkPlacer::heightRandB,
            GiantTrunkPlacer::new
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
        var below = basePos.sub(0, 1, 0);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below, config);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below.add(1, 0, 0), config);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below.add(0, 0, 1), config);
        TrunkPlacer.setDirtAt(getter, logSetter, random, below.add(1, 0, 1), config);

        for (var height = 0; height < freeTreeHeight; height++) {
            this.placeLog(getter, logSetter, random, basePos.add(0, height, 0), config);
            if (height < freeTreeHeight - 1) {
                this.placeLog(getter, logSetter, random, basePos.add(1, height, 0), config);
                this.placeLog(getter, logSetter, random, basePos.add(1, height, 1), config);
                this.placeLog(getter, logSetter, random, basePos.add(0, height, 1), config);
            }
        }

        return List.of(new FoliagePlacer.FoliageAttachment(basePos.add(0, freeTreeHeight, 0), 0, true));
    }

    @Override
    public int getTreeHeight(RandomSource random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }
}

