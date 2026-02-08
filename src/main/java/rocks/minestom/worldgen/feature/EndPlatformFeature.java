package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

public final class EndPlatformFeature implements Feature<NoneFeatureConfiguration> {
    public static final int SPAWN_X = 100;
    public static final int SPAWN_Y = 49;
    public static final int SPAWN_Z = 0;

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var chunkStartX = context.origin().blockX() & ~15;
        var chunkStartZ = context.origin().blockZ() & ~15;
        if (SPAWN_X < chunkStartX || SPAWN_X >= chunkStartX + 16 || SPAWN_Z < chunkStartZ || SPAWN_Z >= chunkStartZ + 16) {
            return false;
        }

        var level = context.accessor();
        var origin = new BlockVec(SPAWN_X, SPAWN_Y, SPAWN_Z);
        createEndPlatform(level, origin);
        return true;
    }

    private static <T extends Block.Getter & Block.Setter> void createEndPlatform(T level, BlockVec origin) {
        for (var offsetX = -2; offsetX <= 2; offsetX++) {
            for (var offsetZ = -2; offsetZ <= 2; offsetZ++) {
                for (var offsetY = -1; offsetY < 3; offsetY++) {
                    var block = offsetY == -1 ? Block.OBSIDIAN : Block.AIR;
                    var position = origin.add(offsetX, offsetY, offsetZ);
                    level.setBlock(position, block);
                }
            }
        }
    }
}
