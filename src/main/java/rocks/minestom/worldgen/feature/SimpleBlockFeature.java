package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SimpleBlockConfiguration;

public final class SimpleBlockFeature implements Feature<SimpleBlockConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SimpleBlockConfiguration, T> context) {
        var targetPosition = context.origin();
        var toPlace = context.config().toPlace().getState(context.random(), targetPosition);
        if (!this.canSurvive(context.accessor(), targetPosition, toPlace)) {
            return false;
        }

        if (this.isDoublePlant(toPlace)) {
            var upperPosition = targetPosition.add(0, 1, 0);
            if (!context.accessor().getBlock(upperPosition).isAir()) {
                return false;
            }

            context.accessor().setBlock(targetPosition, toPlace);
            context.accessor().setBlock(upperPosition, toPlace.withProperty("half", "upper"));
            return true;
        }

        context.accessor().setBlock(targetPosition, toPlace);
        return true;
    }

    private boolean canSurvive(Block.Getter accessor, BlockVec position, Block toPlace) {
        var atPosition = accessor.getBlock(position);
        if (!atPosition.isAir()) {
            return false;
        }

        var below = accessor.getBlock(position.sub(0, 1, 0));
        var key = toPlace.key().asString();

        if (this.requiresPlantGround(key)) {
            return this.isPlantGround(below);
        }

        if (key.endsWith("_mushroom")) {
            return below.registry().isSolid();
        }

        return below.registry().isSolid();
    }

    private boolean isDoublePlant(Block block) {
        var half = block.getProperty("half");
        return half != null && half.equals("lower");
    }

    private boolean requiresPlantGround(String blockKey) {
        return blockKey.equals("minecraft:dandelion")
                || blockKey.equals("minecraft:poppy")
                || blockKey.equals("minecraft:blue_orchid")
                || blockKey.equals("minecraft:allium")
                || blockKey.equals("minecraft:azure_bluet")
                || blockKey.equals("minecraft:red_tulip")
                || blockKey.equals("minecraft:orange_tulip")
                || blockKey.equals("minecraft:white_tulip")
                || blockKey.equals("minecraft:pink_tulip")
                || blockKey.equals("minecraft:oxeye_daisy")
                || blockKey.equals("minecraft:cornflower")
                || blockKey.equals("minecraft:lily_of_the_valley")
                || blockKey.equals("minecraft:pink_petals")
                || blockKey.equals("minecraft:wildflowers")
                || blockKey.equals("minecraft:closed_eyeblossom")
                || blockKey.equals("minecraft:open_eyeblossom")
                || blockKey.equals("minecraft:short_grass")
                || blockKey.equals("minecraft:tall_grass");
    }

    private boolean isPlantGround(Block blockBelow) {
        return Feature.isDirt(blockBelow)
                || blockBelow.compare(Block.FARMLAND)
                || blockBelow.compare(Block.MOSS_BLOCK)
                || blockBelow.compare(Block.MUD)
                || blockBelow.compare(Block.MUDDY_MANGROVE_ROOTS)
                || blockBelow.compare(Block.PALE_MOSS_BLOCK);
    }
}
