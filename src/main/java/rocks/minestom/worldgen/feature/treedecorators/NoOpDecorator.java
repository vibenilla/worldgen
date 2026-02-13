package rocks.minestom.worldgen.feature.treedecorators;

public class NoOpDecorator implements TreeDecorator {
    public static final NoOpDecorator INSTANCE = new NoOpDecorator();

    private NoOpDecorator() {}

    @Override
    public void place(Context context) {
        // Do nothing
    }
}
