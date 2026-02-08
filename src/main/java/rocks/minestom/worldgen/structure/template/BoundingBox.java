package rocks.minestom.worldgen.structure.template;

import net.minestom.server.coordinate.BlockVec;

/**
 * An axis-aligned bounding box defined by minimum and maximum coordinates.
 *
 * <p>Used extensively in structure generation for:
 * <ul>
 *   <li>Collision detection between structure pieces
 *   <li>Determining which chunks a structure overlaps
 *   <li>Enforcing maximum distance constraints
 * </ul>
 *
 * <p>The bounding box is mutable - use {@link #encapsulate} to grow it
 * to include additional positions or boxes.
 */
public final class BoundingBox {
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public BoundingBox(BlockVec pos) {
        this(pos.blockX(), pos.blockY(), pos.blockZ(), pos.blockX(), pos.blockY(), pos.blockZ());
    }

    public static BoundingBox fromCorners(BlockVec corner1, BlockVec corner2) {
        return new BoundingBox(
                Math.min(corner1.blockX(), corner2.blockX()),
                Math.min(corner1.blockY(), corner2.blockY()),
                Math.min(corner1.blockZ(), corner2.blockZ()),
                Math.max(corner1.blockX(), corner2.blockX()),
                Math.max(corner1.blockY(), corner2.blockY()),
                Math.max(corner1.blockZ(), corner2.blockZ())
        );
    }

    public static BoundingBox fromOriginAndSize(BlockVec origin, BlockVec size) {
        return new BoundingBox(
                origin.blockX(),
                origin.blockY(),
                origin.blockZ(),
                origin.blockX() + size.blockX() - 1,
                origin.blockY() + size.blockY() - 1,
                origin.blockZ() + size.blockZ() - 1
        );
    }

    public boolean intersects(BoundingBox other) {
        return this.maxX >= other.minX
                && this.minX <= other.maxX
                && this.maxZ >= other.minZ
                && this.minZ <= other.maxZ
                && this.maxY >= other.minY
                && this.minY <= other.maxY;
    }

    public boolean isInside(BlockVec pos) {
        return pos.blockX() >= this.minX && pos.blockX() <= this.maxX
                && pos.blockY() >= this.minY && pos.blockY() <= this.maxY
                && pos.blockZ() >= this.minZ && pos.blockZ() <= this.maxZ;
    }

    public BoundingBox moved(int dx, int dy, int dz) {
        return new BoundingBox(
                this.minX + dx,
                this.minY + dy,
                this.minZ + dz,
                this.maxX + dx,
                this.maxY + dy,
                this.maxZ + dz
        );
    }

    public void encapsulate(BlockVec pos) {
        this.minX = Math.min(this.minX, pos.blockX());
        this.minY = Math.min(this.minY, pos.blockY());
        this.minZ = Math.min(this.minZ, pos.blockZ());
        this.maxX = Math.max(this.maxX, pos.blockX());
        this.maxY = Math.max(this.maxY, pos.blockY());
        this.maxZ = Math.max(this.maxZ, pos.blockZ());
    }

    public void encapsulate(BoundingBox other) {
        this.minX = Math.min(this.minX, other.minX);
        this.minY = Math.min(this.minY, other.minY);
        this.minZ = Math.min(this.minZ, other.minZ);
        this.maxX = Math.max(this.maxX, other.maxX);
        this.maxY = Math.max(this.maxY, other.maxY);
        this.maxZ = Math.max(this.maxZ, other.maxZ);
    }

    public int minX() {
        return this.minX;
    }

    public int minY() {
        return this.minY;
    }

    public int minZ() {
        return this.minZ;
    }

    public int maxX() {
        return this.maxX;
    }

    public int maxY() {
        return this.maxY;
    }

    public int maxZ() {
        return this.maxZ;
    }

    public int getXSpan() {
        return this.maxX - this.minX + 1;
    }

    public int getYSpan() {
        return this.maxY - this.minY + 1;
    }

    public int getZSpan() {
        return this.maxZ - this.minZ + 1;
    }

    public BlockVec getCenter() {
        return new BlockVec(
                (this.minX + this.maxX) / 2,
                (this.minY + this.maxY) / 2,
                (this.minZ + this.maxZ) / 2
        );
    }

    @Override
    public String toString() {
        return "BoundingBox[(" + this.minX + ", " + this.minY + ", " + this.minZ + ") -> (" + this.maxX + ", " + this.maxY + ", " + this.maxZ + ")]";
    }
}
