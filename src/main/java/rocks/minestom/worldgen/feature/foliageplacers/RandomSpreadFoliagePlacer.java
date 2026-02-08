package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

public record RandomSpreadFoliagePlacer(int radius, int offset, IntProvider foliageHeight, int leafPlacementAttempts) implements FoliagePlacer {
    public static final Codec<RandomSpreadFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, RandomSpreadFoliagePlacer::radius,
            "offset", Codec.INT, RandomSpreadFoliagePlacer::offset,
            "foliage_height", IntProvider.CODEC, RandomSpreadFoliagePlacer::foliageHeight,
            "leaf_placement_attempts", Codec.INT, RandomSpreadFoliagePlacer::leafPlacementAttempts,
            RandomSpreadFoliagePlacer::new
    );

    @Override
    public void createFoliage(
            Block.Getter getter,
            FoliageSetter foliageSetter,
            RandomSource random,
            TreeConfiguration config,
            int maxFreeTreeHeight,
            FoliageAttachment attachment,
            int foliageHeight,
            int foliageRadius
    ) {
        var center = attachment.pos().add(0, this.offset, 0);

        for (var attempt = 0; attempt < this.leafPlacementAttempts; attempt++) {
            var offsetX = this.nextSigned(random, foliageRadius);
            var offsetY = this.nextSigned(random, foliageHeight);
            var offsetZ = this.nextSigned(random, foliageRadius);

            var position = center.add(offsetX, offsetY, offsetZ);
            FoliagePlacer.tryPlaceLeaf(getter, foliageSetter, random, config, position);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.foliageHeight.sample(random);
    }

    @Override
    public int foliageRadius(RandomSource random, int baseHeight) {
        return this.radius;
    }

    private int nextSigned(RandomSource random, int bound) {
        if (bound <= 0) {
            return 0;
        }
        return random.nextInt(bound) - random.nextInt(bound);
    }
}
