package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

import java.util.List;

/**
 * Port of vanilla {@code DesertWellFeature}: a sandstone well built above the
 * first sand block found below the origin, with two suspicious sand blocks
 * scattered around the water column. Brushable-block loot table assignment is
 * out of scope for this library; the suspicious sand blocks are placed with
 * no attached loot.
 */
public final class DesertWellFeature implements Feature<NoneFeatureConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var level = context.accessor();
        var random = context.random();
        var origin = context.origin().add(0, 1, 0);

        while (level.getBlock(origin).air() && origin.blockY() > context.minY() + 2) {
            origin = origin.sub(0, 1, 0);
        }

        if (!level.getBlock(origin).compare(Block.SAND)) {
            return false;
        }

        for (var offsetX = -2; offsetX <= 2; offsetX++) {
            for (var offsetZ = -2; offsetZ <= 2; offsetZ++) {
                if (level.getBlock(origin.add(offsetX, -1, offsetZ)).air()
                        && level.getBlock(origin.add(offsetX, -2, offsetZ)).air()) {
                    return false;
                }
            }
        }

        for (var offsetY = -2; offsetY <= 0; offsetY++) {
            for (var offsetX = -2; offsetX <= 2; offsetX++) {
                for (var offsetZ = -2; offsetZ <= 2; offsetZ++) {
                    level.setBlock(origin.add(offsetX, offsetY, offsetZ), Block.SANDSTONE);
                }
            }
        }

        level.setBlock(origin, Block.WATER);

        for (var direction : Direction.HORIZONTAL) {
            level.setBlock(direction.relative(origin), Block.WATER);
        }

        var sandCenter = origin.sub(0, 1, 0);
        level.setBlock(sandCenter, Block.SAND);

        for (var direction : Direction.HORIZONTAL) {
            level.setBlock(direction.relative(sandCenter), Block.SAND);
        }

        for (var offsetX = -2; offsetX <= 2; offsetX++) {
            for (var offsetZ = -2; offsetZ <= 2; offsetZ++) {
                if (offsetX == -2 || offsetX == 2 || offsetZ == -2 || offsetZ == 2) {
                    level.setBlock(origin.add(offsetX, 1, offsetZ), Block.SANDSTONE);
                }
            }
        }

        level.setBlock(origin.add(2, 1, 0), Block.SANDSTONE_SLAB);
        level.setBlock(origin.add(-2, 1, 0), Block.SANDSTONE_SLAB);
        level.setBlock(origin.add(0, 1, 2), Block.SANDSTONE_SLAB);
        level.setBlock(origin.add(0, 1, -2), Block.SANDSTONE_SLAB);

        for (var offsetX = -1; offsetX <= 1; offsetX++) {
            for (var offsetZ = -1; offsetZ <= 1; offsetZ++) {
                if (offsetX == 0 && offsetZ == 0) {
                    level.setBlock(origin.add(offsetX, 4, offsetZ), Block.SANDSTONE);
                } else {
                    level.setBlock(origin.add(offsetX, 4, offsetZ), Block.SANDSTONE_SLAB);
                }
            }
        }

        for (var offsetY = 1; offsetY <= 3; offsetY++) {
            level.setBlock(origin.add(-1, offsetY, -1), Block.SANDSTONE);
            level.setBlock(origin.add(-1, offsetY, 1), Block.SANDSTONE);
            level.setBlock(origin.add(1, offsetY, -1), Block.SANDSTONE);
            level.setBlock(origin.add(1, offsetY, 1), Block.SANDSTONE);
        }

        var waterPositions = List.of(origin, Direction.EAST.relative(origin), Direction.SOUTH.relative(origin),
                Direction.WEST.relative(origin), Direction.NORTH.relative(origin));
        placeSuspiciousSand(level, waterPositions.get(random.nextInt(waterPositions.size())).sub(0, 1, 0));
        placeSuspiciousSand(level, waterPositions.get(random.nextInt(waterPositions.size())).sub(0, 2, 0));
        return true;
    }

    private static <T extends Block.Getter & Block.Setter> void placeSuspiciousSand(T level, BlockVec position) {
        level.setBlock(position, Block.SUSPICIOUS_SAND);
    }
}
