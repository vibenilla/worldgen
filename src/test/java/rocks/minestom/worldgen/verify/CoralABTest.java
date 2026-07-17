package rocks.minestom.worldgen.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.CoralClawFeature;
import rocks.minestom.worldgen.feature.CoralFeature;
import rocks.minestom.worldgen.feature.CoralMushroomFeature;
import rocks.minestom.worldgen.feature.CoralTreeFeature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the coral feature ports (tree, claw, mushroom) against real
 * vanilla 26.2 code in process: both sides run over an identical synthetic
 * warm ocean column (a solid floor with an unbroken water column above it up
 * to a per-run height) from identical random sequences, and every block
 * write plus the total draw count must match over 300 runs per feature.
 *
 * <p>Vanilla's coral placement draws a random index into the backing list of
 * the {@code #minecraft:coral_blocks}, {@code #minecraft:corals}, and
 * {@code #minecraft:wall_corals} block tags, so the draw only lines up with
 * {@link CoralFeature#CORAL_BLOCKS}, {@link CoralFeature#CORALS}, and
 * {@link CoralFeature#WALL_CORALS} if those tags are bound, in vanilla, in
 * the exact order those constants hardcode. Vanilla freezes its registries
 * before any datapack reload runs in this test process, so the normal tag
 * reload path (which would refuse to bind onto a frozen registry) cannot be
 * used; this test instead binds the ordered tag contents directly through
 * the same private hook the real reload uses ({@code MappedRegistry
 * .getOrCreateTagForRegistration(tag).bind(list)}), with the order read
 * fresh from the 26.2 tag JSON files (nested tag references expanded in
 * place, first occurrence wins) rather than copied from the production
 * constants, so a mismatch in either place would fail the test.
 */
final class CoralABTest {

    private static final int RUNS = 300;
    private static final int SEA_LEVEL = 63;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        FeatureABCompare.bindAllTags();
        bindOrderedCoralTags();
    }

    @Test
    void coralTree() {
        runCase("coral_tree", new net.minecraft.world.level.levelgen.feature.CoralTreeFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC),
                new CoralTreeFeature());
    }

    @Test
    void coralClaw() {
        runCase("coral_claw", new net.minecraft.world.level.levelgen.feature.CoralClawFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC),
                new CoralClawFeature());
    }

    @Test
    void coralMushroom() {
        runCase("coral_mushroom", new net.minecraft.world.level.levelgen.feature.CoralMushroomFeature(net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.CODEC),
                new CoralMushroomFeature());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void runCase(
            String name,
            net.minecraft.world.level.levelgen.feature.Feature<net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration> vanillaFeature,
            CoralFeature ourFeature) {
        var mismatches = 0L;
        var drawMismatchRuns = 0;
        var vanillaSetTotal = 0L;
        var printed = 0;

        for (var run = 0; run < RUNS; run++) {
            var seed = 13579L + run * 65537L;
            var x = 4000 - run * 23;
            var z = -1500 + run * 41;
            var floor = 32 + (run % 24);
            var ceiling = floor + 4 + (run % 17);
            var originY = floor + 1;

            var handler = new OceanLevel(floor, ceiling);
            var level = (WorldGenLevel) Proxy.newProxyInstance(
                    CoralABTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, handler);
            var vanillaRandom = new ChunkVegetationReplay.CountingRandom(new XoroshiroRandomSource(seed));
            var vanillaContext = new net.minecraft.world.level.levelgen.feature.FeaturePlaceContext<>(
                    Optional.empty(), level, null, vanillaRandom, new BlockPos(x, originY, z), net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration.INSTANCE);
            vanillaFeature.place((net.minecraft.world.level.levelgen.feature.FeaturePlaceContext) vanillaContext);

            var vanillaSets = new TreeMap<String, String>();
            for (var entry : handler.overlay.entrySet()) {
                vanillaSets.put(entry.getKey().getX() + "," + entry.getKey().getY() + "," + entry.getKey().getZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            var ourWorld = new OurOceanWorld(floor, ceiling);
            var ourRandom = new FeatureABCompare.CountingOurRandom(
                    new rocks.minestom.worldgen.random.XoroshiroRandomSource(seed));
            var context = new FeaturePlaceContext<NoneFeatureConfiguration, OurOceanWorld>(
                    ourWorld, ourRandom, new BlockVec(x, originY, z), new NoneFeatureConfiguration(), seed, 0, 255, SEA_LEVEL);
            ourFeature.place(context);

            var ourSets = new TreeMap<String, String>();
            for (var entry : ourWorld.overlay.entrySet()) {
                ourSets.put(entry.getKey().blockX() + "," + entry.getKey().blockY() + "," + entry.getKey().blockZ(),
                        FeatureABCompare.canonical(entry.getValue()));
            }

            vanillaSetTotal += vanillaSets.size();
            if (vanillaRandom.count != ourRandom.count) {
                drawMismatchRuns++;
                if (printed < 4) {
                    System.out.println(name + " run=" + run + " DRAWS vanilla=" + vanillaRandom.count
                            + " ours=" + ourRandom.count);
                    printed++;
                }
            }

            var keys = new TreeSet<String>();
            keys.addAll(vanillaSets.keySet());
            keys.addAll(ourSets.keySet());
            for (var key : keys) {
                var vanilla = vanillaSets.get(key);
                var ours = ourSets.get(key);
                if (!java.util.Objects.equals(vanilla, ours)) {
                    mismatches++;
                    if (printed < 16) {
                        System.out.println(name + " run=" + run + " at " + key
                                + " vanilla=" + vanilla + " ours=" + ours);
                        printed++;
                    }
                }
            }
        }

        System.out.println(name + ": runs=" + RUNS + " vanillaSets=" + vanillaSetTotal
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0L, mismatches, name + " block write mismatches");
        assertEquals(0, drawMismatchRuns, name + " runs with diverging draw counts");
    }

    // --- ordered vanilla tag binding ----------------------------------------

    /**
     * Reads the 26.2 coral tag JSON files directly (independent of {@link
     * CoralFeature}'s hardcoded lists) and binds each tag's ordered {@code
     * HolderSet} contents onto the frozen vanilla block registry, exactly as
     * {@code MappedRegistry.bindTags} would during a real tag reload.
     */
    @SuppressWarnings("unchecked")
    private static void bindOrderedCoralTags() throws Exception {
        var tagDirectory = java.nio.file.Path.of("data/mc/datapack/data/minecraft/tags/block");
        bindOrderedTag(tagDirectory, "coral_blocks");
        bindOrderedTag(tagDirectory, "corals");
        bindOrderedTag(tagDirectory, "wall_corals");
    }

    @SuppressWarnings("unchecked")
    private static void bindOrderedTag(java.nio.file.Path tagDirectory, String tagName) throws Exception {
        var ids = resolveOrderedTagValues(tagDirectory, tagName, new java.util.LinkedHashSet<>());
        var holders = new java.util.ArrayList<Holder<net.minecraft.world.level.block.Block>>();
        for (var id : ids) {
            holders.add(BuiltInRegistries.BLOCK.get(Identifier.parse(id)).orElseThrow());
        }

        var tagKey = TagKey.create(Registries.BLOCK, Identifier.parse("minecraft:" + tagName));
        var getOrCreateTagForRegistration = MappedRegistryReflection.GET_OR_CREATE_TAG_FOR_REGISTRATION;
        var named = getOrCreateTagForRegistration.invoke(BuiltInRegistries.BLOCK, tagKey);
        MappedRegistryReflection.BIND.invoke(named, holders);
    }

    private static java.util.LinkedHashSet<String> resolveOrderedTagValues(
            java.nio.file.Path tagDirectory, String tagName, java.util.LinkedHashSet<String> seen) throws Exception {
        var path = tagDirectory.resolve(tagName + ".json");
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
        for (var value : json.getAsJsonArray("values")) {
            var entry = value.getAsString();
            if (entry.startsWith("#")) {
                resolveOrderedTagValues(tagDirectory, entry.substring(entry.indexOf(':') + 1), seen);
            } else {
                seen.add(entry);
            }
        }

        return seen;
    }

    private static final class MappedRegistryReflection {
        static final java.lang.reflect.Method GET_OR_CREATE_TAG_FOR_REGISTRATION;
        static final java.lang.reflect.Method BIND;

        static {
            try {
                GET_OR_CREATE_TAG_FOR_REGISTRATION = net.minecraft.core.MappedRegistry.class
                        .getDeclaredMethod("getOrCreateTagForRegistration", TagKey.class);
                GET_OR_CREATE_TAG_FOR_REGISTRATION.setAccessible(true);
                BIND = net.minecraft.core.HolderSet.Named.class.getDeclaredMethod("bind", List.class);
                BIND.setAccessible(true);
            } catch (NoSuchMethodException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }

    // --- synthetic warm ocean world ------------------------------------------

    static String baseState(int y, int floor, int ceiling) {
        if (y <= floor) {
            return "minecraft:sand";
        }

        return y <= ceiling ? "minecraft:water" : null;
    }

    static final class OceanLevel implements InvocationHandler {
        final Map<BlockPos, BlockState> overlay = new HashMap<>();
        final int floor;
        final int ceiling;

        OceanLevel(int floor, int ceiling) {
            this.floor = floor;
            this.ceiling = ceiling;
        }

        BlockState state(BlockPos pos) {
            var placed = this.overlay.get(pos.immutable());
            if (placed != null) {
                return placed;
            }

            var base = baseState(pos.getY(), this.floor, this.ceiling);
            return base == null ? Blocks.AIR.defaultBlockState() : FeatureABCompare.vanillaState(base);
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getBlockState" -> this.state((BlockPos) args[0]);
                case "setBlock" -> {
                    this.overlay.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
                    yield true;
                }
                case "toString" -> "coral-ab-level";
                case "hashCode" -> 0;
                case "equals" -> proxy == args[0];
                default -> throw new UnsupportedOperationException("unexpected: " + method.getName());
            };
        }
    }

    static final class OurOceanWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> overlay = new HashMap<>();
        final int floor;
        final int ceiling;

        OurOceanWorld(int floor, int ceiling) {
            this.floor = floor;
            this.ceiling = ceiling;
        }

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var placed = this.overlay.get(new BlockVec(x, y, z));
            if (placed != null) {
                return placed;
            }

            var base = baseState(y, this.floor, this.ceiling);
            return base == null ? Block.AIR : FeatureABCompare.ourState(base);
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.overlay.put(new BlockVec(x, y, z), block);
        }
    }
}
