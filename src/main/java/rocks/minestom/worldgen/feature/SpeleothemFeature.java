package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.SpeleothemConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.Optional;

/**
 * Port of vanilla's {@code SpeleothemFeature} (the generic version of the old
 * pointed dripstone feature). Random call order matches vanilla exactly.
 */
public final class SpeleothemFeature implements Feature<SpeleothemConfiguration> {
    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<SpeleothemConfiguration, T> context) {
        var level = context.accessor();
        var pos = context.origin();
        var random = context.random();
        var config = context.config();

        var tipDirection = getTipDirection(level, pos, random, config);
        if (tipDirection.isEmpty()) {
            return false;
        }

        var rootPos = tipDirection.get().opposite().relative(pos);
        createPatchOfBaseBlocks(level, random, rootPos, config);
        var height = random.nextFloat() < config.chanceOfTallerGeneration()
                && SpeleothemUtils.isEmptyOrWater(level.getBlock(tipDirection.get().relative(pos)))
                ? 2
                : 1;
        SpeleothemUtils.growSpeleothem(
                level, pos, tipDirection.get(), height, false, config.baseBlock(), config.pointedBlock(), config.replaceableBlocks());
        return true;
    }

    private static <T extends Block.Getter & Block.Setter> Optional<Direction> getTipDirection(
            T level, BlockVec pos, RandomSource random, SpeleothemConfiguration config
    ) {
        var canPlaceAbove = SpeleothemUtils.isBase(level.getBlock(pos.add(0, 1, 0)), config.baseBlock(), config.replaceableBlocks());
        var canPlaceBelow = SpeleothemUtils.isBase(level.getBlock(pos.sub(0, 1, 0)), config.baseBlock(), config.replaceableBlocks());
        if (canPlaceAbove && canPlaceBelow) {
            return Optional.of(random.nextBoolean() ? Direction.DOWN : Direction.UP);
        }

        if (canPlaceAbove) {
            return Optional.of(Direction.DOWN);
        }

        return canPlaceBelow ? Optional.of(Direction.UP) : Optional.empty();
    }

    private static <T extends Block.Getter & Block.Setter> void createPatchOfBaseBlocks(
            T level, RandomSource random, BlockVec pos, SpeleothemConfiguration config
    ) {
        SpeleothemUtils.placeBaseBlockIfPossible(level, pos, config.baseBlock(), config.replaceableBlocks());

        for (var direction : Direction.HORIZONTAL) {
            if (random.nextFloat() > config.chanceOfDirectionalSpread()) {
                continue;
            }

            var pos1 = direction.relative(pos);
            SpeleothemUtils.placeBaseBlockIfPossible(level, pos1, config.baseBlock(), config.replaceableBlocks());
            if (random.nextFloat() > config.chanceOfSpreadRadius2()) {
                continue;
            }

            var pos2 = Direction.getRandom(random).relative(pos1);
            SpeleothemUtils.placeBaseBlockIfPossible(level, pos2, config.baseBlock(), config.replaceableBlocks());
            if (random.nextFloat() > config.chanceOfSpreadRadius3()) {
                continue;
            }

            var pos3 = Direction.getRandom(random).relative(pos2);
            SpeleothemUtils.placeBaseBlockIfPossible(level, pos3, config.baseBlock(), config.replaceableBlocks());
        }
    }
}
