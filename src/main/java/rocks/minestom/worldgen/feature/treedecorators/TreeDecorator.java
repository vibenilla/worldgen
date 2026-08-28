package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public interface TreeDecorator {
    void place(Context context);

    /**
     * Vanilla {@code Util.shuffle}: descending Fisher-Yates using
     * {@code random.nextInt(i)}, matching the draw sequence exactly.
     */
    static <T> void shuffle(List<T> list, RandomSource random) {
        for (var index = list.size(); index > 1; index--) {
            list.set(index - 1, list.set(random.nextInt(index), list.get(index - 1)));
        }
    }

    final class Context {
        private final Block.Getter level;
        private final BiConsumer<BlockVec, Block> decorationSetter;
        private final RandomSource random;
        private final List<BlockVec> logs;
        private final List<BlockVec> leaves;
        private final List<BlockVec> roots;

        /**
         * Log/leaf/root collections must iterate in vanilla's hash order (see
         * {@code VanillaPos}); the stable Y sort below then reproduces
         * vanilla's list ordering exactly.
         */
        public Context(
                Block.Getter level,
                BiConsumer<BlockVec, Block> decorationSetter,
                RandomSource random,
                List<BlockVec> logs,
                List<BlockVec> leaves,
                List<BlockVec> roots
        ) {
            this.level = level;
            this.decorationSetter = decorationSetter;
            this.random = random;
            this.logs = new ArrayList<>(logs);
            this.leaves = new ArrayList<>(leaves);
            this.roots = new ArrayList<>(roots);
            this.logs.sort(Comparator.comparingInt(BlockVec::blockY));
            this.leaves.sort(Comparator.comparingInt(BlockVec::blockY));
            this.roots.sort(Comparator.comparingInt(BlockVec::blockY));
        }

        public void placeVine(BlockVec position, String directionProperty) {
            this.setBlock(position, Block.VINE.withProperty(directionProperty, "true"));
        }

        public void setBlock(BlockVec position, Block block) {
            this.decorationSetter.accept(position, block);
        }

        public boolean isAir(BlockVec position) {
            return this.level.getBlock(position).air();
        }

        public boolean checkBlock(BlockVec position, Predicate<Block> predicate) {
            return predicate.test(this.level.getBlock(position));
        }

        public Block.Getter level() {
            return this.level;
        }

        public RandomSource random() {
            return this.random;
        }

        public List<BlockVec> logs() {
            return this.logs;
        }

        public List<BlockVec> leaves() {
            return this.leaves;
        }

        public List<BlockVec> roots() {
            return this.roots;
        }
    }
}
