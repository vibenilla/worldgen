package rocks.minestom.worldgen.structure.loader;

import com.google.gson.JsonElement;
import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import org.jetbrains.annotations.Nullable;
import rocks.minestom.worldgen.structure.JigsawStructure;
import rocks.minestom.worldgen.structure.SimpleStructure;
import rocks.minestom.worldgen.structure.Structure;

import java.util.ArrayList;
import java.util.List;

public final class Structures {
    private static final Codec<JigsawStructureData> JIGSAW_CODEC = StructCodec.struct(
            "start_pool", Codec.KEY, JigsawStructureData::startPool,
            "size", Codec.INT, JigsawStructureData::size,
            "start_height", Codec.RAW_VALUE, JigsawStructureData::startHeight,
            "project_start_to_heightmap", Codec.STRING.optional(), JigsawStructureData::projectStartToHeightmap,
            "max_distance_from_center", Codec.INT.optional(), JigsawStructureData::maxDistanceFromCenter,
            "biomes", Codec.RAW_VALUE, JigsawStructureData::biomes,
            JigsawStructureData::new
    );

    private static final Codec<SimpleStructureData> SIMPLE_CODEC = StructCodec.struct(
            "biomes", Codec.RAW_VALUE, SimpleStructureData::biomes,
            SimpleStructureData::new
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

        return parseSimpleStructure(typeStr, json);
    }

    private static Structure parseJigsawStructure(JsonElement json) {
        var decoded = JIGSAW_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var startHeight = parseStartHeight(decoded.startHeight().convertTo(Transcoder.JSON).orElseThrow());
        var projectToHeightmap = decoded.projectStartToHeightmap() != null;
        var maxDistanceFromCenter = decoded.maxDistanceFromCenter();
        var maxDistance = maxDistanceFromCenter == null ? 0 : maxDistanceFromCenter;
        return new JigsawStructure(biomes, decoded.startPool(), decoded.size(), startHeight, projectToHeightmap, maxDistance);
    }

    private static Structure parseSimpleStructure(String typeStr, JsonElement json) {
        var decoded = SIMPLE_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var biomes = parseBiomes(decoded.biomes().convertTo(Transcoder.JSON).orElseThrow());
        var type = Key.key(typeStr);
        var templates = getTemplatesForType(typeStr);
        return new SimpleStructure(type, biomes, templates);
    }

