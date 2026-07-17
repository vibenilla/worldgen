package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;

import java.util.Iterator;
import java.util.NoSuchElementException;

/** Vanilla {@code BlockPos} iteration helpers whose order leaks into generation. */
final class BlockPosIterators {
    private BlockPosIterators() {
    }

    /**
     * Vanilla {@code BlockPos.withinManhattan}: all positions within the given
     * per-axis reach, ordered by increasing Manhattan distance; within a
     * distance shell x varies slowest, then y, and each +z position is
     * immediately followed by its -z mirror.
     */
    static Iterable<BlockVec> withinManhattan(BlockVec origin, int reachX, int reachY, int reachZ) {
        var maxDepth = reachX + reachY + reachZ;
        var originX = origin.blockX();
        var originY = origin.blockY();
        var originZ = origin.blockZ();
        return () -> new Iterator<>() {
            private int currentDepth;
            private int maxX;
            private int maxY;
            private int x;
            private int y;
            private boolean zMirror;
            private int cursorX;
            private int cursorY;
            private int cursorZ;
            private boolean done;
            private BlockVec next = this.compute();

            @Override
            public boolean hasNext() {
                return this.next != null;
            }

            @Override
            public BlockVec next() {
                var result = this.next;
                if (result == null) {
                    throw new NoSuchElementException();
                }

                this.next = this.compute();
                return result;
            }

            private BlockVec compute() {
                if (this.done) {
                    return null;
                }

                if (this.zMirror) {
                    this.zMirror = false;
                    this.cursorZ = originZ - (this.cursorZ - originZ);
                    return new BlockVec(this.cursorX, this.cursorY, this.cursorZ);
                }

                while (true) {
                    if (this.y > this.maxY) {
                        this.x++;
                        if (this.x > this.maxX) {
                            this.currentDepth++;
                            if (this.currentDepth > maxDepth) {
                                this.done = true;
                                return null;
                            }

                            this.maxX = Math.min(reachX, this.currentDepth);
                            this.x = -this.maxX;
                        }

                        this.maxY = Math.min(reachY, this.currentDepth - Math.abs(this.x));
                        this.y = -this.maxY;
                    }

                    var xx = this.x;
                    var yy = this.y;
                    var zz = this.currentDepth - Math.abs(xx) - Math.abs(yy);
                    this.y++;
                    if (zz <= reachZ) {
                        this.zMirror = zz != 0;
                        this.cursorX = originX + xx;
                        this.cursorY = originY + yy;
                        this.cursorZ = originZ + zz;
                        return new BlockVec(this.cursorX, this.cursorY, this.cursorZ);
                    }
                }
            }
        };
    }

    static int distManhattan(BlockVec a, BlockVec b) {
        return Math.abs(a.blockX() - b.blockX())
                + Math.abs(a.blockY() - b.blockY())
                + Math.abs(a.blockZ() - b.blockZ());
    }
}
