package rocks.minestom.worldgen.structure.mineshaft;

import net.minestom.server.instance.block.Block;

/**
 * Port of vanilla {@code MineshaftStructure.Type}: the wood palette used by
 * mineshaft pieces.
 */
public enum MineshaftType {
    NORMAL(Block.OAK_LOG, Block.OAK_PLANKS, Block.OAK_FENCE),
    MESA(Block.DARK_OAK_LOG, Block.DARK_OAK_PLANKS, Block.DARK_OAK_FENCE);

    private final Block woodState;
    private final Block planksState;
    private final Block fenceState;

    MineshaftType(Block wood, Block planks, Block fence) {
        this.woodState = wood;
        this.planksState = planks;
        this.fenceState = fence;
    }

    public Block woodState() {
        return this.woodState;
    }

    public Block planksState() {
        return this.planksState;
    }

    public Block fenceState() {
        return this.fenceState;
    }

    public static MineshaftType fromName(String name) {
        return name.equals("mesa") ? MESA : NORMAL;
    }
}
