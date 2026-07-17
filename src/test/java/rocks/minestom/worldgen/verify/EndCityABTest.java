package rocks.minestom.worldgen.verify;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minestom.server.coordinate.BlockVec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.structure.endcity.EndCityPieces;
import rocks.minestom.worldgen.structure.loader.StructureLoader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A/B compares the end city piece assembly ({@link EndCityPieces}) against
 * real vanilla 26.2 {@code EndCityPieces} run in process, across many
 * synthetic (seed, chunk) starts: both sides must draw the same random
 * sequence and produce the same ordered list of (template name, position,
 * rotation) pieces.
 *
 * <p>Vanilla {@code EndCityPieces} only needs a
 * {@code StructureTemplateManager} to resolve template names to
 * {@code StructureTemplate} instances (for bounding boxes used by collision
 * rejection) - it performs no other level access. Constructing a real
 * manager needs a {@code ResourceManager}/{@code LevelStorageAccess} pair
 * that cannot reasonably be built in a unit test, so - like
 * {@code FossilABTest} - this test bypasses only that lookup: it hand-ports
 * the real vanilla algorithm (transcribed from
 * {@code EndCityPieces.java}) using real vanilla {@code Rotation},
 * {@code BlockPos}, {@code RandomSource} and hand-loaded real vanilla
 * {@code StructureTemplate} instances for bounding boxes, so the geometry,
 * rotation and random-consumption behavior under test are all genuinely
 * vanilla.
 */
final class EndCityABTest {
    private static final Path ROOT = Path.of("data/mc/datapack");
    private static final int RUNS = 500;

    private static final List<String> TEMPLATE_NAMES = List.of(
            "base_floor", "base_roof", "bridge_end", "bridge_gentle_stairs", "bridge_piece",
            "bridge_steep_stairs", "fat_tower_base", "fat_tower_middle", "fat_tower_top",
            "second_floor_1", "second_floor_2", "second_roof", "ship", "third_floor_1",
            "third_floor_2", "third_roof", "tower_base", "tower_piece", "tower_top"
    );

    private static Map<String, StructureTemplate> vanillaTemplates;
    private static StructureLoader ourStructureLoader;

    @BeforeAll
    static void bootstrap() throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        vanillaTemplates = new HashMap<>();
        for (var name : TEMPLATE_NAMES) {
            vanillaTemplates.put(name, loadVanillaTemplate(name));
        }

