package rocks.minestom.worldgen.surface;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.RandomState;
import rocks.minestom.worldgen.biome.BiomeZoomer;
import rocks.minestom.worldgen.random.PositionalRandomFactory;

import java.util.List;

/**
 * Describes the surface material rule system that selects top-layer blocks from climate and noise inputs.
 * Surface rules are responsible for the visible ground: topsoil, stone, sand, and biome-specific
 * layers that sit on top of the raw terrain density.
 */
public final class SurfaceRules {
    private SurfaceRules() {
    }

    public static final Codec<RuleSource> CODEC = createRuleCodec();

    public static Block constantBlock(RuleSource rule) {
        if (rule instanceof BlockRuleSource blockRule) {
            return blockRule.block();
        }
        return null;
    }

    private static Codec<RuleSource> createRuleCodec() {
        return Codec.Recursive(self -> {
            var conditionCodec = createConditionCodec();

            var unionCodec = Codec.KEY.<RuleSource>unionType(
                    "type",
                    type -> structForRule(type, self, conditionCodec),
                    rule -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    }
            );

            return new Codec<>() {
                @Override
                public <D> Result<RuleSource> decode(Transcoder<D> coder, D value) {
                    return unionCodec.decode(coder, value);
                }

                @Override
                public <D> Result<D> encode(Transcoder<D> coder, RuleSource value) {
                    throw new UnsupportedOperationException("Encoding is not supported");
                }
            };
        });
    }

    private static Codec<ConditionSource> createConditionCodec() {
        return Codec.Recursive(self -> Codec.KEY.<ConditionSource>unionType(
                "type",
                type -> structForCondition(type, self),
                condition -> {
                    throw new UnsupportedOperationException("Encoding is not supported");
                }
        ));
    }

    private static StructCodec<? extends RuleSource> structForRule(Key type, Codec<RuleSource> self, Codec<ConditionSource> conditionCodec) {
        return switch (type.asString()) {
            case "minecraft:bandlands" -> StructCodec.struct(Bandlands::new);
            case "minecraft:block" -> StructCodec.struct(
                    "result_state", BlockCodec.CODEC, rule -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    BlockRuleSource::new
            );
            case "minecraft:sequence" -> StructCodec.struct(
                    "sequence", self.list(), rule -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    SequenceRuleSource::new
            );
            case "minecraft:condition" -> StructCodec.struct(
                    "if_true", conditionCodec, rule -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "then_run", self, rule -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    ConditionRuleSource::new
            );
            default -> throw new IllegalStateException("Unsupported surface rule type: " + type.asString());
        };
    }

    private static StructCodec<? extends ConditionSource> structForCondition(Key type, Codec<ConditionSource> self) {
        return switch (type.asString()) {
            case "minecraft:biome" -> StructCodec.struct(
                    "biome_is", Codec.KEY.list(), condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    BiomeConditionSource::new
            );
            case "minecraft:noise_threshold" -> StructCodec.struct(
                    "noise", Codec.KEY, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "min_threshold", Codec.DOUBLE, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "max_threshold", Codec.DOUBLE, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    NoiseThresholdConditionSource::new
            );
            case "minecraft:vertical_gradient" -> StructCodec.struct(
                    "random_name", Codec.KEY, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "true_at_and_below", VerticalAnchor.CODEC, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "false_at_and_above", VerticalAnchor.CODEC, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    VerticalGradientConditionSource::new
            );
            case "minecraft:y_above" -> StructCodec.struct(
                    "anchor", VerticalAnchor.CODEC, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "surface_depth_multiplier", Codec.INT, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "add_stone_depth", Codec.BOOLEAN, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    YAboveConditionSource::new
            );
            case "minecraft:water" -> StructCodec.struct(
                    "offset", Codec.INT, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "surface_depth_multiplier", Codec.INT, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "add_stone_depth", Codec.BOOLEAN, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    WaterConditionSource::new
            );
            case "minecraft:stone_depth" -> StructCodec.struct(
                    "offset", Codec.INT, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "add_surface_depth", Codec.BOOLEAN, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "secondary_depth_range", Codec.INT, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "surface_type", Codec.STRING.transform(
                            value -> switch (value) {
                                case "floor" -> SurfaceType.FLOOR;
                                case "ceiling" -> SurfaceType.CEILING;
                                default -> throw new IllegalStateException("Unsupported surface_type: " + value);
                            },
                            value -> {
                                throw new UnsupportedOperationException("Encoding is not supported");
                            }
                    ), condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    StoneDepthConditionSource::new
            );
            case "minecraft:not" -> StructCodec.struct(
                    "invert", self, condition -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    NotConditionSource::new
            );
            case "minecraft:hole" -> StructCodec.struct(HoleConditionSource::new);
            case "minecraft:steep" -> StructCodec.struct(SteepConditionSource::new);
            case "minecraft:above_preliminary_surface" -> StructCodec.struct(AbovePreliminarySurfaceConditionSource::new);
            case "minecraft:temperature" -> StructCodec.struct(TemperatureConditionSource::new);
            default -> throw new IllegalStateException("Unsupported surface condition type: " + type.asString());
        };
    }

    public interface RuleSource {
        Block tryApply(Context context);
    }

    public interface ConditionSource {
        boolean test(Context context);
    }

    public enum SurfaceType {
        FLOOR,
        CEILING
    }

    public static final class Context {
        private static final int HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE = 8;

        private final SurfaceSystem system;
        private final RandomState randomState;
        private final BiomeResolver biomeResolver;
        private final BiomeZoomer biomeZoomer;
        private final int minY;
        private final int maxYInclusive;

        private Key biome;
        private int blockX;
        private int blockZ;
        private int blockY;

        private int surfaceDepth;
        private double surfaceSecondary;
        private int minSurfaceLevel;
        private int waterHeight;
        private int stoneDepthBelow;
        private int stoneDepthAbove;
        private boolean steep;

        public Context(SurfaceSystem system, RandomState randomState, BiomeResolver biomeResolver, BiomeZoomer biomeZoomer, int minY, int maxYInclusive) {
            this.system = system;
            this.randomState = randomState;
            this.biomeResolver = biomeResolver;
            this.biomeZoomer = biomeZoomer;
            this.minY = minY;
            this.maxYInclusive = maxYInclusive;
        }

        public void updateXZ(int blockX, int blockZ, int preliminarySurfaceLevel, boolean steep, int waterHeight) {
            this.blockX = blockX;
            this.blockZ = blockZ;
            this.waterHeight = waterHeight;
            this.steep = steep;
            this.surfaceDepth = this.system.getSurfaceDepth(blockX, blockZ);
            this.surfaceSecondary = this.system.getSurfaceSecondary(blockX, blockZ);
            this.minSurfaceLevel = preliminarySurfaceLevel + this.surfaceDepth - HOW_FAR_BELOW_PRELIMINARY_SURFACE_LEVEL_TO_BUILD_SURFACE;
        }

        public void updateY(int blockY, int stoneDepthAbove, int stoneDepthBelow) {
            this.blockY = blockY;
            this.stoneDepthAbove = stoneDepthAbove;
            this.stoneDepthBelow = stoneDepthBelow;
            this.biome = this.biomeZoomer.biome(this.blockX, blockY, this.blockZ);
        }

        public SurfaceSystem system() {
            return this.system;
        }

        public RandomState randomState() {
            return this.randomState;
        }

        public BiomeResolver biomeResolver() {
            return this.biomeResolver;
        }

        public int minY() {
            return this.minY;
        }

        public int maxYInclusive() {
            return this.maxYInclusive;
        }

        public Key biome() {
            return this.biome;
        }

        public int blockX() {
            return this.blockX;
        }

        public int blockZ() {
            return this.blockZ;
        }

        public int blockY() {
            return this.blockY;
        }

        public int surfaceDepth() {
            return this.surfaceDepth;
        }

        public double surfaceSecondary() {
            return this.surfaceSecondary;
        }

        public int minSurfaceLevel() {
            return this.minSurfaceLevel;
        }

        public int waterHeight() {
            return this.waterHeight;
        }

        public int stoneDepthBelow() {
            return this.stoneDepthBelow;
        }

        public int stoneDepthAbove() {
            return this.stoneDepthAbove;
        }

        public boolean steep() {
            return this.steep;
        }
    }

    private record Bandlands() implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            return context.system().getBand(context.blockX(), context.blockY(), context.blockZ());
        }
    }

    private record BlockRuleSource(Block block) implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            return this.block;
        }
    }

    private record SequenceRuleSource(List<RuleSource> sequence) implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            for (var rule : this.sequence) {
                var result = rule.tryApply(context);
                if (result != null) {
                    return result;
                }
            }
            return null;
        }
    }

    private record ConditionRuleSource(ConditionSource condition, RuleSource thenRun) implements RuleSource {
        @Override
        public Block tryApply(Context context) {
            if (!this.condition.test(context)) {
                return null;
            }
            return this.thenRun.tryApply(context);
        }
    }

    private record BiomeConditionSource(List<Key> biomes) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return this.biomes.contains(context.biome());
        }
    }

    private record NoiseThresholdConditionSource(Key noise, double minThreshold, double maxThreshold) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var normalNoise = context.randomState().getOrCreateNoise(this.noise);
            var value = normalNoise.getValue((double) context.blockX(), 0.0D, (double) context.blockZ());
            return value >= this.minThreshold && value <= this.maxThreshold;
        }
    }

    private record VerticalGradientConditionSource(Key randomName, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var trueY = this.trueAtAndBelow.resolveY(context.minY(), context.maxYInclusive());
            var falseY = this.falseAtAndAbove.resolveY(context.minY(), context.maxYInclusive());
            var y = context.blockY();
            if (y <= trueY) {
                return true;
            }
            if (y >= falseY) {
                return false;
            }

            var chance = map((double) y, (double) trueY, (double) falseY, 1.0D, 0.0D);
            PositionalRandomFactory randomFactory = context.randomState().getOrCreateRandomFactory(this.randomName);
            var randomSource = randomFactory.at(context.blockX(), y, context.blockZ());
            return (double) randomSource.nextFloat() < chance;
        }
    }

    private record YAboveConditionSource(VerticalAnchor anchor, int surfaceDepthMultiplier, boolean addStoneDepth) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var depth = this.addStoneDepth ? context.stoneDepthAbove() : 0;
            return context.blockY() + depth >= this.anchor.resolveY(context.minY(), context.maxYInclusive()) + context.surfaceDepth() * this.surfaceDepthMultiplier;
        }
    }

    private record WaterConditionSource(int offset, int surfaceDepthMultiplier, boolean addStoneDepth) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            if (context.waterHeight() == Integer.MIN_VALUE) {
                return true;
            }

            var depth = this.addStoneDepth ? context.stoneDepthAbove() : 0;
            return context.blockY() + depth >= context.waterHeight() + this.offset + context.surfaceDepth() * this.surfaceDepthMultiplier;
        }
    }

    private record StoneDepthConditionSource(int offset, boolean addSurfaceDepth, int secondaryDepthRange, SurfaceType surfaceType) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            var ceiling = this.surfaceType == SurfaceType.CEILING;
            var stoneDepth = ceiling ? context.stoneDepthBelow() : context.stoneDepthAbove();
            var surfaceDepth = this.addSurfaceDepth ? context.surfaceDepth() : 0;
            var secondary = this.secondaryDepthRange == 0 ? 0 : (int) map(context.surfaceSecondary(), -1.0D, 1.0D, 0.0D, (double) this.secondaryDepthRange);
            return stoneDepth <= 1 + this.offset + surfaceDepth + secondary;
        }
    }

    private record NotConditionSource(ConditionSource invert) implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return !this.invert.test(context);
        }
    }

    private record HoleConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return context.surfaceDepth() <= 0;
        }
    }

    private record SteepConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return context.steep();
        }
    }

    private record AbovePreliminarySurfaceConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return context.blockY() >= context.minSurfaceLevel();
        }
    }

    private record TemperatureConditionSource() implements ConditionSource {
        @Override
        public boolean test(Context context) {
            return context.biomeResolver().temperature(context.biome()) < 0.15F;
        }
    }

    private static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        if (inMax - inMin == 0.0D) {
            return outMin;
        }
        var delta = (value - inMin) / (inMax - inMin);
        return outMin + delta * (outMax - outMin);
    }
}
