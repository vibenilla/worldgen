package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

/**
 * Replays a full vanilla PLACED feature (placement modifiers + nested
 * features) for one chunk against a captured wide world snapshot + the rng
 * state rewound to the start of the placed feature.
 * Args: trace file, placed feature json, tree key (pre:x:y:z of the FIRST
 * tree), chunk start x, chunk start z, rewind draw count.
 */
public final class ChunkVegetationReplay {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        bindTags();

        var traceFile = Path.of(args[0]);
        var featureJson = Path.of(args[1]);
        var treeKey = args[2];
        var startX = Integer.parseInt(args[3]);
        var startZ = Integer.parseInt(args[4]);
        var rewind = Integer.parseInt(args[5]);

        long seedLo = 0;
        long seedHi = 0;
        var world = new HashMap<BlockPos, BlockState>();
        for (var line : Files.readAllLines(traceFile)) {
            if (!line.startsWith("TRACE ")) {
                continue;
            }
            var parts = line.split(" ");
            if (parts.length < 3 || !parts[2].equals(treeKey)) {
                continue;
            }
            switch (parts[1]) {
                case "rng" -> {
                    seedLo = Long.parseLong(parts[3]);
                    seedHi = Long.parseLong(parts[4]);
                }
                case "world" -> {
                    var pos = new BlockPos(Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]));
                    world.put(pos, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, parts[6], false).blockState());
                }
                default -> {
                }
            }
        }

        // Rewind the xoroshiro state to the start of the placed feature
        var state = new long[]{seedLo, seedHi};
        for (var index = 0; index < rewind; index++) {
            rewind(state);
        }
        System.out.println("world=" + world.size() + " rng@featureStart=" + state[0] + "," + state[1]);

        var lookup = net.minecraft.data.registries.VanillaRegistries.createLookup();
        var placedName = featureJson.getFileName().toString().replace(".json", "");
        var placed = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.PLACED_FEATURE)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.PLACED_FEATURE,
                        net.minecraft.resources.Identifier.parse("minecraft:" + placedName)))
                .value();

        var minY = world.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
        var maxY = world.keySet().stream().mapToInt(BlockPos::getY).max().orElse(0) + 6;
        var level = (WorldGenLevel) Proxy.newProxyInstance(
                ChunkVegetationReplay.class.getClassLoader(),
                new Class<?>[]{WorldGenLevel.class},
                new TreeReplay.Handler(world, minY, maxY));

        var presets = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        var overworldPreset = presets.getOrThrow(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST,
                net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var biomeSource = net.minecraft.world.level.biome.MultiNoiseBiomeSource.createFromPreset(overworldPreset);
        var noiseSettings = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.NOISE_SETTINGS)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.NOISE_SETTINGS,
                        net.minecraft.resources.Identifier.parse("minecraft:overworld")));
        var generator = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(biomeSource, noiseSettings);

        var biomeName = args.length > 6 ? args[6] : "minecraft:dark_forest";
        var biome = lookup.lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getOrThrow(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.BIOME,
                        net.minecraft.resources.Identifier.parse(biomeName)));
        TreeReplay.Handler.biomeHolder = biome;

        var counted = new CountingRandom(new XoroshiroRandomSource(state[0], state[1]));
        TreeReplay.Handler.drawCounter = new java.util.concurrent.atomic.AtomicLong();
        counted.counter = TreeReplay.Handler.drawCounter;
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(counted);
        var result = placed.placeWithBiomeCheck(level, generator, random, new BlockPos(startX, -64, startZ));
        System.out.println("TOTALDRAWS " + counted.count);
        System.out.println("RESULT " + result);
    }

    /** Forwards to a xoroshiro source, counting nextLong draws. */
    static final class CountingRandom implements net.minecraft.util.RandomSource {
        private final XoroshiroRandomSource delegate;
        long count;
        java.util.concurrent.atomic.AtomicLong counter;

        CountingRandom(XoroshiroRandomSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public net.minecraft.util.RandomSource fork() {
            return this.delegate.fork();
        }

        @Override
        public net.minecraft.world.level.levelgen.PositionalRandomFactory forkPositional() {
            return this.delegate.forkPositional();
        }

        @Override
        public void setSeed(long seed) {
            this.delegate.setSeed(seed);
        }

        @Override
        public int nextInt() {
            this.count++;
            if (this.counter != null) this.counter.set(this.count);
            return this.delegate.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            var value = this.delegate.nextInt(bound);
            this.count++;
            if (this.counter != null) this.counter.set(this.count);
            if (this.count <= DEBUG_DRAWS) System.out.println("DRAW " + this.count + " nextInt(" + bound + ")=" + value);
            return value;
        }

        static final long DEBUG_DRAWS = Long.getLong("replay.debugDraws", 0);

        @Override
        public long nextLong() {
            var value = this.delegate.nextLong();
            this.count++;
            if (this.counter != null) this.counter.set(this.count);
            if (this.count <= DEBUG_DRAWS) System.out.println("DRAW " + this.count + " nextLong()=" + value);
            return value;
        }

        @Override
        public boolean nextBoolean() {
            this.count++;
            if (this.counter != null) this.counter.set(this.count);
            return this.delegate.nextBoolean();
        }

        @Override
        public float nextFloat() {
            var value = this.delegate.nextFloat();
            this.count++;
            if (this.counter != null) this.counter.set(this.count);
            if (this.count <= DEBUG_DRAWS) System.out.println("DRAW " + this.count + " nextFloat()=" + value);
            return value;
        }

        @Override
        public double nextDouble() {
            this.count++;
            if (this.counter != null) this.counter.set(this.count);
            return this.delegate.nextDouble();
        }

        @Override
        public double nextGaussian() {
            this.count++;
            if (this.counter != null) this.counter.set(this.count);
            return this.delegate.nextGaussian();
        }
    }

    private static void rewind(long[] state) {
        var rotated = state[1];
        var high = Long.rotateLeft(rotated, 64 - 28);
        var low = Long.rotateLeft(state[0] ^ high ^ (high << 21), 64 - 49);
        state[1] = high ^ low;
        state[0] = low;
    }

    private static void bindTags() throws Exception {
        var method = TreeReplay.class.getDeclaredMethod("bindDatapackTags");
        method.setAccessible(true);
        method.invoke(null);
    }
}
