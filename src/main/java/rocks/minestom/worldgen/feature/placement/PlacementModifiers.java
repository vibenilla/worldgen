package rocks.minestom.worldgen.feature.placement;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Transcoder;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.BlockCodec;
import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.noise.SimplexNoise;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
import rocks.minestom.worldgen.surface.VerticalAnchor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class PlacementModifiers {
    private static final SimplexNoise BIOME_INFO_NOISE = new SimplexNoise(new XoroshiroRandomSource(2345L));

    private PlacementModifiers() {
    }

    public static List<PlacementModifier> parse(JsonArray placementArray) {
        var modifiers = new ArrayList<PlacementModifier>();
        for (var placementElement : placementArray) {
            if (!placementElement.isJsonObject()) {
                continue;
            }

            var modifier = parseModifier(placementElement.getAsJsonObject());
            if (modifier != null) {
                modifiers.add(modifier);
            }
        }

        return List.copyOf(modifiers);
    }

    public static List<BlockVec> apply(
            List<PlacementModifier> modifiers,
            PlacementContext context,
            rocks.minestom.worldgen.random.RandomSource random,
            BlockVec origin
    ) {
        var positions = new ArrayList<BlockVec>();
        positions.add(origin);

        for (var modifier : modifiers) {
            var nextPositions = new ArrayList<BlockVec>();
            for (var position : positions) {
                nextPositions.addAll(modifier.apply(context, random, position));
            }
            positions = nextPositions;
            if (positions.isEmpty()) {
                break;
            }
        }

        return positions;
    }

    private static PlacementModifier parseModifier(JsonObject object) {
        var type = Key.key(object.get("type").getAsString()).asString();
        return switch (type) {
            case "minecraft:count" -> new CountModifier(IntProvider.fromJson(object.get("count")));
            case "minecraft:heightmap" -> {
                var typeValue = object.get("heightmap").getAsString();
                var heightmapType = PlacementContext.HeightmapType.fromString(typeValue);
                yield heightmapType == null ? null : new HeightmapModifier(heightmapType);
            }
            case "minecraft:in_square" -> new InSquareModifier();
            case "minecraft:biome" -> new BiomeModifier();
            case "minecraft:rarity_filter" -> new RarityFilterModifier(object.get("chance").getAsInt());
            case "minecraft:height_range" -> {
                var provider = HeightProvider.parse(object.getAsJsonObject("height"));
                yield new HeightRangeModifier(provider);
            }
            case "minecraft:random_offset" -> new RandomOffsetModifier(
                    IntProvider.fromJson(object.get("xz_spread")),
                    IntProvider.fromJson(object.get("y_spread")));
            case "minecraft:fixed_placement" -> {
                var positions = new ArrayList<BlockVec>();
                for (var positionElement : object.getAsJsonArray("positions")) {
                    var positionArray = positionElement.getAsJsonArray();
                    positions.add(new BlockVec(
                            positionArray.get(0).getAsInt(),
                            positionArray.get(1).getAsInt(),
                            positionArray.get(2).getAsInt()));
                }
                yield new FixedPlacementModifier(positions);
            }
            case "minecraft:noise_based_count" -> new NoiseBasedCountModifier(
                    object.get("noise_to_count_ratio").getAsInt(),
                    object.get("noise_factor").getAsDouble(),
                    object.has("noise_offset") ? object.get("noise_offset").getAsDouble() : 0.0D);
            case "minecraft:noise_threshold_count" -> new NoiseThresholdCountModifier(
                    object.get("noise_level").getAsDouble(),
                    object.get("below_noise").getAsInt(),
                    object.get("above_noise").getAsInt());
            case "minecraft:count_on_every_layer" -> new CountOnEveryLayerModifier(IntProvider.fromJson(object.get("count")));
            case "minecraft:environment_scan" -> new EnvironmentScanModifier(
                    Direction.fromString(object.get("direction_of_search").getAsString()),
                    parseBlockPredicate(object.getAsJsonObject("target_condition")),
                    object.has("allowed_search_condition")
                            ? parseBlockPredicate(object.getAsJsonObject("allowed_search_condition"))
                            : new AlwaysTrueBlockPredicate(),
                    object.get("max_steps").getAsInt());
            case "minecraft:surface_relative_threshold_filter" -> {
                var typeValue = object.get("heightmap").getAsString();
                var heightmapType = PlacementContext.HeightmapType.fromString(typeValue);
                if (heightmapType == null) {
                    yield null;
                }

                var minInclusive = object.has("min_inclusive") ? object.get("min_inclusive").getAsInt() : Integer.MIN_VALUE;
                var maxInclusive = object.has("max_inclusive") ? object.get("max_inclusive").getAsInt() : Integer.MAX_VALUE;
                yield new SurfaceRelativeThresholdFilterModifier(heightmapType, minInclusive, maxInclusive);
            }
            case "minecraft:surface_water_depth_filter" -> new SurfaceWaterDepthFilterModifier(object.get("max_water_depth").getAsInt());
            case "minecraft:block_predicate_filter" -> new BlockPredicateFilterModifier(parseBlockPredicate(object.getAsJsonObject("predicate")));
            default -> null;
        };
    }

    public static BlockPredicate parseBlockPredicate(JsonObject object) {
        var type = Key.key(object.get("type").getAsString()).asString();
        return switch (type) {
            case "minecraft:matching_blocks" -> new MatchingBlocksPredicate(parseBlocks(object.get("blocks")), parseOffset(object));
            case "minecraft:matching_fluids" -> new MatchingFluidsPredicate(parseFluids(object.get("fluids")), parseOffset(object));
            case "minecraft:would_survive" -> new WouldSurvivePredicate(
                    object.has("state") ? object.getAsJsonObject("state") : null,
                    parseOffset(object));
            case "minecraft:all_of" -> {
                var predicates = new ArrayList<BlockPredicate>();
                for (var predicate : object.getAsJsonArray("predicates")) {
                    predicates.add(parseBlockPredicate(predicate.getAsJsonObject()));
                }
                yield new AllOfPredicate(predicates);
            }
            case "minecraft:any_of" -> {
                var predicates = new ArrayList<BlockPredicate>();
                for (var predicate : object.getAsJsonArray("predicates")) {
                    predicates.add(parseBlockPredicate(predicate.getAsJsonObject()));
                }
                yield new AnyOfPredicate(predicates);
            }
            case "minecraft:not" -> new NotPredicate(parseBlockPredicate(object.getAsJsonObject("predicate")));
            case "minecraft:inside_world_bounds" -> new InsideWorldBoundsPredicate(parseOffset(object));
            case "minecraft:solid" -> new SolidPredicate(parseOffset(object));
            case "minecraft:has_sturdy_face" -> new HasSturdyFacePredicate(
                    Direction.fromString(object.get("direction").getAsString()),
                    parseOffset(object));
            default -> new AlwaysTrueBlockPredicate();
        };
    }

    private static Set<Key> parseBlocks(JsonElement value) {
        var blocks = new HashSet<Key>();
        if (value.isJsonArray()) {
            for (var blockValue : value.getAsJsonArray()) {
                blocks.add(Key.key(blockValue.getAsString()));
            }
            return blocks;
        }

        blocks.add(Key.key(value.getAsString()));
        return blocks;
    }

    private static Set<Key> parseFluids(JsonElement value) {
        var fluids = new HashSet<Key>();
        if (value.isJsonArray()) {
            for (var fluidValue : value.getAsJsonArray()) {
                fluids.add(Key.key(fluidValue.getAsString()));
            }
            return fluids;
        }

        fluids.add(Key.key(value.getAsString()));
        return fluids;
    }

    private static BlockVec parseOffset(JsonObject object) {
        if (!object.has("offset")) {
            return BlockVec.ZERO;
        }

        var offset = object.getAsJsonArray("offset");
        return new BlockVec(offset.get(0).getAsInt(), offset.get(1).getAsInt(), offset.get(2).getAsInt());
    }

    private record CountModifier(IntProvider count) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var countValue = this.count.sample(random);
                if (countValue <= 0) {
                    return List.of();
                }

                var results = new ArrayList<BlockVec>(countValue);
                for (var countIndex = 0; countIndex < countValue; countIndex++) {
                    results.add(position);
                }
                return results;
            }
        }

    private record HeightmapModifier(PlacementContext.HeightmapType type) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var y = context.getHeight(this.type, position.blockX(), position.blockZ());
                if (y <= context.minY()) {
                    return List.of();
                }

                return List.of(new BlockVec(position.blockX(), y, position.blockZ()));
            }
        }

    private static final class InSquareModifier implements PlacementModifier {
        @Override
        public List<BlockVec> apply(PlacementContext context, rocks.minestom.worldgen.random.RandomSource random, BlockVec position) {
            var x = position.blockX() + random.nextInt(16);
            var z = position.blockZ() + random.nextInt(16);
            return List.of(new BlockVec(x, position.blockY(), z));
        }
    }

    private static final class BiomeModifier implements PlacementModifier {
        @Override
        public List<BlockVec> apply(PlacementContext context, rocks.minestom.worldgen.random.RandomSource random, BlockVec position) {
            if (context.sourceBiome() == null) {
                return List.of(position);
            }

            if (context.biomeAt(position).equals(context.sourceBiome())) {
                return List.of(position);
            }
            return List.of();
        }
    }

    private record RarityFilterModifier(int chance) implements PlacementModifier {
            private RarityFilterModifier(int chance) {
                this.chance = Math.max(1, chance);
            }

            @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                return random.nextFloat() < 1.0F / (float) this.chance ? List.of(position) : List.of();
            }
        }

    private record HeightRangeModifier(HeightProvider provider) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                return List.of(new BlockVec(position.blockX(), this.provider.sample(random, context), position.blockZ()));
            }
        }

    private record RandomOffsetModifier(IntProvider xzSpread, IntProvider ySpread) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var x = position.blockX() + this.xzSpread.sample(random);
                var y = position.blockY() + this.ySpread.sample(random);
                var z = position.blockZ() + this.xzSpread.sample(random);
                return List.of(new BlockVec(x, y, z));
            }
        }

    private record FixedPlacementModifier(List<BlockVec> positions) implements PlacementModifier {
            private FixedPlacementModifier(List<BlockVec> positions) {
                this.positions = List.copyOf(positions);
            }

            @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var chunkX = Math.floorDiv(position.blockX(), 16);
                var chunkZ = Math.floorDiv(position.blockZ(), 16);
                var results = new ArrayList<BlockVec>();

                for (var fixedPosition : this.positions) {
                    if (Math.floorDiv(fixedPosition.blockX(), 16) == chunkX && Math.floorDiv(fixedPosition.blockZ(), 16) == chunkZ) {
                        results.add(fixedPosition);
                    }
                }

                return results;
            }
        }

    private record NoiseBasedCountModifier(int noiseToCountRatio, double noiseFactor,
                                           double noiseOffset) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var noiseValue = BIOME_INFO_NOISE.getValue(
                        (double) position.blockX() / this.noiseFactor,
                        (double) position.blockZ() / this.noiseFactor);
                var count = (int) Math.ceil((noiseValue + this.noiseOffset) * (double) this.noiseToCountRatio);
                if (count <= 0) {
                    return List.of();
                }

                var results = new ArrayList<BlockVec>(count);
                for (var countIndex = 0; countIndex < count; countIndex++) {
                    results.add(position);
                }
                return results;
            }
        }

    private record NoiseThresholdCountModifier(double noiseLevel, int belowNoise,
                                               int aboveNoise) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var noiseValue = BIOME_INFO_NOISE.getValue((double) position.blockX() / 200.0D, (double) position.blockZ() / 200.0D);
                var count = noiseValue < this.noiseLevel ? this.belowNoise : this.aboveNoise;
                if (count <= 0) {
                    return List.of();
                }

                var results = new ArrayList<BlockVec>(count);
                for (var countIndex = 0; countIndex < count; countIndex++) {
                    results.add(position);
                }
                return results;
            }
        }

    private record CountOnEveryLayerModifier(IntProvider count) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var results = new ArrayList<BlockVec>();
                var layer = 0;
                var found = false;

                do {
                    found = false;
                    var countValue = this.count.sample(random);
                    for (var countIndex = 0; countIndex < countValue; countIndex++) {
                        var x = position.blockX() + random.nextInt(16);
                        var z = position.blockZ() + random.nextInt(16);
                        var topY = context.getHeight(PlacementContext.HeightmapType.MOTION_BLOCKING, x, z);
                        var y = topY - 1 - layer;
                        if (y <= context.minY()) {
                            continue;
                        }

                        var targetPosition = new BlockVec(x, y + 1, z);
                        if (context.inWorldBounds(targetPosition)) {
                            results.add(targetPosition);
                            found = true;
                        }
                    }

                    layer++;
                } while (found);

                return results;
            }
        }

    private record EnvironmentScanModifier(Direction direction, BlockPredicate targetCondition,
                                           BlockPredicate allowedSearchCondition,
                                           int maxSteps) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var mutablePosition = position;
                if (!this.allowedSearchCondition.test(context, mutablePosition)) {
                    return List.of();
                }

                for (var step = 0; step < this.maxSteps; step++) {
                    if (this.targetCondition.test(context, mutablePosition)) {
                        return List.of(mutablePosition);
                    }

                    mutablePosition = mutablePosition.add(this.direction.stepX, this.direction.stepY, this.direction.stepZ);
                    if (!context.inWorldBounds(mutablePosition)) {
                        return List.of();
                    }

                    if (!this.allowedSearchCondition.test(context, mutablePosition)) {
                        break;
                    }
                }

                return this.targetCondition.test(context, mutablePosition) ? List.of(mutablePosition) : List.of();
            }
        }

    private record SurfaceRelativeThresholdFilterModifier(PlacementContext.HeightmapType heightmapType,
                                                          int minInclusive,
                                                          int maxInclusive) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var surfaceY = context.getHeight(this.heightmapType, position.blockX(), position.blockZ());
                var minY = surfaceY + this.minInclusive;
                var maxY = surfaceY + this.maxInclusive;
                if (position.blockY() >= minY && position.blockY() <= maxY) {
                    return List.of(position);
                }

                return List.of();
            }
        }

    private record SurfaceWaterDepthFilterModifier(int maxWaterDepth) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                var oceanFloor = context.getHeight(PlacementContext.HeightmapType.OCEAN_FLOOR, position.blockX(), position.blockZ());
                var worldSurface = context.getHeight(PlacementContext.HeightmapType.WORLD_SURFACE, position.blockX(), position.blockZ());
                return worldSurface - oceanFloor <= this.maxWaterDepth ? List.of(position) : List.of();
            }
        }

    private record BlockPredicateFilterModifier(BlockPredicate predicate) implements PlacementModifier {

        @Override
            public List<BlockVec> apply(PlacementContext context, RandomSource random, BlockVec position) {
                return this.predicate.test(context, position) ? List.of(position) : List.of();
            }
        }

    public interface BlockPredicate {
        boolean test(PlacementContext context, BlockVec position);
    }

    private static final class AlwaysTrueBlockPredicate implements BlockPredicate {
        @Override
        public boolean test(PlacementContext context, BlockVec position) {
            return true;
        }
    }

    private record MatchingBlocksPredicate(Set<Key> blocks, BlockVec offset) implements BlockPredicate {
            private MatchingBlocksPredicate(Set<Key> blocks, BlockVec offset) {
                this.blocks = Set.copyOf(blocks);
                this.offset = offset;
            }

            @Override
            public boolean test(PlacementContext context, BlockVec position) {
                var targetPosition = position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
                var block = context.accessor().getBlock(targetPosition);
                return this.blocks.contains(block.key());
            }
        }

    private record MatchingFluidsPredicate(Set<Key> fluids, BlockVec offset) implements BlockPredicate {
            private MatchingFluidsPredicate(Set<Key> fluids, BlockVec offset) {
                this.fluids = Set.copyOf(fluids);
                this.offset = offset;
            }

            @Override
            public boolean test(PlacementContext context, BlockVec position) {
                var targetPosition = position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
                var block = context.accessor().getBlock(targetPosition);
                if (this.fluids.contains(Key.key("minecraft:water")) && block.compare(Block.WATER)) {
                    return true;
                }
                return this.fluids.contains(Key.key("minecraft:lava")) && block.compare(Block.LAVA);
            }
        }

    private record WouldSurvivePredicate(Block state, BlockVec offset) implements BlockPredicate {
            private WouldSurvivePredicate(JsonObject state, BlockVec offset) {
                this(state == null ? Block.AIR : BlockCodec.CODEC.decode(Transcoder.JSON, state).orElse(Block.AIR),
                        offset);
            }

            @Override
            public boolean test(PlacementContext context, BlockVec position) {
                var targetPosition = position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
                var targetBlock = context.accessor().getBlock(targetPosition);
                if (!targetBlock.isAir()) {
                    return false;
                }

                var below = context.accessor().getBlock(targetPosition.sub(0, 1, 0));
                if (this.state.compare(Block.CACTUS)) {
                    return below.compare(Block.SAND) || below.compare(Block.RED_SAND) || below.compare(Block.CACTUS);
                }

                if (this.state.compare(Block.MANGROVE_PROPAGULE)) {
                    return below.compare(Block.MUD) || below.registry().isSolid();
                }

                return below.registry().isSolid();
            }
        }

    private record AllOfPredicate(List<BlockPredicate> predicates) implements BlockPredicate {
            private AllOfPredicate(List<BlockPredicate> predicates) {
                this.predicates = List.copyOf(predicates);
            }

            @Override
            public boolean test(PlacementContext context, BlockVec position) {
                for (var predicate : this.predicates) {
                    if (!predicate.test(context, position)) {
                        return false;
                    }
                }
                return true;
            }
        }

    private record AnyOfPredicate(List<BlockPredicate> predicates) implements BlockPredicate {
            private AnyOfPredicate(List<BlockPredicate> predicates) {
                this.predicates = List.copyOf(predicates);
            }

            @Override
            public boolean test(PlacementContext context, BlockVec position) {
                for (var predicate : this.predicates) {
                    if (predicate.test(context, position)) {
                        return true;
                    }
                }
                return false;
            }
        }

    private record NotPredicate(BlockPredicate predicate) implements BlockPredicate {

        @Override
            public boolean test(PlacementContext context, BlockVec position) {
                return !this.predicate.test(context, position);
            }
        }

    private record InsideWorldBoundsPredicate(BlockVec offset) implements BlockPredicate {

        @Override
            public boolean test(PlacementContext context, BlockVec position) {
                return context.inWorldBounds(position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ()));
            }
        }

    private record SolidPredicate(BlockVec offset) implements BlockPredicate {

        @Override
            public boolean test(PlacementContext context, BlockVec position) {
                var block = context.accessor().getBlock(position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ()));
                return block.registry().isSolid();
            }
        }

    private record HasSturdyFacePredicate(Direction direction, BlockVec offset) implements BlockPredicate {

        @Override
            public boolean test(PlacementContext context, BlockVec position) {
                var targetPosition = position.add(this.offset.blockX(), this.offset.blockY(), this.offset.blockZ());
                var supportPosition = targetPosition.add(-this.direction.stepX, -this.direction.stepY, -this.direction.stepZ);
                return context.accessor().getBlock(supportPosition).registry().isSolid();
            }
        }

    private enum Direction {
        UP(0, 1, 0),
        DOWN(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        WEST(-1, 0, 0),
        EAST(1, 0, 0);

        private final int stepX;
        private final int stepY;
        private final int stepZ;

        Direction(int stepX, int stepY, int stepZ) {
            this.stepX = stepX;
            this.stepY = stepY;
            this.stepZ = stepZ;
        }

        private static Direction fromString(String value) {
            return Direction.valueOf(value.toUpperCase());
        }
    }

    private interface HeightProvider {
        int sample(rocks.minestom.worldgen.random.RandomSource random, PlacementContext context);

        static HeightProvider parse(JsonObject object) {
            var type = Key.key(object.get("type").getAsString()).asString();
            return switch (type) {
                case "minecraft:uniform" -> new UniformHeightProvider(
                        VerticalAnchor.CODEC.decode(net.minestom.server.codec.Transcoder.JSON, object.get("min_inclusive")).orElseThrow(),
                        VerticalAnchor.CODEC.decode(net.minestom.server.codec.Transcoder.JSON, object.get("max_inclusive")).orElseThrow());
                case "minecraft:trapezoid" -> new TrapezoidHeightProvider(
                        VerticalAnchor.CODEC.decode(net.minestom.server.codec.Transcoder.JSON, object.get("min_inclusive")).orElseThrow(),
                        VerticalAnchor.CODEC.decode(net.minestom.server.codec.Transcoder.JSON, object.get("max_inclusive")).orElseThrow(),
                        object.has("plateau") ? object.get("plateau").getAsInt() : 0);
                case "minecraft:biased_to_bottom", "minecraft:very_biased_to_bottom" -> new BiasedToBottomHeightProvider(
                        VerticalAnchor.CODEC.decode(net.minestom.server.codec.Transcoder.JSON, object.get("min_inclusive")).orElseThrow(),
                        VerticalAnchor.CODEC.decode(net.minestom.server.codec.Transcoder.JSON, object.get("max_inclusive")).orElseThrow());
                default -> new ConstantHeightProvider(PlacementContext::seaLevel);
            };
        }
    }

    private record ConstantHeightProvider(java.util.function.Function<PlacementContext, Integer> valueProvider)
            implements HeightProvider {
        @Override
        public int sample(rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            return this.valueProvider.apply(context);
        }
    }

    private record UniformHeightProvider(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) implements HeightProvider {
        @Override
        public int sample(rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            var minY = this.minInclusive.resolveY(context.minY(), context.maxY());
            var maxY = this.maxInclusive.resolveY(context.minY(), context.maxY());
            if (maxY <= minY) {
                return minY;
            }
            return minY + random.nextInt(maxY - minY + 1);
        }
    }

    private record TrapezoidHeightProvider(VerticalAnchor minInclusive, VerticalAnchor maxInclusive, int plateau)
            implements HeightProvider {
        @Override
        public int sample(rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            var minY = this.minInclusive.resolveY(context.minY(), context.maxY());
            var maxY = this.maxInclusive.resolveY(context.minY(), context.maxY());
            if (maxY <= minY) {
                return minY;
            }

            var span = maxY - minY;
            var plateauSize = Math.max(0, Math.min(this.plateau, span));
            var slope = (span - plateauSize) / 2;
            if (slope <= 0) {
                return minY + random.nextInt(span + 1);
            }

            var randomSample = random.nextInt(span + slope + 1) - random.nextInt(slope + 1);
            var clamped = VMath.clamp(minY + randomSample, minY, maxY);
            return (int) clamped;
        }
    }

    private record BiasedToBottomHeightProvider(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) implements HeightProvider {
        @Override
        public int sample(rocks.minestom.worldgen.random.RandomSource random, PlacementContext context) {
            var minY = this.minInclusive.resolveY(context.minY(), context.maxY());
            var maxY = this.maxInclusive.resolveY(context.minY(), context.maxY());
            if (maxY <= minY) {
                return minY;
            }

            return minY + random.nextInt(random.nextInt(maxY - minY + 1) + 1);
        }
    }
}
