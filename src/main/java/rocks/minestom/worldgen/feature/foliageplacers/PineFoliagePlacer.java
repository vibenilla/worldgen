package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public record PineFoliagePlacer(int radius, int offset, int height) implements FoliagePlacer {
    public static final Codec<PineFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, PineFoliagePlacer::radius,
            "offset", Codec.INT, PineFoliagePlacer::offset,
            "height", Codec.INT, PineFoliagePlacer::height,
            PineFoliagePlacer::new);

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
        var currentRadius = 0;

        for (var y = this.offset; y >= this.offset - foliageHeight; y--) {
            this.placeLeavesRow(
                    getter,
                    foliageSetter,
                    random,
                    config,
                    attachment.pos(),
                    currentRadius,
                    y,
                    attachment.doubleTrunk()
            );

            if (currentRadius >= 1 && y == this.offset - foliageHeight + 1) {
                currentRadius--;
            } else if (currentRadius < foliageRadius + attachment.radiusOffset()) {
                currentRadius++;
            }
        }
    }

    @Override
    public int foliageRadius(RandomSource random, int baseHeight) {
        return this.radius + random.nextInt(Math.max(baseHeight + 1, 1));
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height;
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
                if (!this.shouldSkipLocationSigned(random, x, yOffset, z, radius, doubleTrunk)) {
                    var position = new BlockVec(center.blockX() + x, center.blockY() + yOffset, center.blockZ() + z);
                    FoliagePlacer.tryPlaceLeaf(getter, setter, random, config, position);
                }
            }
        }
    }

    private boolean shouldSkipLocationSigned(RandomSource random, int x, int y, int z, int radius, boolean doubleTrunk) {
        var absX = doubleTrunk ? Math.min(Math.abs(x), Math.abs(x - 1)) : Math.abs(x);
        var absZ = doubleTrunk ? Math.min(Math.abs(z), Math.abs(z - 1)) : Math.abs(z);
        return this.shouldSkipLocation(random, absX, y, absZ, radius, doubleTrunk);
    }

    private boolean shouldSkipLocation(RandomSource random, int absX, int y, int absZ, int radius, boolean doubleTrunk) {
        return absX == radius && absZ == radius && radius > 0;
    }
}
