package rocks.minestom.worldgen.feature;

import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import rocks.minestom.worldgen.feature.configurations.SculkPatchConfiguration;

/**
 * Port of vanilla's {@code SculkPatchFeature}: spreads sculk and sculk vein
 * outward from the origin over a number of charge/spread rounds using a
 * {@link SculkSpreader}, then rolls a sculk catalyst below the origin and a
 * number of extra sculk shriekers nearby.
 */
public final class SculkPatchFeature implements Feature<SculkPatchConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SculkPatchConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        if (!canSpreadFrom(level, origin)) {
            return false;
        }

        var config = context.config();
        var random = context.random();
        var spreader = new SculkSpreader();
        var totalRounds = config.spreadRounds() + config.growthRounds();

        for (var round = 0; round < totalRounds; round++) {
            for (var i = 0; i < config.chargeCount(); i++) {
                spreader.addCursors(origin, config.amountPerCharge());
            }

            var spreadVeins = round < config.spreadRounds();
            for (var i = 0; i < config.spreadAttempts(); i++) {
                spreader.updateCursors(level, origin, random, spreadVeins);
            }

            spreader.clear();
        }

        var belowPos = origin.add(0, -1, 0);
        if (random.nextFloat() <= config.catalystChance() && isFullCollisionBlock(level.getBlock(belowPos))) {
            level.setBlock(origin.blockX(), origin.blockY(), origin.blockZ(), Block.SCULK_CATALYST);
        }

        var extraGrowths = config.extraRareGrowths().sample(random);
        for (var i = 0; i < extraGrowths; i++) {
            var candidate = origin.add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            var candidateBelow = candidate.add(0, -1, 0);
            if (level.getBlock(candidate).air() && hasFullFace(level.getBlock(candidateBelow), BlockFace.TOP)) {
                level.setBlock(candidate.blockX(), candidate.blockY(), candidate.blockZ(),
                        Block.SCULK_SHRIEKER.withProperty("can_summon", "true"));
            }
        }

        return true;
    }

    private static boolean canSpreadFrom(Block.Getter level, net.minestom.server.coordinate.BlockVec origin) {
        var start = level.getBlock(origin);
        if (start.compare(Block.SCULK) || start.compare(Block.SCULK_VEIN)) {
            return true;
        }

        if (!start.air() && !start.compare(Block.WATER)) {
            return false;
        }

        for (var direction : Direction.values()) {
            var neighbour = level.getBlock(direction.relative(origin));
            if (isFullCollisionBlock(neighbour)) {
                return true;
            }
        }

        return false;
    }

    /** Vanilla's {@code BlockState.isCollisionShapeFullBlock}: the collision shape spans the whole block. */
    private static boolean isFullCollisionBlock(Block block) {
        var shape = block.collisionShape();
        var start = shape.relativeStart();
        var end = shape.relativeEnd();
        return start.x() == 0.0 && start.y() == 0.0 && start.z() == 0.0
                && end.x() == 1.0 && end.y() == 1.0 && end.z() == 1.0;
    }

    /** Vanilla's {@code BlockState.isFaceSturdy} (default {@code SupportType.FULL}), approximated with the collision shape. */
    private static boolean hasFullFace(Block block, BlockFace face) {
        return block.collisionShape().isFaceFull(face);
    }
}
