package rocks.minestom.worldgen.structure;

import net.kyori.adventure.key.Key;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.structure.pool.PoolAliasBinding;
import rocks.minestom.worldgen.structure.template.LiquidSettings;

import java.util.List;

/**
 * A structure assembled from multiple pieces connected via jigsaw blocks,
 * mirroring vanilla's {@code JigsawStructure} configuration.
 *
 * @param startJigsawName       jigsaw name the start piece is anchored on
 *                              (ancient cities), or null
 * @param size                  maximum expansion depth
 * @param startHeight           height provider sampled from the assembly random
 * @param projectToHeightmap    whether the start Y is offset by the world
 *                              surface (villages, outposts, trail ruins)
 * @param maxDistanceFromCenter horizontal/vertical piece distance limit
 * @param poolAliases           pool alias bindings (trial chamber spawners)
 * @param dimensionPaddingBottom min distance of the start piece from the world bottom
 * @param dimensionPaddingTop    min distance of the start piece from the world top
 * @param terrainAdaptation     how the noise terrain molds around the pieces
 */
public record JigsawStructure(
        StructureBiomes biomes,
        Key startPool,
        @Nullable Key startJigsawName,
        int size,
        StartHeight startHeight,
        boolean projectToHeightmap,
        boolean useExpansionHack,
        int maxDistanceFromCenter,
        List<PoolAliasBinding> poolAliases,
        int dimensionPaddingBottom,
        int dimensionPaddingTop,
        LiquidSettings liquidSettings,
        TerrainAdjustment terrainAdaptation
) implements Structure {
    /**
     * Minimal vanilla {@code HeightProvider} covering the forms jigsaw
     * structures use: constant anchors and uniform ranges between absolute
     * anchors. Uniform sampling draws exactly one {@code nextInt}.
     */
    public record StartHeight(int min, int max) {
        public int sample(RandomSource random) {
            if (this.min == this.max) {
                return this.min;
            }
            if (this.min > this.max) {
                return this.min;
            }
            return random.nextInt(this.max - this.min + 1) + this.min;
        }
    }

    @Override
    public void place(StructurePlaceContext context) {
        // Jigsaw structures are assembled and placed by the StructurePlacer,
        // which threads the vanilla sequential assembly random.
    }
}
