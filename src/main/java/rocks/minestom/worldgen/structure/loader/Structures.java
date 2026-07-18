package rocks.minestom.worldgen.structure.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.structure.JigsawStructure;
import rocks.minestom.worldgen.structure.SimpleStructure;
import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.TerrainAdjustment;
import rocks.minestom.worldgen.structure.endcity.EndCityStructure;
import rocks.minestom.worldgen.structure.fortress.FortressStructure;
import rocks.minestom.worldgen.structure.igloo.IglooStructure;
import rocks.minestom.worldgen.structure.mineshaft.MineshaftStructure;
import rocks.minestom.worldgen.structure.mineshaft.MineshaftType;
import rocks.minestom.worldgen.structure.pool.PoolAliasBinding;
import rocks.minestom.worldgen.structure.mansion.WoodlandMansionStructure;
import rocks.minestom.worldgen.structure.monument.OceanMonumentStructure;
import rocks.minestom.worldgen.structure.netherfossil.NetherFossilStructure;
import rocks.minestom.worldgen.structure.oceanruin.OceanRuinStructure;
import rocks.minestom.worldgen.structure.ruinedportal.RuinedPortalStructure;
import rocks.minestom.worldgen.structure.scattered.BuriedTreasureStructure;
import rocks.minestom.worldgen.structure.shipwreck.ShipwreckStructure;
import rocks.minestom.worldgen.structure.stronghold.StrongholdStructure;
import rocks.minestom.worldgen.structure.scattered.ScatteredFeatureKind;
import rocks.minestom.worldgen.structure.scattered.ScatteredFeatureStructure;
import rocks.minestom.worldgen.structure.template.LiquidSettings;
import rocks.minestom.worldgen.surface.VerticalAnchor;

import java.util.ArrayList;
import java.util.List;

public final class Structures {
    private static final Codec<SimpleStructureData> SIMPLE_CODEC = StructCodec.struct(
            "biomes", Codec.RAW_VALUE, SimpleStructureData::biomes,
            SimpleStructureData::new
    );

    private static final Codec<MineshaftStructureData> MINESHAFT_CODEC = StructCodec.struct(
            "biomes", Codec.RAW_VALUE, MineshaftStructureData::biomes,
            "mineshaft_type", Codec.STRING.optional("normal"), MineshaftStructureData::mineshaftType,
            MineshaftStructureData::new
    );

    private Structures() {
    }

    public static Structure parseStructure(JsonElement json) {
        if (!json.isJsonObject()) {
            throw new IllegalArgumentException("Structure must be a JSON object");
        }

        var obj = json.getAsJsonObject();
        var typeStr = obj.get("type").getAsString();

        if (typeStr.equals("minecraft:jigsaw")) {
            return parseJigsawStructure(json);
        }

        if (typeStr.equals("minecraft:mineshaft")) {
            return parseMineshaftStructure(json);
        }

        if (typeStr.equals("minecraft:stronghold")) {
            return parseStrongholdStructure(json);
        }

        if (typeStr.equals("minecraft:fortress")) {
            return parseFortressStructure(json);
        }

        if (typeStr.equals("minecraft:desert_pyramid") || typeStr.equals("minecraft:jungle_temple")
                || typeStr.equals("minecraft:swamp_hut")) {
            return parseScatteredFeatureStructure(typeStr, json);
        }

        if (typeStr.equals("minecraft:buried_treasure")) {
            return parseBuriedTreasureStructure(json);
        }

        if (typeStr.equals("minecraft:ocean_monument")) {
            return parseOceanMonumentStructure(json);
        }

        if (typeStr.equals("minecraft:woodland_mansion")) {
            return parseWoodlandMansionStructure(json);
        }

        if (typeStr.equals("minecraft:end_city")) {
            return parseEndCityStructure(json);
        }

        if (typeStr.equals("minecraft:ocean_ruin")) {
            return parseOceanRuinStructure(json);
        }

        if (typeStr.equals("minecraft:shipwreck")) {
            return parseShipwreckStructure(json);
        }

        if (typeStr.equals("minecraft:igloo")) {
            return parseIglooStructure(json);
        }

        if (typeStr.equals("minecraft:nether_fossil")) {
            return parseNetherFossilStructure(json);
        }

        if (typeStr.equals("minecraft:ruined_portal")) {
            return parseRuinedPortalStructure(json);
        }

        return parseSimpleStructure(typeStr, json);
    }

