package rocks.minestom.worldgen.terrain;

import rocks.minestom.worldgen.VMath;
import rocks.minestom.worldgen.structure.TerrainAdjustment;
import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.List;

/**
 * Vanilla's {@code Beardifier}: the density contribution that molds the noise
 * terrain around structures - support platforms under villages (BEARD_THIN),
 * cleared boxes for ancient cities (BEARD_BOX), encapsulating rock around
 * trial chambers (ENCAPSULATE) and raised cover over trail ruins (BURY).
 * <p>
 * The value is computed per block (never interpolated) and added to the
 * interpolated final density before the solid/air decision, exactly like
 * vanilla's {@code cacheAllInCell(add(finalDensity, beardifier))} filler.
 */
public final class Beardifier {
    public static final int BEARD_KERNEL_RADIUS = 12;
    private static final int BEARD_KERNEL_SIZE = 24;
    private static final float[] BEARD_KERNEL = new float[13824];

    static {
        for (var zi = 0; zi < BEARD_KERNEL_SIZE; zi++) {
            for (var xi = 0; xi < BEARD_KERNEL_SIZE; xi++) {
                for (var yi = 0; yi < BEARD_KERNEL_SIZE; yi++) {
                    BEARD_KERNEL[zi * BEARD_KERNEL_SIZE * BEARD_KERNEL_SIZE + xi * BEARD_KERNEL_SIZE + yi] =
                            (float) computeBeardContribution(xi - 12, (yi - 12) + 0.5, zi - 12);
                }
            }
        }
    }

    public static final Beardifier EMPTY = new Beardifier(List.of(), List.of(), null);

    /**
     * A structure piece the terrain adapts around (vanilla
     * {@code Beardifier.Rigid}). The ground level is
     * {@code box.minY() + groundLevelDelta}.
     */
    public record Rigid(BoundingBox box, TerrainAdjustment terrainAdjustment, int groundLevelDelta) {
    }

    /**
     * A jigsaw junction of a terrain-adapting structure; contributes a thin
     * beard around the attachment point.
     */
    public record Junction(int sourceX, int sourceGroundY, int sourceZ) {
    }

    private final List<Rigid> pieces;
    private final List<Junction> junctions;
    // Union of the piece/junction boxes inflated by 24 - outside it the
    // contribution is exactly zero, so the fill loop can early-out.
    private final BoundingBox affectedBox;

    private Beardifier(List<Rigid> pieces, List<Junction> junctions, BoundingBox affectedBox) {
        this.pieces = pieces;
        this.junctions = junctions;
        this.affectedBox = affectedBox;
    }

    /**
     * Builds a beardifier from the pieces and junctions collected for a chunk,
     * deriving the affected box like vanilla {@code forStructuresInChunk}.
     */
    public static Beardifier create(List<Rigid> pieces, List<Junction> junctions) {
        if (pieces.isEmpty() && junctions.isEmpty()) {
            return EMPTY;
        }

        BoundingBox union = null;
        for (var rigid : pieces) {
            union = include(union, rigid.box());
        }
        for (var junction : junctions) {
            union = include(union, new BoundingBox(
                    junction.sourceX(), junction.sourceGroundY(), junction.sourceZ(),
                    junction.sourceX(), junction.sourceGroundY(), junction.sourceZ()));
        }

        var affectedBox = new BoundingBox(
                union.minX() - 24, union.minY() - 24, union.minZ() - 24,
                union.maxX() + 24, union.maxY() + 24, union.maxZ() + 24);
        return new Beardifier(List.copyOf(pieces), List.copyOf(junctions), affectedBox);
    }

    private static BoundingBox include(BoundingBox union, BoundingBox box) {
        if (union == null) {
            return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
        }
        union.encapsulate(box);
        return union;
    }

    /** Vanilla {@code Beardifier.compute}. */
    public double compute(int blockX, int blockY, int blockZ) {
        var affectedBox = this.affectedBox;
        if (affectedBox == null
                || blockX < affectedBox.minX() || blockX > affectedBox.maxX()
                || blockY < affectedBox.minY() || blockY > affectedBox.maxY()
                || blockZ < affectedBox.minZ() || blockZ > affectedBox.maxZ()) {
            return 0.0;
        }

        var noiseValue = 0.0;
        for (var rigid : this.pieces) {
            var box = rigid.box();
            var groundLevelDelta = rigid.groundLevelDelta();
            var dx = Math.max(0, Math.max(box.minX() - blockX, blockX - box.maxX()));
            var dz = Math.max(0, Math.max(box.minZ() - blockZ, blockZ - box.maxZ()));
            var groundY = box.minY() + groundLevelDelta;
            var dyToGround = blockY - groundY;

            var dy = switch (rigid.terrainAdjustment()) {
                case NONE -> 0;
                case BURY, BEARD_THIN -> dyToGround;
                case BEARD_BOX -> Math.max(0, Math.max(groundY - blockY, blockY - box.maxY()));
                case ENCAPSULATE -> Math.max(0, Math.max(box.minY() - blockY, blockY - box.maxY()));
            };

            noiseValue += switch (rigid.terrainAdjustment()) {
                case NONE -> 0.0;
                case BURY -> getBuryContribution(dx, dy / 2.0, dz);
                case BEARD_THIN, BEARD_BOX -> getBeardContribution(dx, dy, dz, dyToGround) * 0.8;
                case ENCAPSULATE -> getBuryContribution(dx / 2.0, dy / 2.0, dz / 2.0) * 0.8;
            };
        }

        for (var junction : this.junctions) {
            var dx = blockX - junction.sourceX();
            var dy = blockY - junction.sourceGroundY();
            var dz = blockZ - junction.sourceZ();
            noiseValue += getBeardContribution(dx, dy, dz, dy) * 0.4;
        }

        return noiseValue;
    }

    private static double getBuryContribution(double dx, double dy, double dz) {
        var distance = Math.sqrt(lengthSquared(dx, dy, dz));
        return VMath.clampedMap(distance, 0.0, 6.0, 1.0, 0.0);
    }

    private static double getBeardContribution(int dx, int dy, int dz, int yToGround) {
        var xi = dx + 12;
        var yi = dy + 12;
        var zi = dz + 12;
        if (isInKernelRange(xi) && isInKernelRange(yi) && isInKernelRange(zi)) {
            var dyWithOffset = yToGround + 0.5;
            var distanceSqr = lengthSquared(dx, dyWithOffset, dz);
            var value = -dyWithOffset * fastInvSqrt(distanceSqr / 2.0) / 2.0;
            return value * BEARD_KERNEL[zi * BEARD_KERNEL_SIZE * BEARD_KERNEL_SIZE + xi * BEARD_KERNEL_SIZE + yi];
        }
        return 0.0;
    }

    private static boolean isInKernelRange(int i) {
        return i >= 0 && i < BEARD_KERNEL_SIZE;
    }

    private static double computeBeardContribution(int dx, double dy, int dz) {
        var distanceSqr = lengthSquared(dx, dy, dz);
        return Math.pow(Math.E, -distanceSqr / 16.0);
    }

    private static double lengthSquared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    /** Vanilla {@code Mth.fastInvSqrt} - bit-exact, not {@code 1 / sqrt}. */
    private static double fastInvSqrt(double x) {
        var half = 0.5 * x;
        var bits = Double.doubleToRawLongBits(x);
        bits = 6910469410427058090L - (bits >> 1);
        x = Double.longBitsToDouble(bits);
        return x * (1.5 - half * x * x);
    }
}
