package rocks.minestom.worldgen.feature;

import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.configurations.NoneFeatureConfiguration;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.Set;

/**
 * Port of vanilla {@code MonsterRoomFeature} (dungeons): a small
 * cobblestone/mossy-cobblestone room with a spawner and up to two chests.
 * Loot and spawner NBT are not written, but every vanilla random call is kept
 * so block placement matches vanilla exactly.
 */
public final class MonsterRoomFeature implements Feature<NoneFeatureConfiguration> {
    private static final Block AIR = Block.CAVE_AIR;
    /** Number of entries in vanilla's MOBS array (skeleton, zombie, zombie, spider). */
    private static final int MOB_COUNT = 4;
    /** #minecraft:features_cannot_replace */
    private static final Set<Key> CANNOT_REPLACE = Set.of(
            Key.key("minecraft:bedrock"), Key.key("minecraft:spawner"), Key.key("minecraft:chest"),
            Key.key("minecraft:end_portal_frame"), Key.key("minecraft:reinforced_deepslate"),
            Key.key("minecraft:trial_spawner"), Key.key("minecraft:vault"));

    @Override
    public <T extends Block.Getter & Block.Setter> boolean place(FeaturePlaceContext<NoneFeatureConfiguration, T> context) {
        var origin = context.origin();
        var random = context.random();
        var level = context.accessor();
        var xRadius = random.nextInt(2) + 2;
        var minX = -xRadius - 1;
        var maxX = xRadius + 1;
        var zRadius = random.nextInt(2) + 2;
        var minZ = -zRadius - 1;
        var maxZ = zRadius + 1;
        var holeCount = 0;

        for (var dx = minX; dx <= maxX; dx++) {
            for (var dy = -1; dy <= 4; dy++) {
                for (var dz = minZ; dz <= maxZ; dz++) {
                    var blockX = origin.blockX() + dx;
                    var blockY = origin.blockY() + dy;
                    var blockZ = origin.blockZ() + dz;
                    var block = level.getBlock(blockX, blockY, blockZ);
                    if ((dy == -1 || dy == 4) && !block.solid()) {
                        return false;
                    }

                    if ((dx == minX || dx == maxX || dz == minZ || dz == maxZ) && dy == 0
                            && block.air() && level.getBlock(blockX, blockY + 1, blockZ).air()) {
                        holeCount++;
                    }
                }
            }
        }

        if (holeCount < 1 || holeCount > 5) {
            return false;
        }

        for (var dx = minX; dx <= maxX; dx++) {
            for (var dy = 3; dy >= -1; dy--) {
                for (var dz = minZ; dz <= maxZ; dz++) {
                    var blockX = origin.blockX() + dx;
                    var blockY = origin.blockY() + dy;
                    var blockZ = origin.blockZ() + dz;
                    var state = level.getBlock(blockX, blockY, blockZ);
                    if (dx == minX || dy == -1 || dz == minZ || dx == maxX || dy == 4 || dz == maxZ) {
                        if (blockY >= context.minY() && !level.getBlock(blockX, blockY - 1, blockZ).solid()) {
                            level.setBlock(blockX, blockY, blockZ, AIR);
                        } else if (state.solid() && !state.compare(Block.CHEST)) {
                            if (dy == -1 && random.nextInt(4) != 0) {
                                this.safeSetBlock(level, blockX, blockY, blockZ, Block.MOSSY_COBBLESTONE);
                            } else {
                                this.safeSetBlock(level, blockX, blockY, blockZ, Block.COBBLESTONE);
                            }
                        }
                    } else if (!state.compare(Block.CHEST) && !state.compare(Block.SPAWNER)) {
                        this.safeSetBlock(level, blockX, blockY, blockZ, AIR);
                    }
                }
            }
        }

        for (var chestIndex = 0; chestIndex < 2; chestIndex++) {
            for (var attempt = 0; attempt < 3; attempt++) {
                var chestX = origin.blockX() + random.nextInt(xRadius * 2 + 1) - xRadius;
                var chestY = origin.blockY();
                var chestZ = origin.blockZ() + random.nextInt(zRadius * 2 + 1) - zRadius;
                var chestPos = new BlockVec(chestX, chestY, chestZ);
                if (level.getBlock(chestPos).air()) {
                    var wallCount = 0;

                    for (var direction : Direction.HORIZONTAL) {
                        if (level.getBlock(direction.relative(chestPos)).solid()) {
                            wallCount++;
                        }
                    }

                    if (wallCount == 1) {
                        this.safeSetBlock(level, chestX, chestY, chestZ, reorient(level, chestPos, Block.CHEST));
                        random.nextLong(); // chest loot table seed
                        break;
                    }
                }
            }
        }

        this.safeSetBlock(level, origin.blockX(), origin.blockY(), origin.blockZ(), Block.SPAWNER);
        if (level.getBlock(origin).compare(Block.SPAWNER)) {
            random.nextInt(MOB_COUNT); // Util.getRandom over the spawner mob types
        }

        return true;
    }

    /**
     * Port of {@code StructurePiece.reorient}: faces the chest away from its
     * single solid neighbor, or towards an open side otherwise.
     */
    static Block reorient(Block.Getter level, BlockVec pos, Block chest) {
        Direction solidNeighbor = null;

        for (var direction : Direction.HORIZONTAL) {
            var state = level.getBlock(direction.relative(pos));
            if (state.compare(Block.CHEST)) {
                return chest;
            }

            if (isSolidRender(state)) {
                if (solidNeighbor != null) {
                    solidNeighbor = null;
                    break;
                }

                solidNeighbor = direction;
            }
        }

        if (solidNeighbor != null) {
            return chest.withProperty("facing", solidNeighbor.opposite().serializedName());
        }

        var facing = Direction.fromSerializedName(chest.getProperty("facing"));
        if (isSolidRender(level.getBlock(facing.relative(pos)))) {
            facing = facing.opposite();
        }

        if (isSolidRender(level.getBlock(facing.relative(pos)))) {
            facing = clockWise(facing);
        }

        if (isSolidRender(level.getBlock(facing.relative(pos)))) {
            facing = facing.opposite();
        }

        return chest.withProperty("facing", facing.serializedName());
    }

    private static Direction clockWise(Direction direction) {
        return switch (direction) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> direction;
        };
    }

    /**
     * Approximation of vanilla {@code isSolidRender()} for the blocks around
     * a dungeon: any solid full block except spawners and chests.
     */
    private static boolean isSolidRender(Block block) {
        return block.solid() && !block.compare(Block.SPAWNER) && !block.compare(Block.CHEST);
    }

    private <T extends Block.Getter & Block.Setter> void safeSetBlock(T level, int x, int y, int z, Block block) {
        if (!CANNOT_REPLACE.contains(level.getBlock(x, y, z).key())) {
            level.setBlock(x, y, z, block);
        }
    }
}
