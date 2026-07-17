package rocks.minestom.worldgen.structure.mansion;

import net.minestom.server.coordinate.BlockVec;
import rocks.minestom.worldgen.structure.template.Mirror;
import rocks.minestom.worldgen.structure.template.Rotation;

/**
 * Port of vanilla {@code WoodlandMansionPieces.WoodlandMansionPiece}: a
 * single NBT template placement (template name, position, rotation, mirror)
 * within a mansion layout.
 */
public record WoodlandMansionPiece(String templateName, BlockVec position, Rotation rotation, Mirror mirror) {
    public WoodlandMansionPiece(String templateName, BlockVec position, Rotation rotation) {
        this(templateName, position, rotation, Mirror.NONE);
    }
}
