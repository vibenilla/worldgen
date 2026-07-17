package rocks.minestom.worldgen.feature;

import net.minestom.server.coordinate.BlockVec;

/**
 * Block position hashing like vanilla's {@code Vec3i}. Vanilla keeps tree
 * logs/leaves in JDK {@code HashSet}s whose iteration order leaks into
 * generation (decorator log ordering, the leaf distance update), so parity
 * requires hashing positions exactly like vanilla does.
 */
public record VanillaPos(int x, int y, int z) {
    public static VanillaPos of(BlockVec position) {
        return new VanillaPos(position.blockX(), position.blockY(), position.blockZ());
    }

    public BlockVec toBlockVec() {
        return new BlockVec(this.x, this.y, this.z);
    }

    @Override
    public int hashCode() {
        return (this.y + this.z * 31) * 31 + this.x;
    }
}
