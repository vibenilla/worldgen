package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

public record DarkOakFoliagePlacer(int radius, int offset) implements FoliagePlacer {
    public static final Codec<DarkOakFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, DarkOakFoliagePlacer::radius,
            "offset", Codec.INT, DarkOakFoliagePlacer::offset,
            DarkOakFoliagePlacer::new
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
        var doubleTrunk = attachment.doubleTrunk();

        if (doubleTrunk) {
            this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius + 2, -1, true);
            this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius + 3, 0, true);
            this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius + 2, 1, true);
            if (random.nextBoolean()) {
                this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius, 2, true);
            }
        } else {
            this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius + 2, -1, false);
            this.placeLeavesRow(getter, foliageSetter, random, config, center, foliageRadius + 1, 0, false);
        }
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return 4;
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
        if (y == 0 && doubleTrunk && (x == -radius || x == radius + 1) && (z == -radius || z == radius + 1)) {
            return true;
        }

        var absX = doubleTrunk ? Math.min(Math.abs(x), Math.abs(x - 1)) : Math.abs(x);
        var absZ = doubleTrunk ? Math.min(Math.abs(z), Math.abs(z - 1)) : Math.abs(z);
        return this.shouldSkipLocation(random, absX, y, absZ, radius, doubleTrunk);
    }

    private boolean shouldSkipLocation(RandomSource random, int absX, int y, int absZ, int radius, boolean doubleTrunk) {
        if (y == -1 && !doubleTrunk) {
            return absX == radius && absZ == radius;
        }

        if (y == 1) {
            return absX + absZ > radius * 2 - 2;
        }

        return false;
    }
}
