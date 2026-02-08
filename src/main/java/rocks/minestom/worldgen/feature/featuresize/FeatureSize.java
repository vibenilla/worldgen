package rocks.minestom.worldgen.feature.featuresize;

import java.util.OptionalInt;

public interface FeatureSize {

    int getSizeAtHeight(int treeHeight, int height);

    OptionalInt minClippedHeight();
}