    private static List<Key> getTemplatesForType(String type) {
        return switch (type) {
            case "minecraft:igloo" -> List.of(Key.key("minecraft:igloo/top"));
            case "minecraft:shipwreck" -> List.of(
                    Key.key("minecraft:shipwreck/rightsideup_full"),
                    Key.key("minecraft:shipwreck/rightsideup_full_degraded"),
                    Key.key("minecraft:shipwreck/rightsideup_backhalf"),
                    Key.key("minecraft:shipwreck/rightsideup_backhalf_degraded"),
                    Key.key("minecraft:shipwreck/rightsideup_fronthalf"),
                    Key.key("minecraft:shipwreck/rightsideup_fronthalf_degraded"),
                    Key.key("minecraft:shipwreck/sideways_full"),
                    Key.key("minecraft:shipwreck/sideways_full_degraded"),
                    Key.key("minecraft:shipwreck/sideways_backhalf"),
                    Key.key("minecraft:shipwreck/sideways_backhalf_degraded"),
                    Key.key("minecraft:shipwreck/sideways_fronthalf"),
                    Key.key("minecraft:shipwreck/sideways_fronthalf_degraded"),
                    Key.key("minecraft:shipwreck/upsidedown_full"),
                    Key.key("minecraft:shipwreck/upsidedown_full_degraded"),
                    Key.key("minecraft:shipwreck/upsidedown_backhalf"),
                    Key.key("minecraft:shipwreck/upsidedown_backhalf_degraded"),
                    Key.key("minecraft:shipwreck/upsidedown_fronthalf"),
                    Key.key("minecraft:shipwreck/upsidedown_fronthalf_degraded"),
                    Key.key("minecraft:shipwreck/with_mast"),
                    Key.key("minecraft:shipwreck/with_mast_degraded")
            );
            case "minecraft:shipwreck_beached" -> List.of(
                    Key.key("minecraft:shipwreck/rightsideup_full"),
                    Key.key("minecraft:shipwreck/rightsideup_full_degraded"),
                    Key.key("minecraft:shipwreck/rightsideup_backhalf"),
                    Key.key("minecraft:shipwreck/rightsideup_backhalf_degraded"),
                    Key.key("minecraft:shipwreck/rightsideup_fronthalf"),
                    Key.key("minecraft:shipwreck/rightsideup_fronthalf_degraded"),
                    Key.key("minecraft:shipwreck/with_mast"),
                    Key.key("minecraft:shipwreck/with_mast_degraded")
            );
            case "minecraft:ocean_ruin_cold" -> List.of(
                    Key.key("minecraft:underwater_ruin/brick_1"),
                    Key.key("minecraft:underwater_ruin/brick_2"),
                    Key.key("minecraft:underwater_ruin/brick_3"),
                    Key.key("minecraft:underwater_ruin/brick_4"),
                    Key.key("minecraft:underwater_ruin/brick_5"),
                    Key.key("minecraft:underwater_ruin/brick_6"),
                    Key.key("minecraft:underwater_ruin/brick_7"),
                    Key.key("minecraft:underwater_ruin/brick_8"),
                    Key.key("minecraft:underwater_ruin/cracked_1"),
                    Key.key("minecraft:underwater_ruin/cracked_2"),
                    Key.key("minecraft:underwater_ruin/cracked_3"),
                    Key.key("minecraft:underwater_ruin/cracked_4"),
                    Key.key("minecraft:underwater_ruin/cracked_5"),
                    Key.key("minecraft:underwater_ruin/cracked_6"),
                    Key.key("minecraft:underwater_ruin/cracked_7"),
                    Key.key("minecraft:underwater_ruin/cracked_8"),
                    Key.key("minecraft:underwater_ruin/mossy_1"),
                    Key.key("minecraft:underwater_ruin/mossy_2"),
                    Key.key("minecraft:underwater_ruin/mossy_3"),
                    Key.key("minecraft:underwater_ruin/mossy_4"),
                    Key.key("minecraft:underwater_ruin/mossy_5"),
                    Key.key("minecraft:underwater_ruin/mossy_6"),
                    Key.key("minecraft:underwater_ruin/mossy_7"),
                    Key.key("minecraft:underwater_ruin/mossy_8")
            );
            case "minecraft:ocean_ruin_warm" -> List.of(
                    Key.key("minecraft:underwater_ruin/warm_1"),
                    Key.key("minecraft:underwater_ruin/warm_2"),
                    Key.key("minecraft:underwater_ruin/warm_3"),
                    Key.key("minecraft:underwater_ruin/warm_4"),
                    Key.key("minecraft:underwater_ruin/warm_5"),
                    Key.key("minecraft:underwater_ruin/warm_6"),
                    Key.key("minecraft:underwater_ruin/warm_7"),
                    Key.key("minecraft:underwater_ruin/warm_8")
            );
            case "minecraft:ruined_portal" -> List.of(
                    Key.key("minecraft:ruined_portal/portal_1"),
                    Key.key("minecraft:ruined_portal/portal_2"),
                    Key.key("minecraft:ruined_portal/portal_3"),
                    Key.key("minecraft:ruined_portal/portal_4"),
                    Key.key("minecraft:ruined_portal/portal_5"),
                    Key.key("minecraft:ruined_portal/portal_6"),
                    Key.key("minecraft:ruined_portal/portal_7"),
                    Key.key("minecraft:ruined_portal/portal_8"),
                    Key.key("minecraft:ruined_portal/portal_9"),
                    Key.key("minecraft:ruined_portal/portal_10")
            );
            case "minecraft:ruined_portal_desert", "minecraft:ruined_portal_jungle",
                 "minecraft:ruined_portal_mountain", "minecraft:ruined_portal_nether",
                 "minecraft:ruined_portal_ocean", "minecraft:ruined_portal_swamp" -> List.of(
                    Key.key("minecraft:ruined_portal/portal_1"),
                    Key.key("minecraft:ruined_portal/portal_2"),
                    Key.key("minecraft:ruined_portal/portal_3"),
                    Key.key("minecraft:ruined_portal/portal_4"),
                    Key.key("minecraft:ruined_portal/portal_5"),
                    Key.key("minecraft:ruined_portal/portal_6"),
                    Key.key("minecraft:ruined_portal/portal_7"),
                    Key.key("minecraft:ruined_portal/portal_8"),
                    Key.key("minecraft:ruined_portal/portal_9"),
                    Key.key("minecraft:ruined_portal/portal_10"),
                    Key.key("minecraft:ruined_portal/giant_portal_1"),
                    Key.key("minecraft:ruined_portal/giant_portal_2"),
                    Key.key("minecraft:ruined_portal/giant_portal_3")
            );
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

    private static int parseStartHeight(JsonElement json) {
        if (!json.isJsonObject()) {
            return 0;
        }

        var obj = json.getAsJsonObject();

        if (obj.has("absolute")) {
            return obj.get("absolute").getAsInt();
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
                return (min + max) / 2;
            }
        }

        return 0;
    }

    private record JigsawStructureData(Key startPool, int size, Codec.RawValue startHeight, @Nullable String projectStartToHeightmap, @Nullable Integer maxDistanceFromCenter, Codec.RawValue biomes) {
    }

    private record SimpleStructureData(Codec.RawValue biomes) {
    }
}
