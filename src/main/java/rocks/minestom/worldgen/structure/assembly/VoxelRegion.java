package rocks.minestom.worldgen.structure.assembly;

import rocks.minestom.worldgen.structure.template.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The free-space region vanilla models with {@code VoxelShape} boolean joins
 * during jigsaw assembly, represented exactly as a set of disjoint
 * axis-aligned boxes in double coordinates.
 *
 * <p>All coordinates are integers or integers ± 0.25 (the vanilla deflate), so
 * the region algebra is exact:
 * <ul>
 * <li>{@code Shapes.joinIsNotEmpty(free, box, ONLY_SECOND)} ≡ {@code !contains(box)}
 * <li>{@code Shapes.joinUnoptimized(free, box, ONLY_FIRST)} ≡ {@code subtract(box)}
 * </ul>
 */
final class VoxelRegion {
    /** Disjoint boxes as {minX, minY, minZ, maxX, maxY, maxZ}. */
    private final List<double[]> boxes = new ArrayList<>();

    private VoxelRegion() {
    }

    /** The world-space AABB of a structure bounding box: max is exclusive. */
    static double[] aabbOf(BoundingBox bounds) {
        return new double[]{
                bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1
        };
    }

    static double[] deflate(double[] box, double amount) {
        return new double[]{
                box[0] + amount, box[1] + amount, box[2] + amount,
                box[3] - amount, box[4] - amount, box[5] - amount
        };
    }

    static VoxelRegion of(double[] box) {
        var region = new VoxelRegion();
        if (!isEmpty(box)) {
            region.boxes.add(box);
        }
        return region;
    }

    /** Whether the given box lies entirely inside this region. */
    boolean contains(double[] box) {
        if (isEmpty(box)) {
            return true;
        }

        var remaining = new ArrayList<double[]>();
        remaining.add(box);
        for (var free : this.boxes) {
            if (remaining.isEmpty()) {
                return true;
            }

            var next = new ArrayList<double[]>();
            for (var fragment : remaining) {
                splitOutside(fragment, free, next);
            }
            remaining = next;
        }

        return remaining.isEmpty();
    }

    /** Removes the given box from this region. */
    void subtract(double[] box) {
        if (isEmpty(box)) {
            return;
        }

        var result = new ArrayList<double[]>(this.boxes.size());
        for (var free : this.boxes) {
            splitOutside(free, box, result);
        }
        this.boxes.clear();
        this.boxes.addAll(result);
    }

    /**
     * Appends the parts of {@code box} outside {@code cut} to {@code out}
     * (up to six disjoint fragments).
     */
    private static void splitOutside(double[] box, double[] cut, List<double[]> out) {
        if (!intersects(box, cut)) {
            out.add(box);
            return;
        }

        var minX = box[0];
        var minY = box[1];
        var minZ = box[2];
        var maxX = box[3];
        var maxY = box[4];
        var maxZ = box[5];

        // Below and above (Y slabs)
        if (cut[1] > minY) {
            out.add(new double[]{minX, minY, minZ, maxX, cut[1], maxZ});
            minY = cut[1];
        }
        if (cut[4] < maxY) {
            out.add(new double[]{minX, cut[4], minZ, maxX, maxY, maxZ});
            maxY = cut[4];
        }
        // X slabs of the remaining Y band
        if (cut[0] > minX) {
            out.add(new double[]{minX, minY, minZ, cut[0], maxY, maxZ});
            minX = cut[0];
        }
        if (cut[3] < maxX) {
            out.add(new double[]{cut[3], minY, minZ, maxX, maxY, maxZ});
            maxX = cut[3];
        }
        // Z slabs of the core
        if (cut[2] > minZ) {
            out.add(new double[]{minX, minY, minZ, maxX, maxY, cut[2]});
        }
        if (cut[5] < maxZ) {
            out.add(new double[]{minX, minY, cut[5], maxX, maxY, maxZ});
        }
    }

    private static boolean intersects(double[] a, double[] b) {
        return a[0] < b[3] && a[3] > b[0]
                && a[1] < b[4] && a[4] > b[1]
                && a[2] < b[5] && a[5] > b[2];
    }

    private static boolean isEmpty(double[] box) {
        return box[0] >= box[3] || box[1] >= box[4] || box[2] >= box[5];
    }
}
