package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

public record MegaPineFoliagePlacer(int radius, int offset, IntProvider crownHeight) implements FoliagePlacer {
    public static final Codec<MegaPineFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, MegaPineFoliagePlacer::radius,
            "offset", Codec.INT, MegaPineFoliagePlacer::offset,
            "crown_height", IntProvider.CODEC, MegaPineFoliagePlacer::crownHeight,
            MegaPineFoliagePlacer::new
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
        var center = attachment.pos();
        var lastRadius = 0;

        for (var blockY = center.blockY() - foliageHeight + this.offset; blockY <= center.blockY() + this.offset; blockY++) {
            var yDistanceFromTop = center.blockY() - blockY;
            var dynamicRadius = foliageRadius + attachment.radiusOffset() + (int) Math.floor((float) yDistanceFromTop / (float) foliageHeight * 3.5F);

            int layerRadius;
            if (yDistanceFromTop > 0 && dynamicRadius == lastRadius && (blockY & 1) == 0) {
                layerRadius = dynamicRadius + 1;
            } else {
                layerRadius = dynamicRadius;
            }

            this.placeLeavesRow(
                    getter,
                    foliageSetter,
                    random,
                    config,
                    new BlockVec(center.blockX(), blockY, center.blockZ()),
                    layerRadius,
                    0,
                    attachment.doubleTrunk()
            );

            lastRadius = dynamicRadius;
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.crownHeight.sample(random);
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
                if (!this.shouldSkipLocationSigned(x, z, radius)) {
                    var position = new BlockVec(center.blockX() + x, center.blockY() + yOffset, center.blockZ() + z);
                    FoliagePlacer.tryPlaceLeaf(getter, setter, random, config, position);
                }
            }
        }
    }

    private boolean shouldSkipLocationSigned(int x, int z, int radius) {
        var absX = Math.abs(x);
        var absZ = Math.abs(z);
        return absX + absZ >= 7 || absX * absX + absZ * absZ > radius * radius;
    }
}
