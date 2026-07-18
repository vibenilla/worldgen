package rocks.minestom.worldgen.verify;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.instance.block.Block;
import rocks.minestom.worldgen.WorldGenerator;
import rocks.minestom.worldgen.WorldGenerators;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Measures per-structure-type in-box block accuracy across many instances, so
 * that rare structure types cannot hide inside a single volume-weighted
 * accuracy percentage.
 *
 * <p>Runs in two phases, driven by the first argument:
 * <ul>
 * <li>{@code locate}: finds the nearest N instances of each requested
 * structure type with this library's own {@code locateStructure}, and emits
 * a forceload plan (consumed by {@code scripts/gen_census.sh} to drive a
 * vanilla server) plus a JSON manifest describing every located instance.
 * <li>{@code verify}: given that manifest and the vanilla world it produced,
 * confirms each instance's structure start against the vanilla region NBT,
 * block-diffs the union of the vanilla piece bounding boxes against this
 * library's generator, and prints a census table of per-type accuracy with
 * the worst mismatch categories.
 * </ul>
 */
public final class StructureCensus {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: StructureCensus <locate|verify> ...");
            System.exit(1);
            return;
        }

        var mode = args[0];
        var rest = new ArrayList<>(List.of(args));
        rest.removeFirst();

        switch (mode) {
            case "locate" -> locate(rest);
            case "verify" -> verify(rest);
            default -> {
                System.out.println("Unknown mode: " + mode);
                System.exit(1);
            }
        }
        System.exit(0);
    }

    // ==================== structure type registry ====================

    private enum CensusDimension {
        OVERWORLD, NETHER, END
    }

    private record CensusType(String name, CensusDimension dimension, List<Key> locateKeys, Set<String> nbtIds,
            int windowRadiusChunks) {
        private CensusType(String name, CensusDimension dimension, String locateKey, int windowRadiusChunks) {
            this(name, dimension, List.of(Key.key(locateKey)), Set.of("minecraft:" + localId(locateKey)),
                    windowRadiusChunks);
        }

        private static String localId(String key) {
            return key.substring(key.indexOf(':') + 1);
        }
    }

    private static List<CensusType> allTypes() {
        var types = new ArrayList<CensusType>();
        types.add(new CensusType("village", CensusDimension.OVERWORLD,
                List.of(Key.key("minecraft:village_plains"), Key.key("minecraft:village_desert"),
                        Key.key("minecraft:village_savanna"), Key.key("minecraft:village_snowy"),
                        Key.key("minecraft:village_taiga")),
                Set.of("minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna",
                        "minecraft:village_snowy", "minecraft:village_taiga"),
                6));
        types.add(new CensusType("mineshaft", CensusDimension.OVERWORLD,
                List.of(Key.key("minecraft:mineshaft"), Key.key("minecraft:mineshaft_mesa")),
                Set.of("minecraft:mineshaft", "minecraft:mineshaft_mesa"), 8));
        types.add(new CensusType("fortress", CensusDimension.NETHER, "minecraft:fortress", 12));
        types.add(new CensusType("bastion_remnant", CensusDimension.NETHER, "minecraft:bastion_remnant", 8));
        types.add(new CensusType("stronghold", CensusDimension.OVERWORLD, "minecraft:stronghold", 6));
        types.add(new CensusType("monument", CensusDimension.OVERWORLD, "minecraft:monument", 4));
        types.add(new CensusType("mansion", CensusDimension.OVERWORLD, "minecraft:mansion", 10));
        types.add(new CensusType("desert_pyramid", CensusDimension.OVERWORLD, "minecraft:desert_pyramid", 3));
        types.add(new CensusType("jungle_temple", CensusDimension.OVERWORLD, "minecraft:jungle_pyramid", 3));
        types.add(new CensusType("swamp_hut", CensusDimension.OVERWORLD, "minecraft:swamp_hut", 3));
        types.add(new CensusType("buried_treasure", CensusDimension.OVERWORLD, "minecraft:buried_treasure", 3));
        types.add(new CensusType("igloo", CensusDimension.OVERWORLD, "minecraft:igloo", 3));
        types.add(new CensusType("shipwreck", CensusDimension.OVERWORLD, "minecraft:shipwreck", 3));
        types.add(new CensusType("shipwreck_beached", CensusDimension.OVERWORLD, "minecraft:shipwreck_beached", 3));
        types.add(new CensusType("ocean_ruin_cold", CensusDimension.OVERWORLD, "minecraft:ocean_ruin_cold", 3));
        types.add(new CensusType("ocean_ruin_warm", CensusDimension.OVERWORLD, "minecraft:ocean_ruin_warm", 3));
        types.add(new CensusType("ruined_portal", CensusDimension.OVERWORLD,
                List.of(Key.key("minecraft:ruined_portal"), Key.key("minecraft:ruined_portal_desert"),
                        Key.key("minecraft:ruined_portal_jungle"), Key.key("minecraft:ruined_portal_swamp"),
                        Key.key("minecraft:ruined_portal_mountain"), Key.key("minecraft:ruined_portal_ocean"),
                        Key.key("minecraft:ruined_portal_nether")),
                Set.of("minecraft:ruined_portal", "minecraft:ruined_portal_desert", "minecraft:ruined_portal_jungle",
                        "minecraft:ruined_portal_swamp", "minecraft:ruined_portal_mountain",
                        "minecraft:ruined_portal_ocean", "minecraft:ruined_portal_nether"),
                3));
        types.add(new CensusType("pillager_outpost", CensusDimension.OVERWORLD, "minecraft:pillager_outpost", 8));
        types.add(new CensusType("ancient_city", CensusDimension.OVERWORLD, "minecraft:ancient_city", 10));
        types.add(new CensusType("trail_ruins", CensusDimension.OVERWORLD, "minecraft:trail_ruins", 4));
        types.add(new CensusType("trial_chambers", CensusDimension.OVERWORLD, "minecraft:trial_chambers", 12));
        types.add(new CensusType("nether_fossil", CensusDimension.NETHER, "minecraft:nether_fossil", 4));
        types.add(new CensusType("end_city", CensusDimension.END, "minecraft:end_city", 6));
        return types;
    }

    private static List<CensusType> selectTypes(List<String> requested) {
        var all = allTypes();
        if (requested.isEmpty() || (requested.size() == 1 && requested.getFirst().equals("all"))) {
            return all;
        }
        var byName = new HashMap<String, CensusType>();
        for (var type : all) {
            byName.put(type.name(), type);
        }
        var selected = new ArrayList<CensusType>();
        for (var name : requested) {
            var type = byName.get(name);
            if (type == null) {
                throw new IllegalArgumentException("Unknown structure type: " + name);
            }
            selected.add(type);
        }
        return selected;
    }

    // ==================== phase 1: locate ====================

    /**
     * Usage: locate &lt;datapackDir&gt; &lt;seed&gt; &lt;planFile&gt; &lt;manifestFile&gt; &lt;N&gt;
     * [comma-separated types, default "all"]
     */
    private static void locate(List<String> args) throws IOException {
        var datapackDir = Path.of(args.get(0));
        var seed = Long.parseLong(args.get(1));
        var planFile = Path.of(args.get(2));
        var manifestFile = Path.of(args.get(3));
        var requestedN = Integer.parseInt(args.get(4));
        var requestedTypes = args.size() > 5
                ? List.of(args.get(5).split(","))
                : List.<String>of();

        var types = selectTypes(requestedTypes);
        var generators = new WorldGenerators(datapackDir, seed);
        var overworld = (WorldGenerator) generators.overworld();
        var nether = (WorldGenerator) generators.nether();
        var end = (WorldGenerator) generators.end();

        var manifestInstances = new JsonArray();
        var overworldTiles = new ArrayList<int[]>();
        var netherTiles = new ArrayList<int[]>();
        var endTiles = new ArrayList<int[]>();

        for (var type : types) {
            var generator = switch (type.dimension()) {
                case OVERWORLD -> overworld;
                case NETHER -> nether;
                case END -> end;
            };

            var excludedChunks = new HashSet<Long>();
            var found = new ArrayList<int[]>();
            var searchRadius = 256;
            for (var instance = 0; instance < requestedN; instance++) {
                BlockVec best = null;
                var bestDistanceSquared = Long.MAX_VALUE;
                for (var locateKey : type.locateKeys()) {
                    var located = generator.locateStructure(locateKey, 0, 0, searchRadius, excludedChunks);
                    if (located == null) {
                        continue;
                    }
                    var distanceSquared = (long) located.blockX() * located.blockX()
                            + (long) located.blockZ() * located.blockZ();
                    if (distanceSquared < bestDistanceSquared) {
                        bestDistanceSquared = distanceSquared;
                        best = located;
                    }
                }
                if (best == null) {
                    break;
                }
                var chunkX = (best.blockX() - 8) >> 4;
                var chunkZ = (best.blockZ() - 8) >> 4;
                found.add(new int[] {chunkX, chunkZ});
                excludedChunks.add(((long) chunkX << 32) | (chunkZ & 0xffffffffL));
            }

            if (found.isEmpty()) {
                System.out.println("locate: no instances found for " + type.name() + " within " + searchRadius
                        + " chunks, skipping");
                continue;
            }
            if (found.size() < requestedN) {
                System.out.println("locate: only found " + found.size() + "/" + requestedN + " instances for "
                        + type.name() + " within " + searchRadius + " chunks; reducing this type's instance count");
            }

            for (var index = 0; index < found.size(); index++) {
                var chunk = found.get(index);
                var chunkX = chunk[0];
                var chunkZ = chunk[1];
                var radius = type.windowRadiusChunks() + 1;
                var minChunkX = chunkX - radius;
                var minChunkZ = chunkZ - radius;
                var maxChunkX = chunkX + radius;
                var maxChunkZ = chunkZ + radius;

                var instanceObject = new JsonObject();
                instanceObject.addProperty("type", type.name());
                instanceObject.addProperty("dimension", type.dimension().name().toLowerCase());
                instanceObject.addProperty("instance", index);
                instanceObject.addProperty("chunkX", chunkX);
                instanceObject.addProperty("chunkZ", chunkZ);
                instanceObject.addProperty("windowMinChunkX", minChunkX);
                instanceObject.addProperty("windowMinChunkZ", minChunkZ);
                instanceObject.addProperty("windowMaxChunkX", maxChunkX);
                instanceObject.addProperty("windowMaxChunkZ", maxChunkZ);
                var nbtIdsArray = new JsonArray();
                type.nbtIds().forEach(nbtIdsArray::add);
                instanceObject.add("nbtIds", nbtIdsArray);
                var locateKeysArray = new JsonArray();
                type.locateKeys().forEach(key -> locateKeysArray.add(key.asString()));
                instanceObject.add("locateKeys", locateKeysArray);
                manifestInstances.add(instanceObject);

                var tileList = switch (type.dimension()) {
                    case OVERWORLD -> overworldTiles;
                    case NETHER -> netherTiles;
                    case END -> endTiles;
                };
                tileList.add(new int[] {minChunkX, minChunkZ, maxChunkX, maxChunkZ});
            }

            System.out.println("locate: " + type.name() + " -> " + found.size() + " instance(s) at "
                    + found.stream().map(c -> c[0] + "," + c[1]).toList());
        }

        var manifest = new JsonObject();
        manifest.addProperty("seed", seed);
        manifest.add("instances", manifestInstances);
        Files.writeString(manifestFile, new GsonBuilder().setPrettyPrinting().create().toJson(manifest));

        var plan = new StringBuilder();
        appendPlanTiles(plan, "", overworldTiles);
        appendPlanTiles(plan, "execute in minecraft:the_nether run ", netherTiles);
        appendPlanTiles(plan, "execute in minecraft:the_end run ", endTiles);
        plan.append("save-all flush\n");
        plan.append("stop\n");
        Files.writeString(planFile, plan.toString());

        System.out.println("Wrote manifest to " + manifestFile + " and plan to " + planFile);
    }

    /** Splits each window into at most 16x16-chunk (256 chunk) forceload tiles. */
    private static void appendPlanTiles(StringBuilder plan, String prefix, List<int[]> windows) {
        for (var window : windows) {
            var minChunkX = window[0];
            var minChunkZ = window[1];
            var maxChunkX = window[2];
            var maxChunkZ = window[3];
            for (var tileMinX = minChunkX; tileMinX <= maxChunkX; tileMinX += 16) {
                var tileMaxX = Math.min(tileMinX + 15, maxChunkX);
                for (var tileMinZ = minChunkZ; tileMinZ <= maxChunkZ; tileMinZ += 16) {
                    var tileMaxZ = Math.min(tileMinZ + 15, maxChunkZ);
                    plan.append(prefix).append("forceload add ")
                            .append(tileMinX * 16).append(' ').append(tileMinZ * 16).append(' ')
                            .append(tileMaxX * 16).append(' ').append(tileMaxZ * 16).append('\n');
                }
            }
        }
    }

    // ==================== phase 2: verify ====================

    /**
     * Usage: verify &lt;datapackDir&gt; &lt;seed&gt; &lt;manifestFile&gt; &lt;vanillaOverworldDir&gt;
     * &lt;vanillaNetherDir&gt; &lt;vanillaEndDir&gt;
     */
    private static void verify(List<String> args) throws IOException {
        var datapackDir = Path.of(args.get(0));
        var seed = Long.parseLong(args.get(1));
        var manifestFile = Path.of(args.get(2));
        var vanillaOverworldDir = Path.of(args.get(3));
        var vanillaNetherDir = Path.of(args.get(4));
        var vanillaEndDir = Path.of(args.get(5));

        var manifestJson = JsonParser.parseString(Files.readString(manifestFile)).getAsJsonObject();
        var instances = manifestJson.getAsJsonArray("instances");

        var generators = new WorldGenerators(datapackDir, seed);
        MinecraftServer.init();
        var overworldInstance = MinecraftServer.getInstanceManager().createInstanceContainer();
        overworldInstance.setGenerator(generators.overworld());
        var netherInstance = MinecraftServer.getInstanceManager().createInstanceContainer();
        netherInstance.setGenerator(generators.nether());
        var endInstance = MinecraftServer.getInstanceManager().createInstanceContainer();
        endInstance.setGenerator(generators.end());

        var regionCaches = new HashMap<Path, Map<Long, RegionFile>>();

        record TypeStats(List<String> divergences, Map<String, Integer> mismatches, long[] totals) {
        }
        var statsByType = new TreeMap<String, TypeStats>();

        for (var element : instances) {
            var instanceObject = element.getAsJsonObject();
            var typeName = instanceObject.get("type").getAsString();
            var dimensionName = instanceObject.get("dimension").getAsString();
            var instanceIndex = instanceObject.get("instance").getAsInt();
            var chunkX = instanceObject.get("chunkX").getAsInt();
            var chunkZ = instanceObject.get("chunkZ").getAsInt();
            var windowMinChunkX = instanceObject.get("windowMinChunkX").getAsInt();
            var windowMinChunkZ = instanceObject.get("windowMinChunkZ").getAsInt();
            var windowMaxChunkX = instanceObject.get("windowMaxChunkX").getAsInt();
            var windowMaxChunkZ = instanceObject.get("windowMaxChunkZ").getAsInt();
            var nbtIds = new HashSet<String>();
            instanceObject.getAsJsonArray("nbtIds").forEach(json -> nbtIds.add(json.getAsString()));

            var vanillaDir = switch (dimensionName) {
                case "overworld" -> vanillaOverworldDir;
                case "nether" -> vanillaNetherDir;
                case "end" -> vanillaEndDir;
                default -> throw new IllegalStateException("Unknown dimension: " + dimensionName);
            };
            var instanceLabel = typeName + "#" + instanceIndex + " (chunk " + chunkX + "," + chunkZ + ")";
            var stats = statsByType.computeIfAbsent(typeName,
                    unused -> new TypeStats(new ArrayList<>(), new TreeMap<>(), new long[2]));

            var regions = regionCaches.computeIfAbsent(vanillaDir, unused -> new HashMap<>());
            CompoundBinaryTag matchedStart = null;
            var bestDistanceSquared = Long.MAX_VALUE;
            for (var scanChunkX = windowMinChunkX; scanChunkX <= windowMaxChunkX; scanChunkX++) {
                for (var scanChunkZ = windowMinChunkZ; scanChunkZ <= windowMaxChunkZ; scanChunkZ++) {
                    var chunkTag = readChunk(vanillaDir, regions, scanChunkX, scanChunkZ);
                    if (chunkTag == null) {
                        continue;
                    }
                    var starts = chunkTag.getCompound("structures").getCompound("starts");
                    for (var entry : starts) {
                        if (!nbtIds.contains(entry.getKey())) {
                            continue;
                        }
                        var start = (CompoundBinaryTag) entry.getValue();
                        if ("INVALID".equals(start.getString("id", "INVALID"))) {
                            continue;
                        }
                        var deltaX = (long) (scanChunkX - chunkX);
                        var deltaZ = (long) (scanChunkZ - chunkZ);
                        var distanceSquared = deltaX * deltaX + deltaZ * deltaZ;
                        if (distanceSquared < bestDistanceSquared) {
                            bestDistanceSquared = distanceSquared;
                            matchedStart = start;
                        }
                    }
                }
            }

            if (matchedStart == null) {
                System.out.println("PLACEMENT DIVERGENCE: " + instanceLabel + " - no matching vanilla start found");
                stats.divergences().add(instanceLabel + ": vanilla missing");
                continue;
            }

            var minX = Integer.MAX_VALUE;
            var minY = Integer.MAX_VALUE;
            var minZ = Integer.MAX_VALUE;
            var maxX = Integer.MIN_VALUE;
            var maxY = Integer.MIN_VALUE;
            var maxZ = Integer.MIN_VALUE;
            for (var childTag : matchedStart.getList("Children")) {
                var child = (CompoundBinaryTag) childTag;
                var childBB = child.getIntArray("BB");
                if (childBB.length != 6) {
                    continue;
                }
                minX = Math.min(minX, childBB[0]);
                minY = Math.min(minY, childBB[1]);
                minZ = Math.min(minZ, childBB[2]);
                maxX = Math.max(maxX, childBB[3]);
                maxY = Math.max(maxY, childBB[4]);
                maxZ = Math.max(maxZ, childBB[5]);
            }
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                System.out.println("PLACEMENT DIVERGENCE: " + instanceLabel
                        + " - vanilla start has no piece bounding boxes");
                stats.divergences().add(instanceLabel + ": vanilla start has no pieces");
                continue;
            }

            var instance = switch (dimensionName) {
                case "overworld" -> overworldInstance;
                case "nether" -> netherInstance;
                case "end" -> endInstance;
                default -> throw new IllegalStateException("Unknown dimension: " + dimensionName);
            };

            var minBoxChunkX = minX >> 4;
            var maxBoxChunkX = maxX >> 4;
            var minBoxChunkZ = minZ >> 4;
            var maxBoxChunkZ = maxZ >> 4;
            for (var boxChunkX = minBoxChunkX - 1; boxChunkX <= maxBoxChunkX + 1; boxChunkX++) {
                for (var boxChunkZ = minBoxChunkZ - 1; boxChunkZ <= maxBoxChunkZ + 1; boxChunkZ++) {
                    instance.loadChunk(boxChunkX, boxChunkZ).join();
                }
            }

            var instanceMismatches = new TreeMap<String, Integer>();
            var instanceTotal = 0L;
            var instanceMatched = 0L;
            for (var boxChunkX = minBoxChunkX; boxChunkX <= maxBoxChunkX; boxChunkX++) {
                for (var boxChunkZ = minBoxChunkZ; boxChunkZ <= maxBoxChunkZ; boxChunkZ++) {
                    var chunkTag = readChunk(vanillaDir, regions, boxChunkX, boxChunkZ);
                    if (chunkTag == null) {
                        continue;
                    }
                    var vanillaChunk = VanillaChunk.parse(chunkTag);
                    if (vanillaChunk == null) {
                        continue;
                    }
                    var chunk = instance.loadChunk(boxChunkX, boxChunkZ).join();

                    for (var y = minY; y <= maxY; y++) {
                        for (var localZ = 0; localZ < 16; localZ++) {
                            var worldZ = boxChunkZ * 16 + localZ;
                            if (worldZ < minZ || worldZ > maxZ) {
                                continue;
                            }
                            for (var localX = 0; localX < 16; localX++) {
                                var worldX = boxChunkX * 16 + localX;
                                if (worldX < minX || worldX > maxX) {
                                    continue;
                                }
                                var expected = vanillaChunk.block(localX, y, localZ);
                                if (expected == null) {
                                    continue;
                                }
                                var actual = canonical(chunk.getBlock(localX, y, localZ));
                                instanceTotal++;
                                if (expected.equals(actual)) {
                                    instanceMatched++;
                                } else {
                                    instanceMismatches.merge(strip(expected) + " -> " + strip(actual), 1,
                                            Integer::sum);
                                }
                            }
                        }
                    }
                }
            }

            stats.totals()[0] += instanceMatched;
            stats.totals()[1] += instanceTotal;
            instanceMismatches.forEach((key, count) -> stats.mismatches().merge(key, count, Integer::sum));

            var accuracy = 100.0 * instanceMatched / Math.max(instanceTotal, 1);
            System.out.printf("%s box accuracy: %d / %d (%.4f%%)%n", instanceLabel, instanceMatched, instanceTotal,
                    accuracy);
        }

        System.out.println();
        System.out.println("=== STRUCTURE CENSUS (seed " + seed + ") ===");
        System.out.printf("%-20s %12s %12s %10s %10s%n", "type", "matched", "total", "accuracy", "divergences");
        for (var entry : statsByType.entrySet()) {
            var stats = entry.getValue();
            var matched = stats.totals()[0];
            var total = stats.totals()[1];
            var accuracy = total == 0 ? Double.NaN : 100.0 * matched / total;
            System.out.printf("%-20s %12d %12d %9.4f%% %10d%n", entry.getKey(), matched, total, accuracy,
                    stats.divergences().size());
        }
        System.out.println();
        for (var entry : statsByType.entrySet()) {
            var stats = entry.getValue();
            if (!stats.divergences().isEmpty()) {
                System.out.println("-- " + entry.getKey() + " placement divergences --");
                stats.divergences().forEach(divergence -> System.out.println("  " + divergence));
            }
        }
        System.out.println();
        for (var entry : statsByType.entrySet()) {
            var stats = entry.getValue();
            if (stats.mismatches().isEmpty()) {
                continue;
            }
            System.out.println("-- " + entry.getKey() + " top mismatch categories --");
            stats.mismatches().entrySet().stream()
                    .sorted((a, b) -> b.getValue() - a.getValue())
                    .limit(10)
                    .forEach(mismatch -> System.out.printf("  %10d  %s%n", mismatch.getValue(), mismatch.getKey()));
        }
    }

    private static CompoundBinaryTag readChunk(Path vanillaDir, Map<Long, RegionFile> regions, int chunkX,
            int chunkZ) {
        var regionKey = ((long) Math.floorDiv(chunkX, 32) << 32) | (Math.floorDiv(chunkZ, 32) & 0xffffffffL);
        var region = regions.computeIfAbsent(regionKey, unused -> {
            try {
                return new RegionFile(vanillaDir.resolve("region").resolve(
                        "r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca"));
            } catch (IOException exception) {
                return null;
            }
        });
        if (region == null) {
            return null;
        }
        try {
            return region.readChunk(chunkX, chunkZ);
        } catch (IOException exception) {
            return null;
        }
    }

    private static String strip(String state) {
        var bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    private static String canonical(Block block) {
        return VanillaChunk.canonical(block.key().asString(), block.properties());
    }
}
