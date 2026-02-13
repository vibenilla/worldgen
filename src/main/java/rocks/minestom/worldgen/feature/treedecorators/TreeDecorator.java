package rocks.minestom.worldgen.feature.treedecorators;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

public interface TreeDecorator {
    void place(Context context);

    final class Context {
        private final Block.Getter level;
        private final BiConsumer<BlockVec, Block> decorationSetter;
        private final RandomSource random;
        private final List<BlockVec> logs;
        private final List<BlockVec> leaves;
        private final List<BlockVec> roots;

        public Context(
                Block.Getter level,
                BiConsumer<BlockVec, Block> decorationSetter,
                RandomSource random,
                Set<BlockVec> roots,
                Set<BlockVec> logs,
                Set<BlockVec> leaves
        ) {
            this.level = level;
            this.decorationSetter = decorationSetter;
            this.random = random;
            this.roots = new ArrayList<>(roots);
            this.logs = new ArrayList<>(logs);
            this.leaves = new ArrayList<>(leaves);
            this.logs.sort(Comparator.comparingInt(BlockVec::blockY));
            this.leaves.sort(Comparator.comparingInt(BlockVec::blockY));
            this.roots.sort(Comparator.comparingInt(BlockVec::blockY));
        }

        public void setBlock(BlockVec position, Block block) {
            this.decorationSetter.accept(position, block);
        }

        public boolean isAir(BlockVec position) {
            return this.level.getBlock(position).isAir();
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
