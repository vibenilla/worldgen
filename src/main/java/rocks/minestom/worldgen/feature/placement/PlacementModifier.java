package rocks.minestom.worldgen.feature.placement;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.List;

public interface PlacementModifier {
    List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position);
}
