package rocks.minestom.worldgen.structure;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.GenerationUnitAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bridges structure-phase block writes into the feature phase.
 *
 * <p>Structures place blocks before the chunk's decoration runs, but the
 * decoration reads the memoized terrain buffer which never saw those writes -
 * vanilla ores would then "repair" mineshaft corridors. Writes are recorded
 * here against the chunk's surface-height array (the shared identity handle
 * both phases receive) and replayed into the terrain-backed accessor when the
 * feature phase begins, mirroring vanilla where features read the proto-chunk
 * that already contains the structure blocks.
 */
public final class StructureWrites {
    private static final Map<int[], List<Write>> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile GenerationUnitAdapter.TerrainLookup terrainLookup;

    private StructureWrites() {
    }

    /**
     * Remembers the most recent terrain lookup seen by the feature phase, so
     * structure placement can read the same live chunk buffers (terrain +
     * surface + carvers + margin writes from previously decorated neighbors).
     * Consumers must validate the lookup against their own chunk handle since
     * multiple generators may share this slot.
     */
    public static void captureTerrainLookup(GenerationUnitAdapter.TerrainLookup lookup) {
        if (lookup != null) {
            terrainLookup = lookup;
        }
    }

    public static GenerationUnitAdapter.TerrainLookup terrainLookup() {
        return terrainLookup;
    }

    public record Write(int x, int y, int z, Block block) {
    }

    public static void record(int[] chunkHandle, int x, int y, int z, Block block) {
        if (chunkHandle == null) {
            return;
        }
        PENDING.computeIfAbsent(chunkHandle, unused -> Collections.synchronizedList(new ArrayList<>()))
                .add(new Write(x, y, z, block));
    }

    /**
     * Replays the chunk's recorded structure writes into the feature-phase
     * accessor (terrain buffer + generation unit) and clears them.
     */
    public static void replay(int[] chunkHandle, Block.Getter accessor) {
        if (chunkHandle == null || !(accessor instanceof GenerationUnitAdapter adapter)) {
            return;
        }

        var writes = PENDING.remove(chunkHandle);
        if (writes == null) {
            return;
        }

        synchronized (writes) {
            for (var write : writes) {
                adapter.setBlock(write.x(), write.y(), write.z(), write.block());
            }
        }
    }
}
