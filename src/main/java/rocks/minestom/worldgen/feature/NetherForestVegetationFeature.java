package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NetherForestVegetationConfig;

import java.util.Set;

/**
 * Port of vanilla {@code NetherForestVegetationFeature}: scatters nether
 * ground vegetation (roots, fungi, sprouts) around an origin standing on
 * nylium.
 */
public final class NetherForestVegetationFeature implements Feature<NetherForestVegetationConfig> {

    /** Vanilla's {@code #minecraft:nylium} block tag (26.2 contents). */
    private static final Set<String> NYLIUM = Set.of("minecraft:crimson_nylium", "minecraft:warped_nylium");

    /**
     * Union of vanilla 26.2 {@code #minecraft:supports_crimson_fungus},
     * {@code #supports_warped_fungus}, {@code #supports_crimson_roots} and
     * {@code #supports_warped_roots}: all nether ground vegetation resolves to
     * the same support set, since the fungus tags only add mycelium which is
     * already inside {@code #supports_vegetation} (via {@code #grass_blocks}).
     */
    private static final Set<String> SUPPORTS_NETHER_VEGETATION = Set.of(
            // #minecraft:supports_vegetation (#substrate_overworld + farmland)
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots",
            "minecraft:moss_block", "minecraft:pale_moss_block",
            "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
            "minecraft:farmland",
            // #minecraft:nylium + soul soil
            "minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:soul_soil");

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NetherForestVegetationConfig, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var config = context.config();
        var random = context.random();

        var belowState = level.getBlock(origin.sub(0, 1, 0));
        if (!NYLIUM.contains(belowState.name())) {
            return false;
        }

        var y = origin.blockY();
        if (y < context.minY() + 1 || y + 1 > context.maxY()) {
            return false;
        }

        var placed = 0;
        for (var i = 0; i < config.spreadWidth() * config.spreadWidth(); i++) {
            var finalPos = origin.add(
                    random.nextInt(config.spreadWidth()) - random.nextInt(config.spreadWidth()),
                    random.nextInt(config.spreadHeight()) - random.nextInt(config.spreadHeight()),
                    random.nextInt(config.spreadWidth()) - random.nextInt(config.spreadWidth()));
            var state = config.stateProvider().getState(level, random, finalPos);
            if (level.getBlock(finalPos).air() && finalPos.blockY() > context.minY()
                    && canSurvive(level, finalPos)) {
                level.setBlock(finalPos, state);
                placed++;
            }
        }

        return placed > 0;
    }

    /**
     * Vanilla {@code state.canSurvive(level, pos)} for the nether vegetation
     * blocks this feature places (fungi, roots, sprouts): the block below must
     * be in the corresponding {@code #supports_*} tag, which all resolve to
     * the same set of blocks.
     */
    private static boolean canSurvive(Block.Getter level, BlockVec pos) {
        var below = level.getBlock(pos.sub(0, 1, 0));
        return SUPPORTS_NETHER_VEGETATION.contains(below.name());
    }
}
