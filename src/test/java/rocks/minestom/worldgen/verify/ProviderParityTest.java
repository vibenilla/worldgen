package rocks.minestom.worldgen.verify;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import rocks.minestom.worldgen.feature.valueproviders.IntProvider;
import rocks.minestom.worldgen.random.XoroshiroRandomSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Compares int provider sampling against the vanilla server classes: same
 * seed, same draw sequence, same values.
 */
final class ProviderParityTest {

    @Test
    void intProvidersMatchVanilla() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();

        record Case(String json, net.minecraft.util.valueproviders.IntProvider vanilla) {
        }

        var cases = new Case[]{
                new Case("{\"type\":\"minecraft:uniform\",\"min_inclusive\":3,\"max_inclusive\":9}",
                        net.minecraft.util.valueproviders.UniformInt.of(3, 9)),
                new Case("{\"type\":\"minecraft:uniform\",\"min_inclusive\":5,\"max_inclusive\":5}",
                        net.minecraft.util.valueproviders.UniformInt.of(5, 5)),
                new Case("{\"type\":\"minecraft:trapezoid\",\"min\":-5,\"max\":5,\"plateau\":0}",
                        net.minecraft.util.valueproviders.TrapezoidInt.of(-5, 5, 0)),
                new Case("{\"type\":\"minecraft:trapezoid\",\"min\":0,\"max\":24,\"plateau\":6}",
                        net.minecraft.util.valueproviders.TrapezoidInt.of(0, 24, 6)),
                new Case("{\"type\":\"minecraft:biased_to_bottom\",\"min_inclusive\":1,\"max_inclusive\":10}",
                        net.minecraft.util.valueproviders.BiasedToBottomInt.of(1, 10)),
                new Case("{\"type\":\"minecraft:clamped\",\"min_inclusive\":2,\"max_inclusive\":6,\"source\":{\"type\":\"minecraft:uniform\",\"min_inclusive\":0,\"max_inclusive\":10}}",
                        net.minecraft.util.valueproviders.ClampedInt.of(net.minecraft.util.valueproviders.UniformInt.of(0, 10), 2, 6)),
        };

        for (var testCase : cases) {
            var ours = IntProvider.fromJson(JsonParser.parseString(testCase.json()));
            var ourRandom = new XoroshiroRandomSource(555L);
            var vanillaRandom = new net.minecraft.world.level.levelgen.XoroshiroRandomSource(555L);
            for (var i = 0; i < 500; i++) {
                assertEquals(testCase.vanilla().sample(vanillaRandom), ours.sample(ourRandom),
                        testCase.json() + " draw " + i);
            }
        }
    }
}
