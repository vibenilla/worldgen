package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.Feature;
import rocks.minestom.worldgen.feature.FeaturePlaceContext;
import rocks.minestom.worldgen.feature.Features;
import rocks.minestom.worldgen.feature.RandomSelectorFeature;
import rocks.minestom.worldgen.random.WorldgenRandom;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;
import rocks.minestom.worldgen.structure.context.BlockTagManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Places the newly implemented features into a synthetic cave world and
 * reports how many blocks each modified, to catch runtime crashes.
 */
public final class FeaturePlaceSmoke {

    static final class FakeWorld implements Block.Getter, Block.Setter {
        final Map<BlockVec, Block> changes = new HashMap<>();

        @Override
        public Block getBlock(int x, int y, int z, Condition condition) {
            var cached = this.changes.get(new BlockVec(x, y, z));
            if (cached != null) {
                return cached;
            }

            // Cave between y=11 and y=20, stone otherwise; snow layer on top of the upper stone.
            if (y > 40) {
                return Block.AIR;
            }
            if (y == 40) {
                return Block.SNOW_BLOCK;
            }
            if (y >= 11 && y <= 20) {
                return Block.AIR;
            }
            return Block.STONE;
        }

        @Override
        public void setBlock(int x, int y, int z, Block block) {
            this.changes.put(new BlockVec(x, y, z), block);
        }
    }

    public static void main(String[] args) throws Exception {
        var root = Path.of("data/mc/datapack");
        var featuresDir = root.resolve("data/minecraft/worldgen/configured_feature");
        var blockTags = new BlockTagManager(root);

        var cases = new String[][]{
                {"forest_rock", "41"},       // block_blob on the surface
                {"ice_spike", "41"},         // spike on snow_block
                {"pointed_dripstone", "15"}, // speleothem in the cave
                {"dripstone_cluster", "15"}, // speleothem_cluster in the cave
                {"lake_lava", "30"},         // lake in stone
                {"sulfur_spring", "15"},     // weighted_random_selector
                {"sulfur_pool", "30"},       // sequence -> lake, embedded in stone
                {"sulfur_spike_cluster", "15"},
        };

        for (var testCase : cases) {
            var name = testCase[0];
            var originY = Integer.parseInt(testCase[1]);
            var json = JsonParser.parseString(Files.readString(featuresDir.resolve(name + ".json")));
            var configured = Features.parseConfiguredFeature(json, blockTags);
            if (configured == null) {
                System.out.println(name + ": UNPARSED");
                continue;
            }

            var world = new FakeWorld();
            var random = new WorldgenRandom(new XoroshiroRandomSource(0L));
            random.setDecorationSeed(123456789L, 0, 0);
            random.setFeatureSeed(123456789L, 3, 5);

            var placedTotal = 0;
            var successes = 0;
            for (var attempt = 0; attempt < 16; attempt++) {
                var origin = new BlockVec(attempt * 32, originY, attempt * 32);
                var context = new FeaturePlaceContext<>(world, random, origin, (rocks.minestom.worldgen.feature.FeatureConfiguration) configured.config(), 123456789L, -64, 319, 63);
                boolean ok;
                try {
                    var impl = configured.feature();
                    if (impl instanceof RandomSelectorFeature selector) {
                        ok = selector.place((FeaturePlaceContext) context, null);
                    } else {
                        ok = ((Feature) impl).place((FeaturePlaceContext) context);
                    }
                } catch (Exception exception) {
                    System.out.println(name + ": EXCEPTION " + exception);
                    exception.printStackTrace();
                    ok = false;
                    break;
                }

                if (ok) {
                    successes++;
                }
            }
            placedTotal = world.changes.size();
            System.out.println(name + ": successes=" + successes + "/16 blocksChanged=" + placedTotal);
        }
    }
}
