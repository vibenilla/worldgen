package rocks.minestom.worldgen.structure.pool;

/**
 * A pool element that places nothing and terminates jigsaw expansion.
 *
 * <p>When the assembler picks an empty element, it stops trying to place
 * pieces at that jigsaw connection. This is used in pools where expansion
 * should sometimes end (e.g., village streets that don't always continue).
 */
public final class EmptyPoolElement implements PoolElement {
    public static final EmptyPoolElement INSTANCE = new EmptyPoolElement();

    private EmptyPoolElement() {
    }
}
