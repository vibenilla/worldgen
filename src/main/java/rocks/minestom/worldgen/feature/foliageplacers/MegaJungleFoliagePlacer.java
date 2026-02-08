package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public record MegaJungleFoliagePlacer(int radius, int offset, int height) implements FoliagePlacer {
    public static final Codec<MegaJungleFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, MegaJungleFoliagePlacer::radius,
            "offset", Codec.INT, MegaJungleFoliagePlacer::offset,
            "height", Codec.INT, MegaJungleFoliagePlacer::height,
            MegaJungleFoliagePlacer::new
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
        var layerCount = attachment.doubleTrunk() ? foliageHeight : 1 + random.nextInt(2);

        for (var layerY = this.offset; layerY >= this.offset - layerCount; layerY--) {
            var layerRadius = foliageRadius + attachment.radiusOffset() + 1 - layerY;
            this.placeLeavesRow(getter, foliageSetter, random, config, attachment.pos(), layerRadius, layerY, attachment.doubleTrunk());
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height;
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
