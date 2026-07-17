package rocks.minestom.worldgen.structure.processor;

import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A template block as it flows through the processor chain, mirroring
 * vanilla's {@code StructureTemplate.StructureBlockInfo}.
 *
 * <p>The state is the <em>unrotated</em> template state; vanilla applies the
 * piece rotation only when the surviving block is finally placed. The position
 * is the world position (already rotated and offset by the piece origin).
 */
public record StructureBlockInfo(BlockVec pos, Block state, @Nullable CompoundBinaryTag nbt) {
}
