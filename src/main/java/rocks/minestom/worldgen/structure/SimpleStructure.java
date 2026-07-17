package rocks.minestom.worldgen.structure;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

import java.util.List;

/**
 * A structure placed directly from a single NBT template.
 *
 * <p>Simple structures are used for features like igloos, shipwrecks, and ruined portals
 * that don't require jigsaw assembly. A random template is selected from the available
 * options and placed with a random rotation.
 *
 * <p>Unlike {@link JigsawStructure}, simple structures:
 * <ul>
 *   <li>Don't connect multiple pieces
 *   <li>Don't use jigsaw blocks
 *   <li>Are typically smaller and self-contained
 * </ul>
 *
 * @see StructureTemplate for the template format
 */
public record SimpleStructure(Key type, StructureBiomes biomes, List<Key> templates,
        TerrainAdjustment terrainAdaptation) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        // Simple structures are assembled and placed by the StructurePlacer,
        // which selects the template and rotation and clips per chunk.
    }
}