    private static Structure parseNetherFossilStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var obj = json.getAsJsonObject();
        var heightObj = obj.getAsJsonObject("height");
        var minHeight = VerticalAnchor.CODEC.decode(Transcoder.JSON, heightObj.get("min_inclusive")).orElseThrow();
        var maxHeight = VerticalAnchor.CODEC.decode(Transcoder.JSON, heightObj.get("max_inclusive")).orElseThrow();
        var templates = getTemplatesForType("minecraft:nether_fossil");
        var terrainAdaptation = parseTerrainAdaptation(obj);
        return new NetherFossilStructure(biomes, minHeight, maxHeight, templates, terrainAdaptation);
    }

    private static Structure parseOceanRuinStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var obj = json.getAsJsonObject();
        var biomeTemp = obj.get("biome_temp").getAsString().equals("cold")
                ? OceanRuinStructure.BiomeTemp.COLD
                : OceanRuinStructure.BiomeTemp.WARM;
        var largeProbability = obj.get("large_probability").getAsFloat();
        var clusterProbability = obj.get("cluster_probability").getAsFloat();
        return new OceanRuinStructure(biomes, biomeTemp, largeProbability, clusterProbability);
    }

    private static Structure parseRuinedPortalStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var obj = json.getAsJsonObject();
        var setups = new ArrayList<RuinedPortalStructure.Setup>();
        for (var element : obj.getAsJsonArray("setups")) {
            var setup = element.getAsJsonObject();
            setups.add(new RuinedPortalStructure.Setup(
                    RuinedPortalStructure.VerticalPlacement.fromName(setup.get("placement").getAsString()),
                    setup.get("air_pocket_probability").getAsFloat(),
                    setup.get("mossiness").getAsFloat(),
                    setup.get("overgrown").getAsBoolean(),
                    setup.get("vines").getAsBoolean(),
                    setup.get("can_be_cold").getAsBoolean(),
                    setup.get("replace_with_blackstone").getAsBoolean(),
                    setup.get("weight").getAsFloat()));
        }
        return new RuinedPortalStructure(biomes, List.copyOf(setups));
    }

    private static Structure parseShipwreckStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var obj = json.getAsJsonObject();
        var isBeached = obj.has("is_beached") && obj.get("is_beached").getAsBoolean();
        return new ShipwreckStructure(biomes, isBeached);
    }

    private static Structure parseIglooStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        return new IglooStructure(biomes);
    }

    private static Structure parseEndCityStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        return new EndCityStructure(biomes);
    }

    private static Structure parseScatteredFeatureStructure(String typeStr, JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var kind = switch (typeStr) {
            case "minecraft:desert_pyramid" -> ScatteredFeatureKind.DESERT_PYRAMID;
            case "minecraft:jungle_temple" -> ScatteredFeatureKind.JUNGLE_TEMPLE;
            case "minecraft:swamp_hut" -> ScatteredFeatureKind.SWAMP_HUT;
            default -> throw new IllegalArgumentException("unexpected scattered feature type " + typeStr);
        };
        return new ScatteredFeatureStructure(kind, biomes);
    }

    private static Structure parseStrongholdStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        return new StrongholdStructure(biomes);
    }

    private static Structure parseFortressStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        return new FortressStructure(biomes);
    }

    private static Structure parseWoodlandMansionStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        return new WoodlandMansionStructure(biomes);
    }

    private static Structure parseOceanMonumentStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        return new OceanMonumentStructure(biomes);
    }

    private static Structure parseBuriedTreasureStructure(JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        return new BuriedTreasureStructure(biomes);
    }

    private static Structure parseMineshaftStructure(JsonElement json) {
        var decoded = MINESHAFT_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var type = MineshaftType.fromName(decoded.mineshaftType());
        return new MineshaftStructure(type, biomes);
    }

    private static Structure parseJigsawStructure(JsonElement json) {
        var obj = json.getAsJsonObject();
        var biomes = parseBiomes(obj.get("biomes"));
        var startPool = Key.key(obj.get("start_pool").getAsString());
        var startJigsawName = obj.has("start_jigsaw_name")
                ? Key.key(obj.get("start_jigsaw_name").getAsString())
                : null;
        var size = obj.get("size").getAsInt();
        var startHeight = parseStartHeight(obj.get("start_height"));
        var projectToHeightmap = obj.has("project_start_to_heightmap");
        var useExpansionHack = obj.has("use_expansion_hack") && obj.get("use_expansion_hack").getAsBoolean();
        // Vanilla's builder default is 80 when the datapack omits the field.
        var maxDistance = obj.has("max_distance_from_center")
                ? parseMaxDistance(obj.get("max_distance_from_center"))
                : 80;
        var poolAliases = PoolAliasBinding.parseList(obj.get("pool_aliases"));
        var paddingBottom = 0;
        var paddingTop = 0;
        if (obj.has("dimension_padding")) {
            var padding = obj.get("dimension_padding");
            if (padding.isJsonPrimitive()) {
                paddingBottom = padding.getAsInt();
                paddingTop = padding.getAsInt();
            } else if (padding.isJsonObject()) {
                var paddingObj = padding.getAsJsonObject();
                paddingBottom = paddingObj.has("bottom") ? paddingObj.get("bottom").getAsInt() : 0;
                paddingTop = paddingObj.has("top") ? paddingObj.get("top").getAsInt() : 0;
            }
        }
        var liquidSettings = obj.has("liquid_settings")
                ? LiquidSettings.fromName(obj.get("liquid_settings").getAsString())
                : LiquidSettings.APPLY_WATERLOGGING;
        var terrainAdaptation = parseTerrainAdaptation(obj);

        return new JigsawStructure(biomes, startPool, startJigsawName, size,
                new JigsawStructure.StartHeight(startHeight.min(), startHeight.max()),
                projectToHeightmap, useExpansionHack, maxDistance, poolAliases,
                paddingBottom, paddingTop, liquidSettings, terrainAdaptation);
    }

    private static int parseMaxDistance(JsonElement json) {
        if (json.isJsonPrimitive()) {
            return json.getAsInt();
        }
        if (json.isJsonObject() && json.getAsJsonObject().has("horizontal")) {
            return json.getAsJsonObject().get("horizontal").getAsInt();
        }
        return 80;
    }

    private static Structure parseSimpleStructure(String typeStr, JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var type = Key.key(typeStr);
        var templates = getTemplatesForType(typeStr);
        var terrainAdaptation = parseTerrainAdaptation(json.getAsJsonObject());
        return new SimpleStructure(type, biomes, templates, terrainAdaptation);
    }

    private static TerrainAdjustment parseTerrainAdaptation(JsonObject obj) {
        return obj.has("terrain_adaptation")
                ? TerrainAdjustment.fromName(obj.get("terrain_adaptation").getAsString())
                : TerrainAdjustment.NONE;
    }

    private static List<Key> getTemplatesForType(String type) {
        return switch (type) {
            case "minecraft:nether_fossil" -> List.of(
                    Key.key("minecraft:nether_fossils/fossil_1"),
                    Key.key("minecraft:nether_fossils/fossil_2"),
                    Key.key("minecraft:nether_fossils/fossil_3"),
                    Key.key("minecraft:nether_fossils/fossil_4"),
                    Key.key("minecraft:nether_fossils/fossil_5"),
                    Key.key("minecraft:nether_fossils/fossil_6"),
                    Key.key("minecraft:nether_fossils/fossil_7"),
                    Key.key("minecraft:nether_fossils/fossil_8"),
                    Key.key("minecraft:nether_fossils/fossil_9"),
                    Key.key("minecraft:nether_fossils/fossil_10"),
                    Key.key("minecraft:nether_fossils/fossil_11"),
                    Key.key("minecraft:nether_fossils/fossil_12"),
                    Key.key("minecraft:nether_fossils/fossil_13"),
                    Key.key("minecraft:nether_fossils/fossil_14")
            );
            default -> List.of();
        };
    }

    private static Structure.StructureBiomes parseBiomes(JsonElement json) {
        if (json.isJsonPrimitive()) {
            var value = json.getAsString();
            if (value.startsWith("#")) {
                return new Structure.StructureBiomes(Key.key(value.substring(1)), null);
            }
            return new Structure.StructureBiomes(null, List.of(Key.key(value)));
        }

        if (json.isJsonArray()) {
            var biomes = new ArrayList<Key>();
            for (var entry : json.getAsJsonArray()) {
                if (entry.isJsonPrimitive()) {
                    var value = entry.getAsString();
                    if (!value.startsWith("#")) {
                        biomes.add(Key.key(value));
                    }
                }
            }
            return new Structure.StructureBiomes(null, List.copyOf(biomes));
        }

        return new Structure.StructureBiomes(null, List.of());
    }

    private static HeightRange parseStartHeight(JsonElement json) {
        if (!json.isJsonObject()) {
            return new HeightRange(0, 0);
        }

        var obj = json.getAsJsonObject();

        if (obj.has("absolute")) {
            var absolute = obj.get("absolute").getAsInt();
            return new HeightRange(absolute, absolute);
        }

        if (obj.has("type")) {
            var type = obj.get("type").getAsString();
            if (type.equals("minecraft:uniform")) {
                var minInclusive = obj.getAsJsonObject("min_inclusive");
                var maxInclusive = obj.getAsJsonObject("max_inclusive");
                var min = minInclusive != null && minInclusive.has("absolute")
                        ? minInclusive.get("absolute").getAsInt() : 0;
                var max = maxInclusive != null && maxInclusive.has("absolute")
                        ? maxInclusive.get("absolute").getAsInt() : 0;
                return new HeightRange(min, max);
            }
        }

        return new HeightRange(0, 0);
    }

    private record HeightRange(int min, int max) {
    }

    private record SimpleStructureData(Codec.RawValue biomes) {
    }

    private record MineshaftStructureData(Codec.RawValue biomes, String mineshaftType) {
    }
}
