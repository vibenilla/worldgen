package rocks.minestom.worldgen.feature.stateproviders;

import com.google.gson.JsonObject;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.feature.placement.PlacementContext;
import rocks.minestom.worldgen.feature.placement.PlacementModifiers;
import rocks.minestom.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of vanilla's {@code RuleBasedStateProvider}: the first rule whose
 * predicate matches provides the state, otherwise the fallback does. Without a
 * fallback, vanilla returns the block already at the position.
 */
public record RuleBasedStateProvider(BlockStateProvider fallback, List<Rule> rules) implements BlockStateProvider {

    @Override
    public Block getState(RandomSource random, BlockVec position) {
        // Without level access the rules cannot be evaluated; use the fallback.
        return this.fallback != null ? this.fallback.getState(random, position) : Block.AIR;
    }

    @Override
    public Block getState(Block.Getter accessor, RandomSource random, BlockVec position) {
        var state = this.getOptionalState(accessor, random, position);
        return state != null ? state : accessor.getBlock(position);
    }

    @Override
    public Block getOptionalState(Block.Getter accessor, RandomSource random, BlockVec position) {
        var context = new PlacementContext(
                accessor, 0, 0, 0, 0, null, null,
                Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2, 0,
                null, null, null);
        for (var rule : this.rules) {
            if (rule.ifTrue().test(context, position)) {
                return rule.then().getState(accessor, random, position);
            }
        }

        return this.fallback != null ? this.fallback.getState(accessor, random, position) : null;
    }

    public static RuleBasedStateProvider fromJson(JsonObject object) {
        BlockStateProvider fallback = null;
        if (object.has("fallback")) {
            fallback = BlockStateProviders.fromJson(object.get("fallback"));
        }

        var rules = new ArrayList<Rule>();
        for (var ruleElement : object.getAsJsonArray("rules")) {
            var ruleObject = ruleElement.getAsJsonObject();
            rules.add(new Rule(
                    PlacementModifiers.parseBlockPredicate(ruleObject.getAsJsonObject("if_true")),
                    BlockStateProviders.fromJson(ruleObject.get("then"))));
        }

        return new RuleBasedStateProvider(fallback, List.copyOf(rules));
    }

    public record Rule(PlacementModifiers.BlockPredicate ifTrue, BlockStateProvider then) {
    }
}
