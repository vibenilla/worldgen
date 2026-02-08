package rocks.minestom.worldgen.feature.foliageplacers;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.TreeConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.RandomSource;

public record CherryFoliagePlacer(
        int radius,
        int offset,
        IntProvider height,
        float wideBottomLayerHoleChance,
        float cornerHoleChance,
        float hangingLeavesChance,
        float hangingLeavesExtensionChance
) implements FoliagePlacer {
    public static final Codec<CherryFoliagePlacer> CODEC = StructCodec.struct(
            "radius", Codec.INT, CherryFoliagePlacer::radius,
            "offset", Codec.INT, CherryFoliagePlacer::offset,
            "height", IntProvider.CODEC, CherryFoliagePlacer::height,
            "wide_bottom_layer_hole_chance", Codec.FLOAT, CherryFoliagePlacer::wideBottomLayerHoleChance,
            "corner_hole_chance", Codec.FLOAT, CherryFoliagePlacer::cornerHoleChance,
            "hanging_leaves_chance", Codec.FLOAT, CherryFoliagePlacer::hangingLeavesChance,
            "hanging_leaves_extension_chance", Codec.FLOAT, CherryFoliagePlacer::hangingLeavesExtensionChance,
            CherryFoliagePlacer::new
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
        var radius = foliageRadius + attachment.radiusOffset() - 1;

        this.placeLeavesRow(getter, foliageSetter, random, config, center, radius - 2, foliageHeight - 3, doubleTrunk);
        this.placeLeavesRow(getter, foliageSetter, random, config, center, radius - 1, foliageHeight - 4, doubleTrunk);

        for (var layerY = foliageHeight - 5; layerY >= 0; layerY--) {
            this.placeLeavesRow(getter, foliageSetter, random, config, center, radius, layerY, doubleTrunk);
        }

        this.placeLeavesRowWithHangingLeavesBelow(getter, foliageSetter, random, config, center, radius, -1, doubleTrunk);
        this.placeLeavesRowWithHangingLeavesBelow(getter, foliageSetter, random, config, center, radius - 1, -2, doubleTrunk);
    }

    @Override
    public int foliageHeight(RandomSource random, int treeHeight, TreeConfiguration config) {
        return this.height.sample(random);
    }

    @Override
    public int foliageRadius(RandomSource random, int baseHeight) {
        return this.radius;
    }

    private void placeLeavesRowWithHangingLeavesBelow(
            Block.Getter getter,
            FoliageSetter setter,
            RandomSource random,
            TreeConfiguration config,
            BlockVec center,
            int radius,
            int yOffset,
            boolean doubleTrunk
    ) {
        this.placeLeavesRow(getter, setter, random, config, center, radius, yOffset, doubleTrunk);

        for (var x = -radius; x <= radius; x++) {
            for (var z = -radius; z <= radius; z++) {
                if (!this.shouldSkipLocationSigned(random, x, yOffset, z, radius)) {
                    var below = center.add(x, yOffset - 1, z);
                    if (random.nextFloat() < this.hangingLeavesChance) {
                        FoliagePlacer.tryPlaceLeaf(getter, setter, random, config, below);
                        if (random.nextFloat() < this.hangingLeavesExtensionChance) {
                            FoliagePlacer.tryPlaceLeaf(getter, setter, random, config, below.sub(0, 1, 0));
                        }
                    }
                }
            }
        }
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

        if (y == -1 && (absX == radius || absZ == radius) && random.nextFloat() < this.wideBottomLayerHoleChance) {
            return true;
        }

        var isCorner = absX == radius && absZ == radius;
        var wide = radius > 2;
        if (wide) {
            return isCorner || absX + absZ > radius * 2 - 2 && random.nextFloat() < this.cornerHoleChance;
        }

        return isCorner && random.nextFloat() < this.cornerHoleChance;
    }
}
