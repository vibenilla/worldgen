package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public record FancyFoliagePlacer(int radius, int offset, int height) implements FoliagePlacer {
    public static final Codec<FancyFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, FancyFoliagePlacer::radius,
            "offset", Codec.INT, FancyFoliagePlacer::offset,
            "height", Codec.INT, FancyFoliagePlacer::height,
            FancyFoliagePlacer::new
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
        for (var layerY = this.offset; layerY >= this.offset - foliageHeight; layerY--) {
            var layerRadius = foliageRadius + (layerY != this.offset && layerY != this.offset - foliageHeight ? 1 : 0);
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
        var deltaX = (float) Math.abs(x) + 0.5F;
        var deltaZ = (float) Math.abs(z) + 0.5F;
        return deltaX * deltaX + deltaZ * deltaZ > (float) (radius * radius);
    }
}
