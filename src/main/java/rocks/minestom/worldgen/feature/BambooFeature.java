package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.ProbabilityConfiguration;

import java.util.Set;

/**
 * Port of vanilla's dedicated {@code BambooFeature}: grows a single bamboo
 * stalk at the origin, replacing the ground beneath a surrounding disc with
 * podzol with the configured probability.
 */
public final class BambooFeature implements Feature<ProbabilityConfiguration> {

    /** Vanilla's {@code #minecraft:supports_bamboo} block tag (26.2 contents). */
    private static final Set<String> SUPPORTS_BAMBOO = Set.of(
            "minecraft:sand", "minecraft:red_sand", "minecraft:suspicious_sand",
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots",
            "minecraft:moss_block", "minecraft:pale_moss_block",
            "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium",
            "minecraft:bamboo", "minecraft:bamboo_sapling",
            "minecraft:gravel", "minecraft:suspicious_gravel");

    /** Vanilla's {@code #minecraft:beneath_bamboo_podzol_replaceable} block tag (26.2 contents). */
    private static final Set<String> BENEATH_BAMBOO_PODZOL_REPLACEABLE = Set.of(
            "minecraft:dirt", "minecraft:coarse_dirt", "minecraft:rooted_dirt",
            "minecraft:mud", "minecraft:muddy_mangrove_roots",
            "minecraft:moss_block", "minecraft:pale_moss_block",
            "minecraft:grass_block", "minecraft:podzol", "minecraft:mycelium");

    private static final Block BAMBOO_TRUNK = Block.BAMBOO
            .withProperty("age", "1").withProperty("leaves", "none").withProperty("stage", "0");
    private static final Block BAMBOO_FINAL_LARGE = BAMBOO_TRUNK.withProperty("leaves", "large").withProperty("stage", "1");
    private static final Block BAMBOO_TOP_LARGE = BAMBOO_TRUNK.withProperty("leaves", "large");
    private static final Block BAMBOO_TOP_SMALL = BAMBOO_TRUNK.withProperty("leaves", "small");

    /** Level types that can answer WORLD_SURFACE queries in tests. */
    public interface WorldSurface {
        int worldSurfaceHeight(int x, int z);
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<ProbabilityConfiguration, T> context) {
        var placed = 0;
        var origin = context.origin();
        var level = context.accessor();
        var random = context.random();
        var config = context.config();
        var bambooPosition = origin;

        if (level.getBlock(bambooPosition).air()) {
            if (canSurvive(level, bambooPosition)) {
                var height = random.nextInt(12) + 5;
                if (random.nextFloat() < config.probability()) {
                    var radius = random.nextInt(4) + 1;

                    for (var x = origin.blockX() - radius; x <= origin.blockX() + radius; x++) {
                        for (var z = origin.blockZ() - radius; z <= origin.blockZ() + radius; z++) {
                            var deltaX = x - origin.blockX();
                            var deltaZ = z - origin.blockZ();
                            if (deltaX * deltaX + deltaZ * deltaZ <= radius * radius) {
                                var podzolY = worldSurfaceHeight(level, x, z) - 1;
                                var podzolPosition = new BlockVec(x, podzolY, z);
                                if (BENEATH_BAMBOO_PODZOL_REPLACEABLE.contains(level.getBlock(podzolPosition).name())) {
                                    level.setBlock(podzolPosition, Block.PODZOL);
                                }
                            }
                        }
                    }
                }

                var placedHeight = 0;
                while (placedHeight < height && level.getBlock(bambooPosition).air()) {
                    level.setBlock(bambooPosition, BAMBOO_TRUNK);
                    bambooPosition = bambooPosition.add(0, 1, 0);
                    placedHeight++;
                }

                if (bambooPosition.blockY() - origin.blockY() >= 3) {
                    level.setBlock(bambooPosition, BAMBOO_FINAL_LARGE);
                    bambooPosition = bambooPosition.sub(0, 1, 0);
                    level.setBlock(bambooPosition, BAMBOO_TOP_LARGE);
                    bambooPosition = bambooPosition.sub(0, 1, 0);
                    level.setBlock(bambooPosition, BAMBOO_TOP_SMALL);
                }
            }

            placed++;
        }

        return placed > 0;
    }

    private static <T extends Block.Getter> boolean canSurvive(T level, BlockVec position) {
        var below = level.getBlock(position.sub(0, 1, 0));
        return SUPPORTS_BAMBOO.contains(below.name());
    }

    private static <T extends Block.Getter> int worldSurfaceHeight(T level, int x, int z) {
        if (level instanceof GenerationUnitAdapter adapter) {
            return adapter.heightmap(GenerationUnitAdapter.HeightmapType.WORLD_SURFACE, x, z);
        }

        if (level instanceof WorldSurface worldSurface) {
            return worldSurface.worldSurfaceHeight(x, z);
        }

        return 0;
    }
}
