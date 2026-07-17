package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code BasaltPillarFeature}: a basalt pillar hanging from
 * the ceiling down to the ground, with random side hang-offs and a scattered
 * base apron.
 */
public final class BasaltPillarFeature implements Feature<NoneFeatureConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var origin = context.origin();
        var level = context.accessor();
        var random = context.random();
        if (!level.getBlock(origin).isAir() || level.getBlock(origin.add(0, 1, 0)).isAir()) {
            return false;
        }

        var pos = origin;
        var placeNorthHangoff = true;
        var placeSouthHangoff = true;
        var placeWestHangoff = true;
        var placeEastHangoff = true;

        while (level.getBlock(pos).isAir()) {
            if (pos.blockY() < context.minY() || pos.blockY() > context.maxY()) {
                return true;
            }

            level.setBlock(pos, Block.BASALT);
            // Vanilla short-circuits the && so a stopped side draws no random.
            if (placeNorthHangoff) {
                placeNorthHangoff = this.placeHangOff(level, random, pos.add(0, 0, -1));
            }

            if (placeSouthHangoff) {
                placeSouthHangoff = this.placeHangOff(level, random, pos.add(0, 0, 1));
            }

            if (placeWestHangoff) {
                placeWestHangoff = this.placeHangOff(level, random, pos.add(-1, 0, 0));
            }

            if (placeEastHangoff) {
                placeEastHangoff = this.placeHangOff(level, random, pos.add(1, 0, 0));
            }

            pos = pos.add(0, -1, 0);
        }

        pos = pos.add(0, 1, 0);
        this.placeBaseHangOff(level, random, pos.add(0, 0, -1));
        this.placeBaseHangOff(level, random, pos.add(0, 0, 1));
        this.placeBaseHangOff(level, random, pos.add(-1, 0, 0));
        this.placeBaseHangOff(level, random, pos.add(1, 0, 0));
        pos = pos.add(0, -1, 0);

        for (var dx = -3; dx < 4; dx++) {
            for (var dz = -3; dz < 4; dz++) {
                var probability = Math.abs(dx) * Math.abs(dz);
                if (random.nextInt(10) < 10 - probability) {
                    var basePos = pos.add(dx, 0, dz);
                    var maxDrop = 3;

                    while (level.getBlock(basePos.add(0, -1, 0)).isAir()) {
                        basePos = basePos.add(0, -1, 0);
                        if (--maxDrop <= 0) {
                            break;
                        }
                    }

                    if (!level.getBlock(basePos.add(0, -1, 0)).isAir()) {
                        level.setBlock(basePos, Block.BASALT);
                    }
                }
            }
        }

        return true;
    }

    private <T extends Block.Getter & Block.Setter> void placeBaseHangOff(T level, RandomSource random, BlockVec pos) {
        if (random.nextBoolean()) {
            level.setBlock(pos, Block.BASALT);
        }
    }

    private <T extends Block.Getter & Block.Setter> boolean placeHangOff(T level, RandomSource random, BlockVec pos) {
        if (random.nextInt(10) != 0) {
            level.setBlock(pos, Block.BASALT);
            return true;
        }

        return false;
    }
}
