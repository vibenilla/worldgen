package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SpringConfiguration;

/**
 * Port of vanilla {@code SpringFeature}: places a single fluid source block on
 * cave/cliff faces with the configured number of solid ("rock") and air
 * ("hole") neighbors. Vanilla additionally schedules a fluid tick; block
 * output at generation time is identical without it.
 */
public final class SpringFeature implements Feature<SpringConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SpringConfiguration, T> context) {
        var config = context.config();
        var level = context.accessor();
        var origin = context.origin();
        if (!isValid(config, level.getBlock(origin.add(0, 1, 0)))) {
            return false;
        }

        if (config.requiresBlockBelow() && !isValid(config, level.getBlock(origin.add(0, -1, 0)))) {
            return false;
        }

        var currentState = level.getBlock(origin);
        if (!currentState.isAir() && !isValid(config, currentState)) {
            return false;
        }

        var placed = 0;
        var rockCount = 0;
        if (isValid(config, level.getBlock(origin.add(-1, 0, 0)))) {
            rockCount++;
        }

        if (isValid(config, level.getBlock(origin.add(1, 0, 0)))) {
            rockCount++;
        }

        if (isValid(config, level.getBlock(origin.add(0, 0, -1)))) {
            rockCount++;
        }

        if (isValid(config, level.getBlock(origin.add(0, 0, 1)))) {
            rockCount++;
        }

        if (isValid(config, level.getBlock(origin.add(0, -1, 0)))) {
            rockCount++;
        }

        var holeCount = 0;
        if (level.getBlock(origin.add(-1, 0, 0)).isAir()) {
            holeCount++;
        }

        if (level.getBlock(origin.add(1, 0, 0)).isAir()) {
            holeCount++;
        }

        if (level.getBlock(origin.add(0, 0, -1)).isAir()) {
            holeCount++;
        }

        if (level.getBlock(origin.add(0, 0, 1)).isAir()) {
            holeCount++;
        }

        if (level.getBlock(origin.add(0, -1, 0)).isAir()) {
            holeCount++;
        }

        if (rockCount == config.rockCount() && holeCount == config.holeCount()) {
            level.setBlock(origin, config.state());
            placed++;
        }

        return placed > 0;
    }

    private static boolean isValid(SpringConfiguration config, Block block) {
        return config.validBlocks().contains(block.key());
    }
}
