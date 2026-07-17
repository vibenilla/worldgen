package rocks.minestom.worldgen.structure.scattered;

import rocks.minestom.worldgen.random.RandomSource;
import rocks.minestom.worldgen.structure.template.BoundingBox;

/**
 * Uniform view over the four scattered feature piece types so
 * {@link ScatteredFeaturePlacer} can treat a chunk's structure start
 * generically regardless of which vanilla piece it wraps.
 */
sealed interface AssembledPiece {
    BoundingBox boundingBox();

    void postProcess(ScatteredFeatureLevel level, RandomSource random, BoundingBox chunkBB, long levelSeed);

    record OfDesertPyramid(DesertPyramidPiece piece) implements AssembledPiece {
        @Override
        public BoundingBox boundingBox() {
            return this.piece.boundingBox();
        }

        @Override
        public void postProcess(ScatteredFeatureLevel level, RandomSource random, BoundingBox chunkBB, long levelSeed) {
            this.piece.postProcess(level, random, chunkBB, levelSeed);
            DesertPyramidPiece.afterPlace(level, chunkBB, levelSeed, this.piece);
        }
    }

    record OfJungleTemple(JungleTemplePiece piece) implements AssembledPiece {
        @Override
        public BoundingBox boundingBox() {
            return this.piece.boundingBox();
        }

        @Override
        public void postProcess(ScatteredFeatureLevel level, RandomSource random, BoundingBox chunkBB, long levelSeed) {
            this.piece.postProcess(level, random, chunkBB);
        }
    }

    record OfSwampHut(SwampHutPiece piece) implements AssembledPiece {
        @Override
        public BoundingBox boundingBox() {
            return this.piece.boundingBox();
        }

        @Override
        public void postProcess(ScatteredFeatureLevel level, RandomSource random, BoundingBox chunkBB, long levelSeed) {
            this.piece.postProcess(level, chunkBB);
        }
    }

    record OfBuriedTreasure(BuriedTreasurePiece piece) implements AssembledPiece {
        @Override
        public BoundingBox boundingBox() {
            return this.piece.boundingBox();
        }

        @Override
        public void postProcess(ScatteredFeatureLevel level, RandomSource random, BoundingBox chunkBB, long levelSeed) {
            this.piece.postProcess(level, random);
        }
    }
}
