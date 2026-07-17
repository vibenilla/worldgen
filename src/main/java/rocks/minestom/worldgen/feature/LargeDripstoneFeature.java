package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.LargeDripstoneConfiguration;
import rocks.minestom.worldgen.feature.valueproviders.FloatProvider;
import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla's {@code LargeDripstoneFeature}: large wind-swept dripstone
 * columns. Random call order matches vanilla exactly.
 */
public final class LargeDripstoneFeature implements Feature<LargeDripstoneConfiguration> {

    /** Level types that can answer WORLD_SURFACE_WG queries in tests. */
    public interface WorldSurface {
        int worldSurfaceHeight(int x, int z);
    }

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<LargeDripstoneConfiguration, T> context) {
        var level = context.accessor();
        var origin = context.origin();
        var config = context.config();
        var random = context.random();
        if (!SpeleothemUtils.isEmptyOrWater(level, origin)) {
            return false;
        }

        var column = Column.scan(
                level,
                origin,
                config.floorToCeilingSearchRange(),
                SpeleothemUtils::isEmptyOrWater,
                state -> SpeleothemUtils.isBaseOrLava(state, Block.DRIPSTONE_BLOCK, config.replaceableBlocks()));
        if (column.isEmpty() || column.get().floor().isEmpty() || column.get().ceiling().isEmpty()) {
            return false;
        }

        var floor = column.get().floor().getAsInt();
        var ceiling = column.get().ceiling().getAsInt();
        var caveHeight = ceiling - floor - 1;
        if (caveHeight < 4) {
            return false;
        }

        var maxColumnRadiusBasedOnColumnHeight = (int) (caveHeight * config.maxColumnRadiusToCaveHeightRatio());
        var maxColumnRadius = Math.max(config.columnRadius().minValue(),
                Math.min(config.columnRadius().maxValue(), maxColumnRadiusBasedOnColumnHeight));
        var radius = randomBetweenInclusive(random, config.columnRadius().minValue(), maxColumnRadius);
        var stalactite = makeDripstone(
                new BlockVec(origin.blockX(), ceiling - 1, origin.blockZ()), false, random, radius,
                config.stalactiteBluntness(), config.heightScale());
        var stalagmite = makeDripstone(
                new BlockVec(origin.blockX(), floor + 1, origin.blockZ()), true, random, radius,
                config.stalagmiteBluntness(), config.heightScale());

        WindOffsetter wind;
        if (stalactite.isSuitableForWind(config) && stalagmite.isSuitableForWind(config)) {
            wind = new WindOffsetter(origin.blockY(), random, config.windSpeed(), 16 - radius);
        } else {
            wind = WindOffsetter.noWind();
        }

        var stalactiteBaseEmbeddedInStone = stalactite.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(level, wind);
        var stalagmiteBaseEmbeddedInStone = stalagmite.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(level, wind);
        if (stalactiteBaseEmbeddedInStone) {
            stalactite.placeBlocks(level, random, wind);
        }

        if (stalagmiteBaseEmbeddedInStone) {
            stalagmite.placeBlocks(level, random, wind);
        }

        return true;
    }

    private static LargeDripstone makeDripstone(
            BlockVec root, boolean pointingUp, RandomSource random, int radius,
            FloatProvider bluntness, FloatProvider heightScale
    ) {
        return new LargeDripstone(root, pointingUp, radius, bluntness.sample(random), heightScale.sample(random));
    }

    private static int randomBetweenInclusive(RandomSource random, int min, int maxInclusive) {
        return random.nextInt(maxInclusive - min + 1) + min;
    }

    private static float randomBetween(RandomSource random, float min, float maxExclusive) {
        return random.nextFloat() * (maxExclusive - min) + min;
    }

    private static <T extends Block.Getter & Block.Setter> int worldSurfaceHeight(T level, int x, int z) {
        if (level instanceof GenerationUnitAdapter adapter) {
            return adapter.getHeight(x, z);
        }

        if (level instanceof WorldSurface surface) {
            return surface.worldSurfaceHeight(x, z);
        }

        return Integer.MAX_VALUE;
    }

    private static final class LargeDripstone {
        private BlockVec root;
        private final boolean pointingUp;
        private int radius;
        private final double bluntness;
        private final double scale;

        private LargeDripstone(BlockVec root, boolean pointingUp, int radius, double bluntness, double scale) {
            this.root = root;
            this.pointingUp = pointingUp;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        private int getHeight() {
            return this.getHeightAtRadius(0.0F);
        }

        private int getHeightAtRadius(float checkRadius) {
            return (int) SpeleothemUtils.getSpeleothemHeight(checkRadius, this.radius, this.scale, this.bluntness);
        }

        private <T extends Block.Getter & Block.Setter> boolean moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(
                T level, WindOffsetter wind
        ) {
            while (this.radius > 1) {
                var newRoot = this.root;
                var maxTries = Math.min(10, this.getHeight());

                for (var attempt = 0; attempt < maxTries; attempt++) {
                    if (level.getBlock(newRoot).compare(Block.LAVA)) {
                        return false;
                    }

                    if (SpeleothemUtils.isCircleMostlyEmbeddedInStone(level, wind.offset(newRoot), this.radius)) {
                        this.root = newRoot;
                        return true;
                    }

                    newRoot = newRoot.add(0, this.pointingUp ? -1 : 1, 0);
                }

                this.radius /= 2;
            }

            return false;
        }

        private <T extends Block.Getter & Block.Setter> void placeBlocks(T level, RandomSource random, WindOffsetter wind) {
            for (var dx = -this.radius; dx <= this.radius; dx++) {
                for (var dz = -this.radius; dz <= this.radius; dz++) {
                    var currentRadius = (float) Math.sqrt(dx * dx + dz * dz);
                    if (currentRadius > this.radius) {
                        continue;
                    }

                    var height = this.getHeightAtRadius(currentRadius);
                    if (height <= 0) {
                        continue;
                    }

                    if (random.nextFloat() < 0.2F) {
                        height = (int) (height * randomBetween(random, 0.8F, 1.0F));
                    }

                    var pos = this.root.add(dx, 0, dz);
                    var hasBeenOutOfStone = false;
                    var maxY = this.pointingUp
                            ? worldSurfaceHeight(level, pos.blockX(), pos.blockZ())
                            : Integer.MAX_VALUE;

                    for (var step = 0; step < height && pos.blockY() < maxY; step++) {
                        var windAdjustedPos = wind.offset(pos);
                        if (SpeleothemUtils.isEmptyOrWaterOrLava(level.getBlock(windAdjustedPos))) {
                            hasBeenOutOfStone = true;
                            level.setBlock(windAdjustedPos, Block.DRIPSTONE_BLOCK);
                        } else if (hasBeenOutOfStone && isBaseStone(level.getBlock(windAdjustedPos))) {
                            break;
                        }

                        pos = pos.add(0, this.pointingUp ? 1 : -1, 0);
                    }
                }
            }
        }

        /** Vanilla checks {@code BlockTags.BASE_STONE_OVERWORLD} here. */
        private static boolean isBaseStone(Block state) {
            return state.compare(Block.STONE) || state.compare(Block.GRANITE) || state.compare(Block.DIORITE)
                    || state.compare(Block.ANDESITE) || state.compare(Block.TUFF) || state.compare(Block.DEEPSLATE);
        }

        private boolean isSuitableForWind(LargeDripstoneConfiguration config) {
            return this.radius >= config.minRadiusForWind() && this.bluntness >= config.minBluntnessForWind();
        }
    }

    private static final class WindOffsetter {
        private final int originY;
        private final double windSpeedX;
        private final double windSpeedZ;
        private final boolean hasWind;
        private final int maxOffset;

        private WindOffsetter(int originY, RandomSource random, FloatProvider windSpeedRange, int maxOffset) {
            this.originY = originY;
            this.maxOffset = maxOffset;
            var speed = windSpeedRange.sample(random);
            var direction = randomBetween(random, 0.0F, (float) Math.PI);
            this.windSpeedX = SpeleothemUtils.cos(direction) * speed;
            this.windSpeedZ = SpeleothemUtils.sin(direction) * speed;
            this.hasWind = true;
        }

        private WindOffsetter() {
            this.originY = 0;
            this.windSpeedX = 0.0D;
            this.windSpeedZ = 0.0D;
            this.hasWind = false;
            this.maxOffset = 0;
        }

        private static WindOffsetter noWind() {
            return new WindOffsetter();
        }

        private BlockVec offset(BlockVec pos) {
            if (!this.hasWind) {
                return pos;
            }

            var dy = this.originY - pos.blockY();
            var dx = (int) Math.max(-this.maxOffset, Math.min(this.maxOffset, Math.floor(this.windSpeedX * dy)));
            var dz = (int) Math.max(-this.maxOffset, Math.min(this.maxOffset, Math.floor(this.windSpeedZ * dy)));
            return pos.add(dx, 0, dz);
        }
    }
}
