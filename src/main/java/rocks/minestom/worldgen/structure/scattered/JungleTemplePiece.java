package rocks.minestom.worldgen.structure.scattered;

import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Direction;
import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

/**
 * Port of vanilla {@code JungleTemplePiece}.
 */
final class JungleTemplePiece extends ScatteredFeaturePiece {
    static final int WIDTH = 12;
    static final int DEPTH = 15;

    private boolean placedMainChest;
    private boolean placedHiddenChest;
    private boolean placedTrap1;
    private boolean placedTrap2;

    private static final MossStoneSelector STONE_SELECTOR = new MossStoneSelector();

    JungleTemplePiece(RandomSource random, int west, int north) {
        super(west, 64, north, WIDTH, 10, DEPTH, getRandomHorizontalDirection(random));
    }

    void postProcess(ScatteredFeatureLevel level, RandomSource random, BoundingBox chunkBB) {
        if (!this.updateAverageGroundHeight(level, chunkBB, 0)) {
            return;
        }

        this.generateBox(level, chunkBB, 0, -4, 0, this.width - 1, 0, this.depth - 1, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 2, 1, 2, 9, 2, 2, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 2, 1, 12, 9, 2, 12, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 2, 1, 3, 2, 2, 11, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 9, 1, 3, 9, 2, 11, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 1, 3, 1, 10, 6, 1, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 1, 3, 13, 10, 6, 13, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 1, 3, 2, 1, 6, 12, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 10, 3, 2, 10, 6, 12, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 2, 3, 2, 9, 3, 12, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 2, 6, 2, 9, 6, 12, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 3, 7, 3, 8, 7, 11, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 4, 8, 4, 7, 8, 10, false, random, STONE_SELECTOR);
        this.generateAirBox(level, chunkBB, 3, 1, 3, 8, 2, 11);
        this.generateAirBox(level, chunkBB, 4, 3, 6, 7, 3, 9);
        this.generateAirBox(level, chunkBB, 2, 4, 2, 9, 5, 12);
        this.generateAirBox(level, chunkBB, 4, 6, 5, 7, 6, 9);
        this.generateAirBox(level, chunkBB, 5, 7, 6, 6, 7, 8);
        this.generateAirBox(level, chunkBB, 5, 1, 2, 6, 2, 2);
        this.generateAirBox(level, chunkBB, 5, 2, 12, 6, 2, 12);
        this.generateAirBox(level, chunkBB, 5, 5, 1, 6, 5, 1);
        this.generateAirBox(level, chunkBB, 5, 5, 13, 6, 5, 13);
        this.placeBlock(level, Block.AIR, 1, 5, 5, chunkBB);
        this.placeBlock(level, Block.AIR, 10, 5, 5, chunkBB);
        this.placeBlock(level, Block.AIR, 1, 5, 9, chunkBB);
        this.placeBlock(level, Block.AIR, 10, 5, 9, chunkBB);

        for (var z = 0; z <= 14; z += 14) {
            this.generateBox(level, chunkBB, 2, 4, z, 2, 5, z, false, random, STONE_SELECTOR);
            this.generateBox(level, chunkBB, 4, 4, z, 4, 5, z, false, random, STONE_SELECTOR);
            this.generateBox(level, chunkBB, 7, 4, z, 7, 5, z, false, random, STONE_SELECTOR);
            this.generateBox(level, chunkBB, 9, 4, z, 9, 5, z, false, random, STONE_SELECTOR);
        }

        this.generateBox(level, chunkBB, 5, 6, 0, 6, 6, 0, false, random, STONE_SELECTOR);

        for (var x = 0; x <= 11; x += 11) {
            for (var z = 2; z <= 12; z += 2) {
                this.generateBox(level, chunkBB, x, 4, z, x, 5, z, false, random, STONE_SELECTOR);
            }

            this.generateBox(level, chunkBB, x, 6, 5, x, 6, 5, false, random, STONE_SELECTOR);
            this.generateBox(level, chunkBB, x, 6, 9, x, 6, 9, false, random, STONE_SELECTOR);
        }

        this.generateBox(level, chunkBB, 2, 7, 2, 2, 9, 2, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 9, 7, 2, 9, 9, 2, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 2, 7, 12, 2, 9, 12, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 9, 7, 12, 9, 9, 12, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 4, 9, 4, 4, 9, 4, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 7, 9, 4, 7, 9, 4, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 4, 9, 10, 4, 9, 10, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 7, 9, 10, 7, 9, 10, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 5, 9, 7, 6, 9, 7, false, random, STONE_SELECTOR);

        var eastStairs = Block.COBBLESTONE_STAIRS.withProperty("facing", "east");
        var westStairs = Block.COBBLESTONE_STAIRS.withProperty("facing", "west");
        var southStairs = Block.COBBLESTONE_STAIRS.withProperty("facing", "south");
        var northStairs = Block.COBBLESTONE_STAIRS.withProperty("facing", "north");
        this.placeBlock(level, northStairs, 5, 9, 6, chunkBB);
        this.placeBlock(level, northStairs, 6, 9, 6, chunkBB);
        this.placeBlock(level, southStairs, 5, 9, 8, chunkBB);
        this.placeBlock(level, southStairs, 6, 9, 8, chunkBB);
        this.placeBlock(level, northStairs, 4, 0, 0, chunkBB);
        this.placeBlock(level, northStairs, 5, 0, 0, chunkBB);
        this.placeBlock(level, northStairs, 6, 0, 0, chunkBB);
        this.placeBlock(level, northStairs, 7, 0, 0, chunkBB);
        this.placeBlock(level, northStairs, 4, 1, 8, chunkBB);
        this.placeBlock(level, northStairs, 4, 2, 9, chunkBB);
        this.placeBlock(level, northStairs, 4, 3, 10, chunkBB);
        this.placeBlock(level, northStairs, 7, 1, 8, chunkBB);
        this.placeBlock(level, northStairs, 7, 2, 9, chunkBB);
        this.placeBlock(level, northStairs, 7, 3, 10, chunkBB);
        this.generateBox(level, chunkBB, 4, 1, 9, 4, 1, 9, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 7, 1, 9, 7, 1, 9, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 4, 1, 10, 7, 2, 10, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 5, 4, 5, 6, 4, 5, false, random, STONE_SELECTOR);
        this.placeBlock(level, eastStairs, 4, 4, 5, chunkBB);
        this.placeBlock(level, westStairs, 7, 4, 5, chunkBB);

        for (var i = 0; i < 4; i++) {
            this.placeBlock(level, southStairs, 5, 0 - i, 6 + i, chunkBB);
            this.placeBlock(level, southStairs, 6, 0 - i, 6 + i, chunkBB);
            this.generateAirBox(level, chunkBB, 5, 0 - i, 7 + i, 6, 0 - i, 9 + i);
        }

        this.generateAirBox(level, chunkBB, 1, -3, 12, 10, -1, 13);
        this.generateAirBox(level, chunkBB, 1, -3, 1, 3, -1, 13);
        this.generateAirBox(level, chunkBB, 1, -3, 1, 9, -1, 5);

        for (var z = 1; z <= 13; z += 2) {
            this.generateBox(level, chunkBB, 1, -3, z, 1, -2, z, false, random, STONE_SELECTOR);
        }

        for (var z = 2; z <= 12; z += 2) {
            this.generateBox(level, chunkBB, 1, -1, z, 3, -1, z, false, random, STONE_SELECTOR);
        }

        this.generateBox(level, chunkBB, 2, -2, 1, 5, -2, 1, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 7, -2, 1, 9, -2, 1, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 6, -3, 1, 6, -3, 1, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 6, -1, 1, 6, -1, 1, false, random, STONE_SELECTOR);

        this.placeBlock(level, Block.TRIPWIRE_HOOK.withProperty("facing", "east").withProperty("attached", "true"),
                1, -3, 8, chunkBB);
        this.placeBlock(level, Block.TRIPWIRE_HOOK.withProperty("facing", "west").withProperty("attached", "true"),
                4, -3, 8, chunkBB);
        var tripwireEastWest = Block.TRIPWIRE.withProperty("east", "true").withProperty("west", "true")
                .withProperty("attached", "true");
        this.placeBlock(level, tripwireEastWest, 2, -3, 8, chunkBB);
        this.placeBlock(level, tripwireEastWest, 3, -3, 8, chunkBB);

        var redstoneWireNS = Block.REDSTONE_WIRE.withProperty("north", "side").withProperty("south", "side");
        this.placeBlock(level, redstoneWireNS, 5, -3, 7, chunkBB);
        this.placeBlock(level, redstoneWireNS, 5, -3, 6, chunkBB);
        this.placeBlock(level, redstoneWireNS, 5, -3, 5, chunkBB);
        this.placeBlock(level, redstoneWireNS, 5, -3, 4, chunkBB);
        this.placeBlock(level, redstoneWireNS, 5, -3, 3, chunkBB);
        this.placeBlock(level, redstoneWireNS, 5, -3, 2, chunkBB);
        this.placeBlock(level, Block.REDSTONE_WIRE.withProperty("north", "side").withProperty("west", "side"),
                5, -3, 1, chunkBB);
        this.placeBlock(level, Block.REDSTONE_WIRE.withProperty("east", "side").withProperty("west", "side"),
                4, -3, 1, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 3, -3, 1, chunkBB);
        if (!this.placedTrap1) {
            this.placedTrap1 = this.createDispenser(level, chunkBB, random, 3, -2, 1, Direction.NORTH);
        }

        this.placeBlock(level, Block.VINE.withProperty("south", "true"), 3, -2, 2, chunkBB);
        this.placeBlock(level, Block.TRIPWIRE_HOOK.withProperty("facing", "north").withProperty("attached", "true"),
                7, -3, 1, chunkBB);
        this.placeBlock(level, Block.TRIPWIRE_HOOK.withProperty("facing", "south").withProperty("attached", "true"),
                7, -3, 5, chunkBB);
        var tripwireNorthSouth = Block.TRIPWIRE.withProperty("north", "true").withProperty("south", "true")
                .withProperty("attached", "true");
        this.placeBlock(level, tripwireNorthSouth, 7, -3, 2, chunkBB);
        this.placeBlock(level, tripwireNorthSouth, 7, -3, 3, chunkBB);
        this.placeBlock(level, tripwireNorthSouth, 7, -3, 4, chunkBB);
        this.placeBlock(level, Block.REDSTONE_WIRE.withProperty("east", "side").withProperty("west", "side"),
                8, -3, 6, chunkBB);
        this.placeBlock(level, Block.REDSTONE_WIRE.withProperty("west", "side").withProperty("south", "side"),
                9, -3, 6, chunkBB);
        this.placeBlock(level, Block.REDSTONE_WIRE.withProperty("north", "side").withProperty("south", "up"),
                9, -3, 5, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 9, -3, 4, chunkBB);
        this.placeBlock(level, redstoneWireNS, 9, -2, 4, chunkBB);
        if (!this.placedTrap2) {
            this.placedTrap2 = this.createDispenser(level, chunkBB, random, 9, -2, 3, Direction.WEST);
        }

        this.placeBlock(level, Block.VINE.withProperty("east", "true"), 8, -1, 3, chunkBB);
        this.placeBlock(level, Block.VINE.withProperty("east", "true"), 8, -2, 3, chunkBB);
        if (!this.placedMainChest) {
            this.placedMainChest = this.createChest(level, chunkBB, random, 8, -3, 3);
        }

        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 9, -3, 2, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 8, -3, 1, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 4, -3, 5, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 5, -2, 5, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 5, -1, 5, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 6, -3, 5, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 7, -2, 5, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 7, -1, 5, chunkBB);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 8, -3, 5, chunkBB);
        this.generateBox(level, chunkBB, 9, -1, 1, 9, -1, 5, false, random, STONE_SELECTOR);
        this.generateAirBox(level, chunkBB, 8, -3, 8, 10, -1, 10);
        this.placeBlock(level, Block.CHISELED_STONE_BRICKS, 8, -2, 11, chunkBB);
        this.placeBlock(level, Block.CHISELED_STONE_BRICKS, 9, -2, 11, chunkBB);
        this.placeBlock(level, Block.CHISELED_STONE_BRICKS, 10, -2, 11, chunkBB);
        var lever = Block.LEVER.withProperty("facing", "north").withProperty("face", "wall");
        this.placeBlock(level, lever, 8, -2, 12, chunkBB);
        this.placeBlock(level, lever, 9, -2, 12, chunkBB);
        this.placeBlock(level, lever, 10, -2, 12, chunkBB);
        this.generateBox(level, chunkBB, 8, -3, 8, 8, -3, 10, false, random, STONE_SELECTOR);
        this.generateBox(level, chunkBB, 10, -3, 8, 10, -3, 10, false, random, STONE_SELECTOR);
        this.placeBlock(level, Block.MOSSY_COBBLESTONE, 10, -2, 9, chunkBB);
        this.placeBlock(level, redstoneWireNS, 8, -2, 9, chunkBB);
        this.placeBlock(level, redstoneWireNS, 8, -2, 10, chunkBB);
        var redstoneWireCross = Block.REDSTONE_WIRE.withProperty("north", "side").withProperty("south", "side")
                .withProperty("east", "side").withProperty("west", "side");
        this.placeBlock(level, redstoneWireCross, 10, -1, 9, chunkBB);
        this.placeBlock(level, Block.STICKY_PISTON.withProperty("facing", "up"), 9, -2, 8, chunkBB);
        this.placeBlock(level, Block.STICKY_PISTON.withProperty("facing", "west"), 10, -2, 8, chunkBB);
        this.placeBlock(level, Block.STICKY_PISTON.withProperty("facing", "west"), 10, -1, 8, chunkBB);
        this.placeBlock(level, Block.REPEATER.withProperty("facing", "north"), 10, -2, 10, chunkBB);
        if (!this.placedHiddenChest) {
            this.placedHiddenChest = this.createChest(level, chunkBB, random, 9, -3, 10);
        }
    }

    private static final class MossStoneSelector extends BlockSelector {
        @Override
        protected void next(RandomSource random, int worldX, int worldY, int worldZ, boolean isEdge) {
            this.next = random.nextFloat() < 0.4F ? Block.COBBLESTONE : Block.MOSSY_COBBLESTONE;
        }
    }
}
