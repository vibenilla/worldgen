package rocks.minestom.worldgen.structure.mansion;

import rocks.minestom.worldgen.random.RandomSource;

/**
 * Port of vanilla {@code WoodlandMansionPieces.FloorRoomCollection}: per-floor
 * template name selection for each room shape.
 */
abstract class FloorRoomCollection {
    abstract String get1x1(RandomSource random);

    abstract String get1x1Secret(RandomSource random);

    abstract String get1x2SideEntrance(RandomSource random, boolean isStairsRoom);

    abstract String get1x2FrontEntrance(RandomSource random, boolean isStairsRoom);

    abstract String get1x2Secret(RandomSource random);

    abstract String get2x2(RandomSource random);

    abstract String get2x2Secret(RandomSource random);

    /** Port of vanilla {@code FirstFloorRoomCollection}. */
    static final class First extends FloorRoomCollection {
        @Override
        String get1x1(RandomSource random) {
            return "1x1_a" + (random.nextInt(5) + 1);
        }

        @Override
        String get1x1Secret(RandomSource random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }

        @Override
        String get1x2SideEntrance(RandomSource random, boolean isStairsRoom) {
            return "1x2_a" + (random.nextInt(9) + 1);
        }

        @Override
        String get1x2FrontEntrance(RandomSource random, boolean isStairsRoom) {
            return "1x2_b" + (random.nextInt(5) + 1);
        }

        @Override
        String get1x2Secret(RandomSource random) {
            return "1x2_s" + (random.nextInt(2) + 1);
        }

        @Override
        String get2x2(RandomSource random) {
            return "2x2_a" + (random.nextInt(4) + 1);
        }

        @Override
        String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    /** Port of vanilla {@code SecondFloorRoomCollection}. */
    static class Second extends FloorRoomCollection {
        @Override
        String get1x1(RandomSource random) {
            return "1x1_b" + (random.nextInt(5) + 1);
        }

        @Override
        String get1x1Secret(RandomSource random) {
            return "1x1_as" + (random.nextInt(4) + 1);
        }

        @Override
        String get1x2SideEntrance(RandomSource random, boolean isStairsRoom) {
            return isStairsRoom ? "1x2_c_stairs" : "1x2_c" + (random.nextInt(4) + 1);
        }

        @Override
        String get1x2FrontEntrance(RandomSource random, boolean isStairsRoom) {
            return isStairsRoom ? "1x2_d_stairs" : "1x2_d" + (random.nextInt(5) + 1);
        }

        @Override
        String get1x2Secret(RandomSource random) {
            return "1x2_se" + (random.nextInt(1) + 1);
        }

        @Override
        String get2x2(RandomSource random) {
            return "2x2_b" + (random.nextInt(5) + 1);
        }

        @Override
        String get2x2Secret(RandomSource random) {
            return "2x2_s1";
        }
    }

    /** Port of vanilla {@code ThirdFloorRoomCollection}: identical to {@link Second}. */
    static final class Third extends Second {
    }
}