        ourStructureLoader = new StructureLoader(new DataPack(ROOT));
    }

    @Test
    void pieceListsMatchAcrossSeeds() {
        var mismatches = 0;
        var drawMismatchRuns = 0;
        var printed = 0;
        var totalVanillaPieces = 0L;

        for (var run = 0; run < RUNS; run++) {
            var seed = 13579L + run * 104729L;
            var chunkX = -400 + run * 3;
            var chunkZ = 550 - run * 7;
            var originX = (chunkX << 4) + 7;
            var originZ = (chunkZ << 4) + 7;
            var originY = 64 + (run % 40);

            // vanilla
            var largeFeatureRandom = new WorldgenRandom(new LegacyRandomSource(0L));
            largeFeatureRandom.setLargeFeatureSeed(seed, chunkX, chunkZ);
            var countingVanillaRandom = new VanillaCountingRandom(largeFeatureRandom);
            var rotation = Rotation.getRandom(countingVanillaRandom);
            var vanillaPieces = new ArrayList<VanillaPiece>();
            vanillaStartHouseTower(new BlockPos(originX, originY, originZ), rotation, vanillaPieces, countingVanillaRandom);

            // ours
            var ourLargeFeatureRandom = new rocks.minestom.worldgen.random.WorldgenRandom(
                    new rocks.minestom.worldgen.random.LegacyRandomSource(0L));
            ourLargeFeatureRandom.setLargeFeatureSeed(seed, chunkX, chunkZ);
            var ourCountingRandom = new FeatureABCompare.CountingOurRandom(ourLargeFeatureRandom);
            var ourRotation = rocks.minestom.worldgen.structure.template.Rotation.getRandom(ourCountingRandom);
            var ourPieces = EndCityPieces.startHouseTower(ourStructureLoader,
                    new BlockVec(originX, originY, originZ), ourRotation, ourCountingRandom);

            totalVanillaPieces += vanillaPieces.size();

            if (countingVanillaRandom.count != ourCountingRandom.count) {
                drawMismatchRuns++;
                if (printed < 8) {
                    System.out.println("run=" + run + " DRAWS vanilla=" + countingVanillaRandom.count
                            + " ours=" + ourCountingRandom.count);
                    printed++;
                }
            }

            if (vanillaPieces.size() != ourPieces.size()) {
                mismatches++;
                if (printed < 24) {
                    System.out.println("run=" + run + " SIZE vanilla=" + vanillaPieces.size()
                            + " ours=" + ourPieces.size());
                    printed++;
                }
                continue;
            }

            for (var index = 0; index < vanillaPieces.size(); index++) {
                var vanillaPiece = vanillaPieces.get(index);
                var ourPiece = ourPieces.get(index);
                var vanillaRotation = canonicalRotation(vanillaPiece.rotation);
                var ourPieceRotation = canonicalRotation(ourPiece.rotation);
                var same = vanillaPiece.name.equals(templateSimpleName(ourPiece.templateKey.value()))
                        && vanillaPiece.position.getX() == ourPiece.position.blockX()
                        && vanillaPiece.position.getY() == ourPiece.position.blockY()
                        && vanillaPiece.position.getZ() == ourPiece.position.blockZ()
                        && vanillaRotation.equals(ourPieceRotation)
                        && vanillaPiece.overwrite == ourPiece.overwrite;
                if (!same) {
                    mismatches++;
                    if (printed < 24) {
                        System.out.println("run=" + run + " index=" + index
                                + " vanilla=" + vanillaPiece.name + "@" + vanillaPiece.position
                                + " rot=" + vanillaRotation + " ow=" + vanillaPiece.overwrite
                                + " | ours=" + ourPiece.templateKey + "@" + ourPiece.position
                                + " rot=" + ourPieceRotation + " ow=" + ourPiece.overwrite);
                        printed++;
                    }
                }
            }
        }

        System.out.println("EndCityABTest: runs=" + RUNS + " vanillaPieces=" + totalVanillaPieces
                + " mismatches=" + mismatches + " drawMismatchRuns=" + drawMismatchRuns);
        assertEquals(0, drawMismatchRuns, "runs with diverging draw counts");
        assertEquals(0, mismatches, "piece mismatches");
    }

    private static String templateSimpleName(String templateValue) {
        var slash = templateValue.lastIndexOf('/');
        return slash < 0 ? templateValue : templateValue.substring(slash + 1);
    }

    private static String canonicalRotation(Rotation rotation) {
        return rotation.name();
    }

    private static String canonicalRotation(rocks.minestom.worldgen.structure.template.Rotation rotation) {
        return rotation.name();
    }

    // --- hand port of vanilla EndCityPieces, using real vanilla types -------

    private static final int MAX_GEN_DEPTH = 8;

    private static void vanillaStartHouseTower(BlockPos origin, Rotation rotation, List<VanillaPiece> pieces,
            net.minecraft.util.RandomSource random) {
        var shipCreated = new boolean[]{false};
        var last = vanillaAddHelper(pieces, new VanillaPiece("base_floor", origin, rotation, true));
        last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 0, -1), "second_floor_1", rotation, false));
        last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 4, -1), "third_floor_1", rotation, false));
        last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 8, -1), "third_roof", rotation, true));
        vanillaRecursiveChildren(EndCityABTest::vanillaTowerGenerator, 1, last, null, pieces, random, shipCreated);
    }

    private static boolean vanillaHouseTowerGenerator(int genDepth, VanillaPiece parent, BlockPos offset,
            List<VanillaPiece> pieces, net.minecraft.util.RandomSource random, boolean[] shipCreated) {
        if (genDepth > MAX_GEN_DEPTH) {
            return false;
        }

        var rotation = parent.rotation;
        var last = vanillaAddHelper(pieces, vanillaAddPiece(parent, offset, "base_floor", rotation, true));
        var numFloors = random.nextInt(3);
        if (numFloors == 0) {
            vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 4, -1), "base_roof", rotation, true));
        } else if (numFloors == 1) {
            last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 0, -1), "second_floor_2", rotation, false));
            vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 8, -1), "second_roof", rotation, false));
            vanillaRecursiveChildren(EndCityABTest::vanillaTowerGenerator, genDepth + 1, last, null, pieces, random, shipCreated);
        } else if (numFloors == 2) {
            last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 0, -1), "second_floor_2", rotation, false));
            last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 4, -1), "third_floor_2", rotation, false));
            vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 8, -1), "third_roof", rotation, true));
            vanillaRecursiveChildren(EndCityABTest::vanillaTowerGenerator, genDepth + 1, last, null, pieces, random, shipCreated);
        }

        return true;
    }

    private static final List<Object[]> TOWER_BRIDGES = List.of(
            new Object[]{Rotation.NONE, new BlockPos(1, -1, 0)},
            new Object[]{Rotation.CLOCKWISE_90, new BlockPos(6, -1, 1)},
            new Object[]{Rotation.COUNTERCLOCKWISE_90, new BlockPos(0, -1, 5)},
            new Object[]{Rotation.CLOCKWISE_180, new BlockPos(5, -1, 6)}
    );

    private static final List<Object[]> FAT_TOWER_BRIDGES = List.of(
            new Object[]{Rotation.NONE, new BlockPos(4, -1, 0)},
            new Object[]{Rotation.CLOCKWISE_90, new BlockPos(12, -1, 4)},
            new Object[]{Rotation.COUNTERCLOCKWISE_90, new BlockPos(0, -1, 8)},
            new Object[]{Rotation.CLOCKWISE_180, new BlockPos(8, -1, 12)}
    );

    private static boolean vanillaTowerGenerator(int genDepth, VanillaPiece parent, BlockPos offset,
            List<VanillaPiece> pieces, net.minecraft.util.RandomSource random, boolean[] shipCreated) {
        var rotation = parent.rotation;
        var last = vanillaAddHelper(pieces, vanillaAddPiece(parent,
                new BlockPos(3 + random.nextInt(2), -3, 3 + random.nextInt(2)), "tower_base", rotation, true));
        last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(0, 7, 0), "tower_piece", rotation, true));
        var bridgePiece = random.nextInt(3) == 0 ? last : null;
        var towerHeight = 1 + random.nextInt(3);

        for (var i = 0; i < towerHeight; i++) {
            last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(0, 4, 0), "tower_piece", rotation, true));
            if (i < towerHeight - 1 && random.nextBoolean()) {
                bridgePiece = last;
            }
        }

        if (bridgePiece != null) {
            for (var bridge : TOWER_BRIDGES) {
                if (random.nextBoolean()) {
                    var bridgeStart = vanillaAddHelper(pieces, vanillaAddPiece(bridgePiece, (BlockPos) bridge[1],
                            "bridge_end", rotation.getRotated((Rotation) bridge[0]), true));
                    vanillaRecursiveChildren(EndCityABTest::vanillaTowerBridgeGenerator, genDepth + 1, bridgeStart,
                            null, pieces, random, shipCreated);
                }
            }
            vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 4, -1), "tower_top", rotation, true));
        } else {
            if (genDepth != 7) {
                return vanillaRecursiveChildren(EndCityABTest::vanillaFatTowerGenerator, genDepth + 1, last, null,
                        pieces, random, shipCreated);
            }
            vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-1, 4, -1), "tower_top", rotation, true));
        }

        return true;
    }

    private static boolean vanillaTowerBridgeGenerator(int genDepth, VanillaPiece parent, BlockPos offset,
            List<VanillaPiece> pieces, net.minecraft.util.RandomSource random, boolean[] shipCreated) {
        var rotation = parent.rotation;
        var bridgeLength = random.nextInt(4) + 1;
        var last = vanillaAddHelper(pieces, vanillaAddPiece(parent, new BlockPos(0, 0, -4), "bridge_piece", rotation, true));
        last.genDepth = -1;
        var nextY = 0;

        for (var i = 0; i < bridgeLength; i++) {
            if (random.nextBoolean()) {
                last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(0, nextY, -4), "bridge_piece", rotation, true));
                nextY = 0;
            } else {
                if (random.nextBoolean()) {
                    last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(0, nextY, -4), "bridge_steep_stairs", rotation, true));
                } else {
                    last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(0, nextY, -8), "bridge_gentle_stairs", rotation, true));
                }
                nextY = 4;
            }
        }

        if (!shipCreated[0] && random.nextInt(10 - genDepth) == 0) {
            vanillaAddHelper(pieces, vanillaAddPiece(last,
                    new BlockPos(-8 + random.nextInt(8), nextY, -70 + random.nextInt(10)), "ship", rotation, true));
            shipCreated[0] = true;
        } else if (!vanillaRecursiveChildren(EndCityABTest::vanillaHouseTowerGenerator, genDepth + 1, last,
                new BlockPos(-3, nextY + 1, -11), pieces, random, shipCreated)) {
            return false;
        }

        last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(4, nextY, 0), "bridge_end",
                rotation.getRotated(Rotation.CLOCKWISE_180), true));
        last.genDepth = -1;
        return true;
    }

    private static boolean vanillaFatTowerGenerator(int genDepth, VanillaPiece parent, BlockPos offset,
            List<VanillaPiece> pieces, net.minecraft.util.RandomSource random, boolean[] shipCreated) {
        var rotation = parent.rotation;
        var last = vanillaAddHelper(pieces, vanillaAddPiece(parent, new BlockPos(-3, 4, -3), "fat_tower_base", rotation, true));
        last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(0, 4, 0), "fat_tower_middle", rotation, true));

        for (var i = 0; i < 2 && random.nextInt(3) != 0; i++) {
            last = vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(0, 8, 0), "fat_tower_middle", rotation, true));
            for (var bridge : FAT_TOWER_BRIDGES) {
                if (random.nextBoolean()) {
                    var bridgeStart = vanillaAddHelper(pieces, vanillaAddPiece(last, (BlockPos) bridge[1],
                            "bridge_end", rotation.getRotated((Rotation) bridge[0]), true));
                    vanillaRecursiveChildren(EndCityABTest::vanillaTowerBridgeGenerator, genDepth + 1, bridgeStart,
                            null, pieces, random, shipCreated);
                }
            }
        }

        vanillaAddHelper(pieces, vanillaAddPiece(last, new BlockPos(-2, 8, -2), "fat_tower_top", rotation, true));
        return true;
    }

    private static VanillaPiece vanillaAddPiece(VanillaPiece parent, BlockPos offset, String templateName,
            Rotation rotation, boolean overwrite) {
        var child = new VanillaPiece(templateName, parent.position, rotation, overwrite);
        var origin = parent.template.calculateConnectedPosition(parent.placeSettings, offset, child.placeSettings, BlockPos.ZERO);
        child.move(origin.getX(), origin.getY(), origin.getZ());
        return child;
    }

    private static VanillaPiece vanillaAddHelper(List<VanillaPiece> pieces, VanillaPiece piece) {
        pieces.add(piece);
        return piece;
    }

    @FunctionalInterface
    private interface VanillaGenerator {
        boolean generate(int genDepth, VanillaPiece parent, BlockPos offset, List<VanillaPiece> pieces,
                net.minecraft.util.RandomSource random, boolean[] shipCreated);
    }

    private static boolean vanillaRecursiveChildren(VanillaGenerator generator, int genDepth, VanillaPiece parent,
            BlockPos offset, List<VanillaPiece> pieces, net.minecraft.util.RandomSource random, boolean[] shipCreated) {
        if (genDepth > MAX_GEN_DEPTH) {
            return false;
        }

        var childPieces = new ArrayList<VanillaPiece>();
        if (generator.generate(genDepth, parent, offset, childPieces, random, shipCreated)) {
            var collision = false;
            var childTag = random.nextInt();
            for (var child : childPieces) {
                child.genDepth = childTag;
                var collisionPiece = vanillaFindCollisionPiece(pieces, child.boundingBox);
                if (collisionPiece != null && collisionPiece.genDepth != parent.genDepth) {
                    collision = true;
                    break;
                }
            }
            if (!collision) {
                pieces.addAll(childPieces);
                return true;
            }
        }

        return false;
    }

    private static VanillaPiece vanillaFindCollisionPiece(List<VanillaPiece> pieces, BoundingBox box) {
        for (var piece : pieces) {
            if (piece.boundingBox.intersects(box)) {
                return piece;
            }
        }
        return null;
    }

    private static final class VanillaPiece {
        final String name;
        final StructureTemplate template;
        BlockPos position;
        final Rotation rotation;
        final boolean overwrite;
        final StructurePlaceSettings placeSettings;
        BoundingBox boundingBox;
        int genDepth;

        VanillaPiece(String name, BlockPos position, Rotation rotation, boolean overwrite) {
            this.name = name;
            this.template = vanillaTemplates.get(name);
            this.position = position;
            this.rotation = rotation;
            this.overwrite = overwrite;
            this.placeSettings = new StructurePlaceSettings().setIgnoreEntities(true).setRotation(rotation);
            this.boundingBox = this.template.getBoundingBox(this.placeSettings, this.position);
        }

        void move(int dx, int dy, int dz) {
            this.position = this.position.offset(dx, dy, dz);
            this.boundingBox = this.boundingBox.move(dx, dy, dz);
        }
    }

    /** Counts draws on any vanilla {@code RandomSource}, unlike {@code ChunkVegetationReplay.CountingRandom}, which is Xoroshiro-specific. */
    private static final class VanillaCountingRandom implements net.minecraft.util.RandomSource {
        private final net.minecraft.util.RandomSource delegate;
        long count;

        VanillaCountingRandom(net.minecraft.util.RandomSource delegate) {
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
            return this.delegate.nextInt();
        }

        @Override
        public int nextInt(int bound) {
            this.count++;
            return this.delegate.nextInt(bound);
        }

        @Override
        public long nextLong() {
            this.count++;
            return this.delegate.nextLong();
        }

        @Override
        public boolean nextBoolean() {
            this.count++;
            return this.delegate.nextBoolean();
        }

        @Override
        public float nextFloat() {
            this.count++;
            return this.delegate.nextFloat();
        }

        @Override
        public double nextDouble() {
            this.count++;
            return this.delegate.nextDouble();
        }

        @Override
        public double nextGaussian() {
            this.count++;
            return this.delegate.nextGaussian();
        }
    }

    private static StructureTemplate loadVanillaTemplate(String name) throws Exception {
        var path = ROOT.resolve("data/minecraft/structure/end_city/" + name + ".nbt");
        CompoundTag tag = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        var template = new StructureTemplate();
        template.load(net.minecraft.core.registries.BuiltInRegistries.BLOCK, tag);
        return template;
    }
}
