package rocks.minestom.worldgen.structure;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.context.BiomeTagManager;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.structure.placement.StructurePlacer;

import java.util.List;

/**
 * Represents a structure that can be placed in the world during generation.
 *
 * <p>Structures are large, pre-designed features like villages, temples, or shipwrecks.
 * Each structure defines which biomes it can spawn in and how to place itself.
 *
 * <p>There are two main types of structures:
 * <ul>
 *   <li>{@link JigsawStructure} - Complex structures assembled from multiple pieces using jigsaw blocks
 *   <li>{@link SimpleStructure} - Single-template structures placed directly from NBT files
 * </ul>
 *
 * @see StructureSet for grouping structures with placement rules
 * @see StructurePlacer for the placement algorithm
 */
public interface Structure {
    /**
     * Returns the biome constraints for this structure.
     */
    StructureBiomes biomes();

    /**
     * Places this structure at the location specified in the context.
     */
    void place(StructurePlaceContext context);

    /**
     * Defines which biomes a structure can generate in, either by tag or explicit list.
     *
     * @param tag    a biome tag (e.g., "has_structure/village_plains"), or null
     * @param biomes an explicit list of biome keys, or null
     */
    record StructureBiomes(Key tag, List<Key> biomes) {
        /**
         * Checks if this structure can generate in the given biome.
         */
        public boolean matches(Key biomeKey, BiomeTagManager biomeTags) {
            if (this.tag != null) {
                return biomeTags.biomes(this.tag).contains(biomeKey);
            }
            return this.biomes != null && this.biomes.contains(biomeKey);
        }
    }
}
