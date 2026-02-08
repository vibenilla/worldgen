package rocks.minestom.worldgen.structure.pool;

import java.util.List;

/**
 * A pool element containing multiple elements placed at the same location.
 *
 * <p>Used for layered structures where multiple templates combine, such as:
 * <ul>
 *   <li>Pillager outpost watchtower (base + overgrown variant)
 *   <li>Structures with separate decoration layers
 * </ul>
 *
 * <p>All contained elements are placed at the same position, allowing one
 * to add details or weathering on top of another.
 *
 * @param elements   the elements to place together
 * @param projection placement projection mode
 */
public record ListPoolElement(List<PoolElement> elements, String projection) implements PoolElement {
}
