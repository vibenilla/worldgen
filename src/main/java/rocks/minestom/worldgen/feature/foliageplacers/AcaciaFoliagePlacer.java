package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public record AcaciaFoliagePlacer(int radius, int offset) implements FoliagePlacer {
    public static final Codec<AcaciaFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, AcaciaFoliagePlacer::radius,
            "offset", Codec.INT, AcaciaFoliagePlacer::offset,
            AcaciaFoliagePlacer::new
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
        var doubleTrunk = attachment.doubleTrunk();
        var center = attachment.pos().add(0, this.offset, 0);

        this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius + attachment.radiusOffset(), -1 - foliageHeight, doubleTrunk);
        this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius - 1, -foliageHeight, doubleTrunk);
        this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius + attachment.radiusOffset() - 1, 0, doubleTrunk);
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return 0;
    }

    @Override
    public int foliageRadius(RandomSource random, int baseHeight) {
        return this.radius;
    }

    private void placeLeavesRow(
            Block.Getter getter,
            FoliageSetter setter,
            RandomSource random,
            TreeConfiguration config,
            BlockVec center,
            int radius,
            int yOffset,
            boolean doubleTrunk
    ) {
        var widthOffset = doubleTrunk ? 1 : 0;

        for (var x = -radius; x <= radius + widthOffset; x++) {
            for (var z = -radius; z <= radius + widthOffset; z++) {
                if (!this.shouldSkipLocationSigned(random, x, yOffset, z, radius)) {
                    var position = new BlockVec(center.blockX() + x, center.blockY() + yOffset, center.blockZ() + z);
                    FoliagePlacer.tryPlaceLeaf(getter, setter, random, config, position);
                }
            }
        }
    }

    private boolean shouldSkipLocationSigned(RandomSource random, int x, int y, int z, int radius) {
        var absX = Math.abs(x);
        var absZ = Math.abs(z);

        if (y == 0) {
            return (absX > 1 || absZ > 1) && absX != 0 && absZ != 0;
        }

        return absX == radius && absZ == radius && radius > 0;
    }
}
