package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public record SpruceFoliagePlacer(int radius, int offset, int trunkHeight) implements FoliagePlacer {

    public static final Codec<SpruceFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, SpruceFoliagePlacer::radius,
            "offset", Codec.INT, SpruceFoliagePlacer::offset,
            "trunk_height", Codec.INT, SpruceFoliagePlacer::trunkHeight,
            SpruceFoliagePlacer::new
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
        var currentRadius = random.nextInt(2);
        var maxRadius = 1;
        var minRadius = 0;

        for (var y = this.offset; y >= this.offset - foliageHeight; y--) {
            this.placeLeavesRow(
                    getter,
                    foliageSetter,
                    random,
                    config,
                    center,
                    currentRadius,
                    y,
                    attachment.doubleTrunk()
            );

            if (currentRadius >= maxRadius) {
                currentRadius = minRadius;
                minRadius = 1;
                maxRadius = Math.min(maxRadius + 1, foliageRadius + attachment.radiusOffset());
            } else {
                currentRadius++;
            }
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return Math.max(4, treeHeight - this.trunkHeight);
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
