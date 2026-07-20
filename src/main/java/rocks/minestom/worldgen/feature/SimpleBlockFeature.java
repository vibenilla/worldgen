package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SimpleBlockConfiguration;

public final class SimpleBlockFeature implements Feature<SimpleBlockConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SimpleBlockConfiguration, T> context) {
        var targetPosition = context.origin();
        var toPlace = context.config().toPlace().getState(context.accessor(), context.random(), targetPosition);
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

    /**
     * Approximation of vanilla's {@code state.canSurvive(level, pos)}: plants
     * need plant ground below, other non-solid blocks need support, and full
     * blocks survive anywhere. Vanilla does not require the target position to
     * be empty here; that gating comes from the placement block predicates.
     */
    private boolean canSurvive(Block.Getter accessor, BlockVec position, Block toPlace) {
        var key = toPlace.key().asString();
        if (this.requiresPlantGround(key)) {
            return this.isPlantGround(accessor.getBlock(position.sub(0, 1, 0)));
        }

        // Vanilla DryVegetationBlock.mayPlaceOn: supports_dry_vegetation
        // (sand, terracotta plus the vegetation substrates)
        if (key.equals("minecraft:dead_bush") || key.equals("minecraft:short_dry_grass")
                || key.equals("minecraft:tall_dry_grass")) {
            return BlockSupports.isInTag("minecraft:supports_dry_vegetation",
                    accessor.getBlock(position.sub(0, 1, 0)));
        }

        // Vanilla MushroomBlock.canSurvive: an overriding floor (mycelium,
        // podzol, nylium) or raw brightness below 13 - during generation only
        // a not-yet-generated chunk reads bright (15 everywhere), so the
        // placement fails exactly when the target chunk generates later than
        // the decorated one; the FULL post-process mark re-checks survivors
        // against the real light
        if (key.equals("minecraft:red_mushroom") || key.equals("minecraft:brown_mushroom")) {
            var below = accessor.getBlock(position.sub(0, 1, 0));
            if (BlockSupports.isInTag("minecraft:overrides_mushroom_light_requirement", below)) {
                return true;
            }
            if (accessor instanceof GenerationUnitAdapter adapter
                    && adapter.fullBrightAtGeneration(position.blockX(), position.blockZ())) {
                return false;
            }
            return below.registry().isSolid();
        }

        // 26.x per-block supports_<name> tags carry most plant survival rules
        var supported = BlockSupports.supportsOf(toPlace);
        if (supported != null) {
            return supported.contains(accessor.getBlock(position.sub(0, 1, 0)).key());
        }

        // Ceiling-hanging blocks survive on support ABOVE (vanilla
        // SporeBlossomBlock/HangingRootsBlock canSurvive checks
        // canSupportCenter of the block above, not below - a mineshaft
        // cobweb ceiling is NOT support, and Minestom's isSolid says it is)
        if (key.equals("minecraft:spore_blossom") || key.equals("minecraft:hanging_roots")) {
            return SturdyFaces.isFaceSturdy(accessor.getBlock(position.add(0, 1, 0)),
                    net.minestom.server.instance.block.BlockFace.BOTTOM);
        }

        if (toPlace.registry().isSolid()) {
            return true;
        }

        return accessor.getBlock(position.sub(0, 1, 0)).registry().isSolid();
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
                || blockKey.equals("minecraft:tall_grass")
                || blockKey.equals("minecraft:fern")
                || blockKey.equals("minecraft:large_fern")
                || blockKey.equals("minecraft:firefly_bush")
                || blockKey.equals("minecraft:bush");
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
