package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.placement.PlacementContext;

import java.util.Set;

public interface Feature<C extends FeatureConfiguration> {
    <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<C, T> context);

    /**
     * Minimal placement context over the feature accessor, used to evaluate
     * block predicates from feature configurations. Heightmap and biome
     * lookups are unavailable through it; the predicates used by feature
     * configs only need block access and world bounds.
     */
    static PlacementContext predicateContext(Block.Getter accessor, int minY, int maxY) {
        return new PlacementContext(accessor, 0, 0, 0, 0, null, null, minY, maxY, 0, null, null, null);
    }

    /** Vanilla's {@code #minecraft:leaves} block tag (26.2 contents). */
    Set<String> LEAVES = Set.of(
            "minecraft:jungle_leaves", "minecraft:oak_leaves", "minecraft:spruce_leaves",
            "minecraft:pale_oak_leaves", "minecraft:dark_oak_leaves", "minecraft:acacia_leaves",
            "minecraft:birch_leaves", "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves",
            "minecraft:mangrove_leaves", "minecraft:cherry_leaves");

    /**
     * Vanilla {@code TreeFeature.validTreePos}: air or anything in the
     * {@code #minecraft:replaceable_by_trees} block tag (26.2 contents).
     */
    Set<String> REPLACEABLE_BY_TREES = Set.of(
            // #minecraft:leaves
            "minecraft:jungle_leaves", "minecraft:oak_leaves", "minecraft:spruce_leaves",
            "minecraft:pale_oak_leaves", "minecraft:dark_oak_leaves", "minecraft:acacia_leaves",
            "minecraft:birch_leaves", "minecraft:azalea_leaves", "minecraft:flowering_azalea_leaves",
            "minecraft:mangrove_leaves", "minecraft:cherry_leaves",
            // #minecraft:small_flowers
            "minecraft:dandelion", "minecraft:open_eyeblossom", "minecraft:poppy", "minecraft:blue_orchid",
            "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip",
            "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower",
            "minecraft:lily_of_the_valley", "minecraft:wither_rose", "minecraft:torchflower",
            "minecraft:closed_eyeblossom", "minecraft:golden_dandelion",
            // direct entries
            "minecraft:pale_moss_carpet", "minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush",
            "minecraft:vine", "minecraft:glow_lichen", "minecraft:sunflower", "minecraft:lilac",
            "minecraft:rose_bush", "minecraft:peony", "minecraft:tall_grass", "minecraft:large_fern",
            "minecraft:hanging_roots", "minecraft:pitcher_plant", "minecraft:water", "minecraft:seagrass",
            "minecraft:tall_seagrass", "minecraft:bush", "minecraft:firefly_bush", "minecraft:warped_roots",
            "minecraft:nether_sprouts", "minecraft:crimson_roots", "minecraft:leaf_litter",
            "minecraft:short_dry_grass", "minecraft:tall_dry_grass");

    static boolean isValidTreePosition(Block.Getter getter, BlockVec position) {
        var block = getter.getBlock(position);
        return block.isAir() || REPLACEABLE_BY_TREES.contains(block.name());
    }

    static boolean isDirt(Block block) {
        return block.compare(Block.DIRT) || block.compare(Block.GRASS_BLOCK) ||
                block.compare(Block.PODZOL) || block.compare(Block.COARSE_DIRT) ||
                block.compare(Block.MYCELIUM) || block.compare(Block.ROOTED_DIRT);
    }
}
