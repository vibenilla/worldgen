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

public record StraightTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) implements TrunkPlacer {
    public static final Codec<StraightTrunkPlacer> CODEC = StructCodec.struct(
            "base_height", Codec.INT, StraightTrunkPlacer::baseHeight,
            "height_rand_a", Codec.INT, StraightTrunkPlacer::heightRandA,
            "height_rand_b", Codec.INT, StraightTrunkPlacer::heightRandB,
            StraightTrunkPlacer::new);

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

        for (var height = 0; height < freeTreeHeight; height++) {
            this.placeLog(getter, logSetter, random, basePos.add(0, height, 0), config);
        }

        return List.of(new FoliagePlacer.FoliageAttachment(basePos.add(0, freeTreeHeight, 0), 0, false));
    }

    @Override
    public int getTreeHeight(RandomSource random) {
        return this.baseHeight + random.nextInt(this.heightRandA + 1) + random.nextInt(this.heightRandB + 1);
    }
}
