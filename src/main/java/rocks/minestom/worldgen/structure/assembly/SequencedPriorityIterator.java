package rocks.minestom.worldgen.structure.assembly;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Port of vanilla's {@code SequencedPriorityIterator}: yields items from the
 * highest priority first, FIFO within a priority, and supports interleaved
 * adds while iterating (26.2 placement priorities).
 */
final class SequencedPriorityIterator<T> {
    private static final int MIN_PRIO = Integer.MIN_VALUE;

    private final Map<Integer, Deque<T>> queuesByPriority = new HashMap<>();
    private Deque<T> highestPrioQueue;
    private int highestPrio = MIN_PRIO;

    void add(T item, int priority) {
        if (priority == this.highestPrio && this.highestPrioQueue != null) {
            this.highestPrioQueue.addLast(item);
            return;
        }

        var queue = this.queuesByPriority.computeIfAbsent(priority, unused -> new ArrayDeque<>());
        queue.addLast(item);
        if (priority >= this.highestPrio) {
            this.highestPrioQueue = queue;
            this.highestPrio = priority;
        }
    }

    boolean hasNext() {
        return this.highestPrioQueue != null && !this.highestPrioQueue.isEmpty();
    }

    T next() {
        var item = this.highestPrioQueue.removeFirst();
        if (this.highestPrioQueue.isEmpty()) {
            this.switchToNextHighestPrioQueue();
        }
        return item;
    }

    private void switchToNextHighestPrioQueue() {
        var bestPrio = MIN_PRIO;
        Deque<T> best = null;
        for (var entry : this.queuesByPriority.entrySet()) {
            if (entry.getKey() > bestPrio && !entry.getValue().isEmpty()) {
                bestPrio = entry.getKey();
                best = entry.getValue();
            }
        }

        this.highestPrioQueue = best;
        this.highestPrio = best != null ? bestPrio : MIN_PRIO;
    }
}
