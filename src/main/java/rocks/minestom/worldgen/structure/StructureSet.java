package rocks.minestom.worldgen.structure;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import rocks.minestom.worldgen.structure.placement.StructurePlacer;

import java.util.List;

/**
 * Groups one or more structures with shared placement rules.
 *
 * <p>Structure sets define both which structures can generate and where they appear.
 * When multiple structures are in a set, one is randomly selected based on weight
 * for each valid placement location.
 *
 * <p>Examples:
 * <ul>
 *   <li>"villages" set - contains all village variants, placed with random spread
 *   <li>"nether_complexes" set - contains fortress and bastion, sharing placement grid
 * </ul>
 *
 * @see StructurePlacement for the placement algorithm
 * @see StructurePlacer for how sets are processed during generation
 */
public record StructureSet(List<StructureSet.StructureSelection> structures, StructurePlacement placement) {

    /**
     * A structure entry with its selection weight.
     */
    public record StructureSelection(Key structure, int weight) {
        public static final Codec<StructureSelection> CODEC = StructCodec.struct(
                "structure", Codec.KEY, StructureSelection::structure,
                "weight", Codec.INT, StructureSelection::weight,
                StructureSelection::new
        );
    }
}
