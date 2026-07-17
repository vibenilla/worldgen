package rocks.minestom.worldgen.verify;

import org.junit.jupiter.api.Test;

/**
 * Prints vanilla's draw sequence for the stone-variant blobs of chunk (-8,-7)
 * so blob origins can be compared with this library's placement.
 */
final class StoneBlobTraceTest {

    @Test
    void printVanillaStoneBlobDraws() {
        var random = new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.XoroshiroRandomSource(0L));
        var decorationSeed = random.setDecorationSeed(123456789L, -8 * 16, -7 * 16);

        // step 6 global indices: granite_upper=2 granite_lower=3 diorite_upper=4
        // diorite_lower=5 andesite_upper=6 andesite_lower=7 (FeatureOrderParityTest)
        String[] names = {"granite_upper", "granite_lower", "diorite_upper", "diorite_lower", "andesite_upper", "andesite_lower"};
        for (var index = 2; index <= 7; index++) {
            random.setFeatureSeed(decorationSeed, index, 6);
            var name = names[index - 2];
            var isUpper = name.endsWith("upper");
            if (isUpper) {
                // rarity_filter(6): nextFloat < 1/6
                var roll = random.nextFloat();
                if (roll >= 1.0F / 6.0F) {
                    System.out.println(name + " idx=" + index + " SKIPPED rarity roll=" + roll);
                    continue;
                }
                var dx = random.nextInt(16);
                var dz = random.nextInt(16);
                var y = 64 + random.nextInt(65);
                System.out.println(name + " idx=" + index + " pos=(" + (-128 + dx) + "," + y + "," + (-112 + dz) + ")");
            } else {
                for (var position = 0; position < 2; position++) {
                    var dx = random.nextInt(16);
                    var dz = random.nextInt(16);
                    var y = random.nextInt(61);
                    System.out.println(name + " idx=" + index + " pos" + position + "=(" + (-128 + dx) + "," + y + "," + (-112 + dz) + ")");
                    // feature draws for size 64: dir float, y0, y1, 64 radii
                    random.nextFloat();
                    random.nextInt(3);
                    random.nextInt(3);
                    for (var i = 0; i < 64; i++) {
                        random.nextDouble();
                    }
                }
            }
        }
    }
}
