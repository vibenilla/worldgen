package rocks.minestom.worldgen.feature.treedecorators;

/**
 * Vanilla's {@code TrunkVineDecorator}: each log has a 2/3 chance per
 * horizontal side to sprout a vine facing back at the trunk.
 */
public final class TrunkVineDecorator implements TreeDecorator {
    public static final TrunkVineDecorator INSTANCE = new TrunkVineDecorator();

    private TrunkVineDecorator() {
    }

    @Override
    public void place(TreeDecorator.Context context) {
        var random = context.random();
        for (var log : context.logs()) {
            if (random.nextInt(3) > 0) {
                var west = log.add(-1, 0, 0);
                if (context.isAir(west)) {
                    context.placeVine(west, "east");
                }
            }

            if (random.nextInt(3) > 0) {
                var east = log.add(1, 0, 0);
                if (context.isAir(east)) {
                    context.placeVine(east, "west");
                }
            }

            if (random.nextInt(3) > 0) {
                var north = log.add(0, 0, -1);
                if (context.isAir(north)) {
                    context.placeVine(north, "south");
                }
            }

            if (random.nextInt(3) > 0) {
                var south = log.add(0, 0, 1);
                if (context.isAir(south)) {
                    context.placeVine(south, "north");
                }
            }
        }
    }
}
