package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.CountConfiguration;

/**
 * Port of vanilla {@code SeaPickleFeature}: scatters sea pickle clusters over
 * the ocean floor around the origin.
 */
public final class SeaPickleFeature implements Feature<CountConfiguration> {

    /** Level types that can answer OCEAN_FLOOR queries in tests. */
    public interface OceanFloor {
        int oceanFloorHeight(int x, int z);
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<CountConfiguration, T> context) {
        var placed = 0;
        var random = context.random();
        var level = context.accessor();
        var origin = context.origin();
        var count = context.config().count().sample(random);

        for (var index = 0; index < count; index++) {
            var offsetX = random.nextInt(8) - random.nextInt(8);
            var offsetZ = random.nextInt(8) - random.nextInt(8);
            var x = origin.blockX() + offsetX;
            var z = origin.blockZ() + offsetZ;
            var y = oceanFloorHeight(level, x, z);
            var picklePosition = new BlockVec(x, y, z);
            var pickleState = Block.SEA_PICKLE.withProperty("pickles", String.valueOf(random.nextInt(4) + 1));

            if (level.getBlock(picklePosition).compare(Block.WATER) && canSurvive(level, picklePosition)) {
                level.setBlock(picklePosition, pickleState);
                placed++;
            }
        }

        return placed > 0;
    }

    private static <T extends Block.Getter> int oceanFloorHeight(T level, int x, int z) {
        if (level instanceof GenerationUnitAdapter adapter) {
            return adapter.getHeight(x, z);
        }

        if (level instanceof OceanFloor oceanFloor) {
            return oceanFloor.oceanFloorHeight(x, z);
        }

        return 0;
    }

    private static <T extends Block.Getter> boolean canSurvive(T level, BlockVec position) {
        var below = level.getBlock(position.sub(0, 1, 0));
        return below.isSolid();
    }
}
