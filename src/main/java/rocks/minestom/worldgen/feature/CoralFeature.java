package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.feature.treedecorators.TreeDecorator;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Port of vanilla's abstract {@code CoralFeature}: picks a random coral block
 * from the {@code #minecraft:coral_blocks} block tag and hands it to the
 * concrete tree, claw, or mushroom shape. {@link #placeCoralBlock} is the
 * shared per-position placement used by every shape: it writes the coral
 * block, may grow a coral plant or sea pickle above it, and may attach coral
 * wall fans on the horizontal neighbors.
 *
 * <p>The 26.2 block tags below are hardcoded in registration order because
 * vanilla's {@code getRandomElementOf} draws a random index into the tag's
 * backing list, so the draw sequence only matches vanilla if the list order
 * matches vanilla's tag order exactly. That order is: the tag file's
 * {@code values} array, with nested tag references expanded in place in
 * their own file order and duplicates removed by first occurrence (vanilla's
 * {@code TagLoader}, which folds entries into a {@code LinkedHashSet}). The
 * {@code #minecraft:coral_blocks} and {@code #minecraft:wall_corals} tags
 * list their five blocks directly; {@code #minecraft:corals} lists
 * {@code #minecraft:coral_plants} (five non-wall coral plants) first,
 * followed by the five non-wall coral fans.
 */
public abstract class CoralFeature implements Feature<NoneFeatureConfiguration> {
    public static final List<Block> CORAL_BLOCKS = List.of(
            Block.TUBE_CORAL_BLOCK, Block.BRAIN_CORAL_BLOCK, Block.BUBBLE_CORAL_BLOCK,
            Block.FIRE_CORAL_BLOCK, Block.HORN_CORAL_BLOCK);

    public static final List<Block> CORALS = List.of(
            Block.TUBE_CORAL, Block.BRAIN_CORAL, Block.BUBBLE_CORAL, Block.FIRE_CORAL, Block.HORN_CORAL,
            Block.TUBE_CORAL_FAN, Block.BRAIN_CORAL_FAN, Block.BUBBLE_CORAL_FAN,
            Block.FIRE_CORAL_FAN, Block.HORN_CORAL_FAN);

    public static final List<Block> WALL_CORALS = List.of(
            Block.TUBE_CORAL_WALL_FAN, Block.BRAIN_CORAL_WALL_FAN, Block.BUBBLE_CORAL_WALL_FAN,
            Block.FIRE_CORAL_WALL_FAN, Block.HORN_CORAL_WALL_FAN);

    private static final Set<String> CORAL_NAMES =
            CORALS.stream().map(Block::name).collect(Collectors.toUnmodifiableSet());

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var random = context.random();
        var level = context.accessor();
        var origin = context.origin();
        var state = CORAL_BLOCKS.get(random.nextInt(CORAL_BLOCKS.size()));
        return this.placeFeature(level, random, origin, state);
    }

    protected abstract <T extends Block.Getter & Block.Setter> boolean placeFeature(
            T level, RandomSource random, BlockVec origin, Block state);

    protected static Direction randomHorizontalDirection(RandomSource random) {
        return Direction.HORIZONTAL.get(random.nextInt(Direction.HORIZONTAL.size()));
    }

    protected static List<Direction> shuffledHorizontalDirections(RandomSource random) {
        var directions = new ArrayList<>(Direction.HORIZONTAL);
        TreeDecorator.shuffle(directions, random);
        return directions;
    }

    protected <T extends Block.Getter & Block.Setter> boolean placeCoralBlock(
            T level, RandomSource random, BlockVec position, Block state) {
        var above = position.add(0, 1, 0);
        var current = level.getBlock(position);
        if ((current.compare(Block.WATER) || CORAL_NAMES.contains(current.name()))
                && level.getBlock(above).compare(Block.WATER)) {
            level.setBlock(position, state);
            if (random.nextFloat() < 0.25F) {
                level.setBlock(above, CORALS.get(random.nextInt(CORALS.size())));
            } else if (random.nextFloat() < 0.05F) {
                level.setBlock(above, Block.SEA_PICKLE.withProperty("pickles", String.valueOf(random.nextInt(4) + 1)));
            }

            for (var direction : Direction.HORIZONTAL) {
                if (random.nextFloat() < 0.2F) {
                    var relative = direction.relative(position);
                    if (level.getBlock(relative).compare(Block.WATER)) {
                        var coralFan = WALL_CORALS.get(random.nextInt(WALL_CORALS.size()));
                        level.setBlock(relative, coralFan.withProperty("facing", direction.serializedName()));
                    }
                }
            }

            return true;
        }

        return false;
    }
}
