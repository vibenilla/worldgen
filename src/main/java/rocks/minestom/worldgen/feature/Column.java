package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Port of vanilla's {@code net.minecraft.world.level.levelgen.Column}, reduced
 * to the floor/ceiling scan used by speleothem clusters.
 */
public record Column(OptionalInt floor, OptionalInt ceiling) {

    /**
     * Number of inside-column blocks between floor and ceiling, mirroring
     * vanilla's {@code Column.Range#getHeight()}.
     */
    public OptionalInt height() {
        if (this.floor.isPresent() && this.ceiling.isPresent()) {
            return OptionalInt.of(this.ceiling.getAsInt() - this.floor.getAsInt() - 1);
        }

        return OptionalInt.empty();
    }

    public Column withFloor(OptionalInt floor) {
        return new Column(floor, this.ceiling);
    }

    public static Optional<Column> scan(
            Block.Getter level,
            BlockVec position,
            int searchRange,
            Predicate<Block> insideColumn,
            Predicate<Block> validEdge
    ) {
        if (!insideColumn.test(level.getBlock(position))) {
            return Optional.empty();
        }

        var startY = position.blockY();
        var ceiling = scanDirection(level, position, searchRange, insideColumn, validEdge, startY, 1);
        var floor = scanDirection(level, position, searchRange, insideColumn, validEdge, startY, -1);
        return Optional.of(new Column(floor, ceiling));
    }

    private static OptionalInt scanDirection(
            Block.Getter level,
            BlockVec position,
            int searchRange,
            Predicate<Block> insideColumn,
            Predicate<Block> validEdge,
            int startY,
            int step
    ) {
        var y = startY;
        for (var index = 1; index < searchRange && insideColumn.test(level.getBlock(position.blockX(), y, position.blockZ())); index++) {
            y += step;
        }

        if (validEdge.test(level.getBlock(position.blockX(), y, position.blockZ()))) {
            return OptionalInt.of(y);
        }

        return OptionalInt.empty();
    }
}
