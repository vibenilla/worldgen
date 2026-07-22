package rocks.minestom.worldgen.demo;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.Suggestion;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.generator.GenerationUnit;
import net.minestom.server.instance.generator.Generator;
import net.minestom.server.world.DimensionType;
import rocks.minestom.worldgen.WorldGenerator;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.biome.BiomeSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runnable showcase server exposing all three dimensions with vanilla-style
 * /gamemode, /teleport, /locate and /dimension commands.
 *
 * <p>Run with {@code ./gradlew demoServer [-Pport=25565] [-Pseed=123456789]}.
 */
public final class DemoServer {
    private static final int STRUCTURE_SEARCH_RADIUS_CHUNKS = 100;
    private static final int BIOME_SEARCH_RADIUS = 6400;
    private static final int BIOME_SAMPLE_RESOLUTION_HORIZONTAL = 32;
    private static final int BIOME_SAMPLE_RESOLUTION_VERTICAL = 64;
    private static final long FROZEN_TIME = 6000;
    private static final String MATCH_SPLIT_CHARACTERS = "._-/";

    public static void main(String[] args) {
        var port = args.length > 0 ? Integer.parseInt(args[0]) : 25565;
        var seed = args.length > 1 ? Long.parseLong(args[1]) : 123456789L;

        var server = MinecraftServer.init();
        var generators = new WorldGenerators(Path.of("data/mc/datapack"), seed);
        var manager = MinecraftServer.getInstanceManager();

        var overworldGenerator = (WorldGenerator) generators.overworld();
        var netherGenerator = (WorldGenerator) generators.nether();
        var endGenerator = (WorldGenerator) generators.end();

        var overworld = manager.createInstanceContainer(DimensionType.OVERWORLD);
        overworld.setGenerator(new PrioritizedGenerator(overworldGenerator, overworld));
        var nether = manager.createInstanceContainer(DimensionType.THE_NETHER);
        nether.setGenerator(new PrioritizedGenerator(netherGenerator, nether));
        var end = manager.createInstanceContainer(DimensionType.THE_END);
        end.setGenerator(new PrioritizedGenerator(endGenerator, end));

        freezeTime(overworld);
        freezeTime(nether);
        freezeTime(end);

        var dimensions = new LinkedHashMap<String, Dimension>();
        dimensions.put("overworld", new Dimension(overworld, surfaceSpawn(overworld, 0, 0)));
        dimensions.put("nether", new Dimension(nether, roofedSpawn(nether, 0, 0)));
        dimensions.put("end", new Dimension(end, surfaceSpawn(end, 0, 0)));

        var locatables = new LinkedHashMap<Instance, Locatable>();
        locatables.put(overworld, new Locatable(overworldGenerator, generators.overworldBiomes()));
        locatables.put(nether, new Locatable(netherGenerator, generators.netherBiomes()));
        locatables.put(end, new Locatable(endGenerator, generators.endBiomes()));

        var structureIds = listResourceIds(generators.dataPackRoot(), "worldgen/structure");
        var biomeIds = listResourceIds(generators.dataPackRoot(), "worldgen/biome");

        var spawn = dimensions.get("overworld").spawn();
        var events = MinecraftServer.getGlobalEventHandler();
        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(overworld);
            event.getPlayer().setRespawnPoint(spawn);
        });
        events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                event.getPlayer().setGameMode(GameMode.CREATIVE);
            }
        });

        var commands = MinecraftServer.getCommandManager();
        commands.register(new GamemodeCommand());
        commands.register(new TeleportCommand());
        commands.register(new LocateCommand(locatables, structureIds, biomeIds));
        commands.register(new DimensionCommand(dimensions));
        commands.register(new ExecuteCommand(dimensions));

        server.start("0.0.0.0", port);
        MinecraftServer.LOGGER.info("Demo server listening on port {}, seed {}, spawn at {}", port, seed, spawn);
    }

    private static void freezeTime(Instance instance) {
        var clock = instance.defaultClock();
        if (clock == null) {
            return;
        }
        clock.time(FROZEN_TIME);
        clock.pause();
    }

    private static Pos surfaceSpawn(Instance instance, int x, int z) {
        instance.loadChunk(x >> 4, z >> 4).join();
        var maxY = instance.getCachedDimensionType().maxY();
        var minY = instance.getCachedDimensionType().minY();
        for (var y = maxY - 1; y > minY; y--) {
            if (!instance.getBlock(x, y, z).isAir()) {
                return new Pos(x + 0.5, y + 1, z + 0.5);
            }
        }
        return new Pos(x + 0.5, 65, z + 0.5);
    }

    private static Pos roofedSpawn(Instance instance, int x, int z) {
        instance.loadChunk(x >> 4, z >> 4).join();
        for (var y = 32; y < 120; y++) {
            if (instance.getBlock(x, y - 1, z).isSolid()
                    && instance.getBlock(x, y, z).isAir()
                    && instance.getBlock(x, y + 1, z).isAir()) {
                return new Pos(x + 0.5, y, z + 0.5);
            }
        }
        return new Pos(x + 0.5, 64, z + 0.5);
    }

    private static String prettyName(GameMode gameMode) {
        var name = gameMode.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static List<String> listResourceIds(Path dataPackRoot, String category) {
        var ids = new ArrayList<String>();
        var dataDirectory = dataPackRoot.resolve("data");
        try (var namespaces = Files.list(dataDirectory)) {
            for (var namespaceDirectory : (Iterable<Path>) namespaces.filter(Files::isDirectory)::iterator) {
                var categoryDirectory = namespaceDirectory.resolve(category);
                if (!Files.isDirectory(categoryDirectory)) {
                    continue;
                }
                var namespace = namespaceDirectory.getFileName().toString();
                try (var files = Files.list(categoryDirectory)) {
                    for (var file : (Iterable<Path>) files::iterator) {
                        var fileName = file.getFileName().toString();
                        if (fileName.endsWith(".json")) {
                            ids.add(namespace + ":" + fileName.substring(0, fileName.length() - ".json".length()));
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        Collections.sort(ids);
        return ids;
    }

    private static void suggestResourceIds(Suggestion suggestion, List<String> ids) {
        var input = suggestion.getInput();
        var remaining = input.substring(input.lastIndexOf(' ') + 1).toLowerCase(Locale.ROOT);
        var hasNamespace = remaining.indexOf(':') > -1;
        for (var id : ids) {
            var separator = id.indexOf(':');
            var namespace = id.substring(0, separator);
            var path = id.substring(separator + 1);
            var matches = hasNamespace
                    ? matchesSubString(remaining, id)
                    : matchesSubString(remaining, namespace) || matchesSubString(remaining, path);
            if (matches) {
                suggestion.addEntry(new SuggestionEntry(id));
            }
        }
    }

    private static boolean matchesSubString(String pattern, String input) {
        var index = 0;
        while (!input.startsWith(pattern, index)) {
            var split = -1;
            for (var cursor = index; cursor < input.length(); cursor++) {
                if (MATCH_SPLIT_CHARACTERS.indexOf(input.charAt(cursor)) >= 0) {
                    split = cursor;
                    break;
                }
            }
            if (split < 0) {
                return false;
            }
            index = split + 1;
        }
        return true;
    }

    private static Pos locateBiome(BiomeSource source, Key target, Pos origin, int minY, int maxYExclusive) {
        if (!source.possibleBiomes().contains(target)) {
            return null;
        }
        var sampleRadius = Math.floorDiv(BIOME_SEARCH_RADIUS, BIOME_SAMPLE_RESOLUTION_HORIZONTAL);
        var sampleYs = outFromOrigin(origin.blockY(), minY + 1, maxYExclusive, BIOME_SAMPLE_RESOLUTION_VERTICAL);
        var directionsX = new int[]{1, 0, -1, 0};
        var directionsZ = new int[]{0, 1, 0, -1};
        var legs = 4 * sampleRadius;
        var leg = -1;
        var legIndex = 0;
        var legSize = 0;
        var offsetX = 0;
        var offsetZ = 1;
        while (true) {
            var direction = ((leg % 4) + 4) % 4;
            offsetX += directionsX[direction];
            offsetZ += directionsZ[direction];
            if (legIndex >= legSize) {
                if (leg >= legs) {
                    return null;
                }
                leg++;
                legIndex = 0;
                legSize = leg / 2 + 1;
            }
            legIndex++;

            var blockX = origin.blockX() + offsetX * BIOME_SAMPLE_RESOLUTION_HORIZONTAL;
            var blockZ = origin.blockZ() + offsetZ * BIOME_SAMPLE_RESOLUTION_HORIZONTAL;
            var noiseX = blockX >> 2;
            var noiseZ = blockZ >> 2;
            for (var blockY : sampleYs) {
                if (target.equals(source.biome(noiseX, blockY >> 2, noiseZ))) {
                    return new Pos(blockX, blockY, blockZ);
                }
            }
        }
    }

    private static int[] outFromOrigin(int origin, int lowerBound, int upperBound, int stepSize) {
        var values = new ArrayList<Integer>();
        var clampedOrigin = Math.max(lowerBound, Math.min(upperBound, origin));
        var cursor = clampedOrigin;
        while (true) {
            var distance = Math.abs(clampedOrigin - cursor);
            var hasNext = clampedOrigin - distance >= lowerBound || clampedOrigin + distance <= upperBound;
            if (!hasNext) {
                break;
            }
            values.add(cursor);
            var previousWasNegative = cursor <= clampedOrigin;
            var canMovePositive = clampedOrigin + distance + stepSize <= upperBound;
            var next = clampedOrigin + distance + stepSize;
            if (!previousWasNegative || !canMovePositive) {
                var attempted = clampedOrigin - distance - (previousWasNegative ? stepSize : 0);
                if (attempted >= lowerBound) {
                    next = attempted;
                }
            }
            cursor = next;
        }
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Component locateResult(String name, Point found, int distance, boolean includeY) {
        var displayedY = includeY ? String.valueOf(found.blockY()) : "~";
        var coordinates = Component.text(
                        "[" + found.blockX() + ", " + displayedY + ", " + found.blockZ() + "]", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.runCommand(
                        "/tp @s " + found.blockX() + " " + displayedY + " " + found.blockZ()))
                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport")));
        return Component.text("The nearest " + name + " is at ")
                .append(coordinates)
                .append(Component.text(" (" + distance + " blocks away)"));
    }

    private record Dimension(Instance instance, Pos spawn) {
    }

    private record Locatable(WorldGenerator generator, BiomeSource biomeSource) {
    }

    /**
     * Funnels chunk generation through a fixed worker pool draining a queue
     * prioritized by distance to the nearest player (spawn when nobody is
     * online), so the chunks a joining player stands on always generate
     * first regardless of how the thousands of view-distance load requests
     * are scheduled. This mirrors vanilla's distance-ordered chunk tickets.
     */
    private static final class PrioritizedGenerator implements Generator {
        private static final PriorityBlockingQueue<PendingGeneration> QUEUE = new PriorityBlockingQueue<>();
        private static final AtomicLong SEQUENCE = new AtomicLong();

        static {
            var workers = Math.max(2, Runtime.getRuntime().availableProcessors() - 2);
            for (var workerIndex = 0; workerIndex < workers; workerIndex++) {
                Thread.ofPlatform().name("chunk-generation-" + workerIndex).daemon().start(() -> {
                    while (true) {
                        try {
                            QUEUE.take().task().run();
                        } catch (InterruptedException exception) {
                            return;
                        }
                    }
                });
            }
        }

        private final Generator delegate;
        private final Instance instance;

        PrioritizedGenerator(Generator delegate, Instance instance) {
            this.delegate = delegate;
            this.instance = instance;
        }

        @Override
        public void generate(GenerationUnit unit) {
            var task = new FutureTask<Void>(() -> {
                this.delegate.generate(unit);
                return null;
            });
            QUEUE.add(new PendingGeneration(this.priority(unit), SEQUENCE.getAndIncrement(), task));
            try {
                task.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(exception);
            } catch (ExecutionException exception) {
                throw exception.getCause() instanceof RuntimeException runtime
                        ? runtime
                        : new RuntimeException(exception.getCause());
            }
        }

        private long priority(GenerationUnit unit) {
            var chunkX = unit.absoluteStart().chunkX();
            var chunkZ = unit.absoluteStart().chunkZ();
            var best = Long.MAX_VALUE;
            for (var player : this.instance.getPlayers()) {
                var position = player.getPosition();
                var distance = Math.max(
                        Math.abs(position.chunkX() - chunkX),
                        Math.abs(position.chunkZ() - chunkZ));
                best = Math.min(best, distance);
            }
            if (best == Long.MAX_VALUE) {
                best = Math.max(Math.abs(chunkX), Math.abs(chunkZ));
            }
            return best;
        }

        private record PendingGeneration(long priority, long sequence, FutureTask<Void> task)
                implements Comparable<PendingGeneration> {
            @Override
            public int compareTo(PendingGeneration other) {
                var byPriority = Long.compare(this.priority, other.priority);
                return byPriority != 0 ? byPriority : Long.compare(this.sequence, other.sequence);
            }
        }
    }

    private static final class GamemodeCommand extends Command {
        GamemodeCommand() {
            super("gamemode");
            var mode = ArgumentType.Enum("mode", GameMode.class).setFormat(ArgumentEnum.Format.LOWER_CASED);
            var targets = ArgumentType.Entity("targets").onlyPlayers(true);
            setDefaultExecutor((sender, context) ->
                    sender.sendMessage(Component.text("Usage: /gamemode <survival|creative|adventure|spectator> [targets]")));

            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var gameMode = context.get(mode);
                player.setGameMode(gameMode);
                player.sendMessage(Component.text("Set own game mode to " + prettyName(gameMode) + " Mode"));
            }, mode);

            addSyntax((sender, context) -> {
                var gameMode = context.get(mode);
                var found = context.get(targets).find(sender);
                if (found.isEmpty()) {
                    sender.sendMessage(Component.text("No player was found"));
                    return;
                }
                for (var entity : found) {
                    if (!(entity instanceof Player player)) {
                        continue;
                    }
                    player.setGameMode(gameMode);
                    if (player == sender) {
                        sender.sendMessage(Component.text("Set own game mode to " + prettyName(gameMode) + " Mode"));
                    } else {
                        player.sendMessage(Component.text(
                                "Your game mode has been updated to " + prettyName(gameMode) + " Mode"));
                        sender.sendMessage(Component.text(
                                "Set " + player.getUsername() + "'s game mode to " + prettyName(gameMode) + " Mode"));
                    }
                }
            }, mode, targets);
        }
    }

    private static final class TeleportCommand extends Command {
        TeleportCommand() {
            super("teleport", "tp");
            var position = ArgumentType.RelativeVec3("position");
            var yaw = ArgumentType.Float("yaw");
            var pitch = ArgumentType.Float("pitch");
            var destination = ArgumentType.Entity("destination").onlyPlayers(true).singleEntity(true);
            var targets = ArgumentType.Entity("targets").onlyPlayers(true);
            setDefaultExecutor((sender, context) ->
                    sender.sendMessage(Component.text("Usage: /teleport <target> or /teleport [targets] <x> <y> <z>")));

            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var point = context.get(position).from(player);
                teleportPreservingRotation(player, point.x(), point.y(), point.z());
                sender.sendMessage(locationFeedback(List.of(player), point.x(), point.y(), point.z()));
            }, position);

            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var target = context.get(destination).findFirstPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Component.text("No player was found"));
                    return;
                }
                teleportToEntity(player, target);
                sender.sendMessage(Component.text(
                        "Teleported " + player.getUsername() + " to " + target.getUsername()));
            }, destination);

            addSyntax((sender, context) -> {
                var moved = new ArrayList<Player>();
                var point = new double[3];
                for (var entity : context.get(targets).find(sender)) {
                    if (!(entity instanceof Player player)) {
                        continue;
                    }
                    var resolved = context.get(position).from(player);
                    point[0] = resolved.x();
                    point[1] = resolved.y();
                    point[2] = resolved.z();
                    teleportPreservingRotation(player, resolved.x(), resolved.y(), resolved.z());
                    moved.add(player);
                }
                if (moved.isEmpty()) {
                    sender.sendMessage(Component.text("No player was found"));
                    return;
                }
                sender.sendMessage(locationFeedback(moved, point[0], point[1], point[2]));
            }, targets, position);

            addSyntax((sender, context) -> {
                var moved = new ArrayList<Player>();
                var point = new double[3];
                var rotationYaw = context.get(yaw);
                var rotationPitch = context.get(pitch);
                for (var entity : context.get(targets).find(sender)) {
                    if (!(entity instanceof Player player)) {
                        continue;
                    }
                    var resolved = context.get(position).from(player);
                    point[0] = resolved.x();
                    point[1] = resolved.y();
                    point[2] = resolved.z();
                    player.teleport(new Pos(
                            resolved.x(), resolved.y(), resolved.z(), rotationYaw, rotationPitch));
                    moved.add(player);
                }
                if (moved.isEmpty()) {
                    sender.sendMessage(Component.text("No player was found"));
                    return;
                }
                sender.sendMessage(locationFeedback(moved, point[0], point[1], point[2]));
            }, targets, position, yaw, pitch);

            addSyntax((sender, context) -> {
                var target = context.get(destination).findFirstPlayer(sender);
                if (target == null) {
                    sender.sendMessage(Component.text("No player was found"));
                    return;
                }
                var moved = new ArrayList<Player>();
                for (var entity : context.get(targets).find(sender)) {
                    if (entity instanceof Player player) {
                        teleportToEntity(player, target);
                        moved.add(player);
                    }
                }
                if (moved.isEmpty()) {
                    sender.sendMessage(Component.text("No player was found"));
                    return;
                }
                if (moved.size() == 1) {
                    sender.sendMessage(Component.text(
                            "Teleported " + moved.getFirst().getUsername() + " to " + target.getUsername()));
                } else {
                    sender.sendMessage(Component.text(
                            "Teleported " + moved.size() + " entities to " + target.getUsername()));
                }
            }, targets, destination);
        }

        private static void teleportToEntity(Player player, Player target) {
            if (target.getInstance() != player.getInstance()) {
                player.setInstance(target.getInstance(), target.getPosition());
            } else {
                player.teleport(target.getPosition());
            }
        }

        private static void teleportPreservingRotation(Player player, double x, double y, double z) {
            var current = player.getPosition();
            player.teleport(new Pos(x, y, z, current.yaw(), current.pitch()));
        }

        private static Component locationFeedback(List<Player> moved, double x, double y, double z) {
            var coordinates = String.format(Locale.ROOT, "%f, %f, %f", x, y, z);
            if (moved.size() == 1) {
                return Component.text("Teleported " + moved.getFirst().getUsername() + " to " + coordinates);
            }
            return Component.text("Teleported " + moved.size() + " entities to " + coordinates);
        }
    }

    private static final class LocateCommand extends Command {
        LocateCommand(Map<Instance, Locatable> locatables, List<String> structureIds, List<String> biomeIds) {
            super("locate");
            var structureLiteral = ArgumentType.Literal("structure");
            var structureId = ArgumentType.ResourceLocation("structure");
            structureId.setSuggestionCallback((sender, context, suggestion) ->
                    suggestResourceIds(suggestion, structureIds));
            var biomeLiteral = ArgumentType.Literal("biome");
            var biomeId = ArgumentType.ResourceLocation("biome");
            biomeId.setSuggestionCallback((sender, context, suggestion) ->
                    suggestResourceIds(suggestion, biomeIds));

            setDefaultExecutor((sender, context) ->
                    sender.sendMessage(Component.text("Usage: /locate structure <id> or /locate biome <id>")));

            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var locatable = locatables.get(player.getInstance());
                if (locatable == null || locatable.generator() == null) {
                    player.sendMessage(Component.text("This dimension has no locatable structures", NamedTextColor.RED));
                    return;
                }
                var structureKey = Key.key(context.get(structureId).asString());
                var origin = player.getPosition();
                player.sendMessage(Component.text("Locating " + structureKey.asString() + "..."));
                CompletableFuture
                        .supplyAsync(() -> locatable.generator().locateStructure(
                                structureKey, origin.blockX(), origin.blockZ(), STRUCTURE_SEARCH_RADIUS_CHUNKS))
                        .whenComplete((found, throwable) -> {
                            if (throwable != null) {
                                player.sendMessage(Component.text("Locate failed: " + throwable.getMessage(),
                                        NamedTextColor.RED));
                                return;
                            }
                            if (found == null) {
                                player.sendMessage(Component.text("Could not find a structure of type \""
                                        + structureKey.asString() + "\" nearby", NamedTextColor.RED));
                                return;
                            }
                            var distance = (int) Math.floor(Math.hypot(
                                    found.x() - origin.x(), found.z() - origin.z()));
                            player.sendMessage(locateResult(structureKey.asString(), found, distance, false));
                        });
            }, structureLiteral, structureId);

            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var locatable = locatables.get(player.getInstance());
                if (locatable == null) {
                    player.sendMessage(Component.text("This dimension has no locatable biomes", NamedTextColor.RED));
                    return;
                }
                var biomeKey = Key.key(context.get(biomeId).asString());
                var origin = player.getPosition();
                var dimensionType = player.getInstance().getCachedDimensionType();
                var minY = dimensionType.minY();
                var maxYExclusive = dimensionType.maxY();
                player.sendMessage(Component.text("Locating " + biomeKey.asString() + "..."));
                CompletableFuture
                        .supplyAsync(() -> locateBiome(
                                locatable.biomeSource(), biomeKey, origin, minY, maxYExclusive))
                        .whenComplete((found, throwable) -> {
                            if (throwable != null) {
                                player.sendMessage(Component.text("Locate failed: " + throwable.getMessage(),
                                        NamedTextColor.RED));
                                return;
                            }
                            if (found == null) {
                                player.sendMessage(Component.text("Could not find a biome of type \""
                                        + biomeKey.asString() + "\" within a reasonable distance", NamedTextColor.RED));
                                return;
                            }
                            var distance = (int) Math.floor(Math.sqrt(
                                    Math.pow(found.x() - origin.blockX(), 2)
                                            + Math.pow(found.y() - origin.blockY(), 2)
                                            + Math.pow(found.z() - origin.blockZ(), 2)));
                            player.sendMessage(locateResult(biomeKey.asString(), found, distance, true));
                        });
            }, biomeLiteral, biomeId);
        }
    }

    private static final class DimensionCommand extends Command {
        DimensionCommand(Map<String, Dimension> dimensions) {
            super("dimension");
            var dimension = ArgumentType.Word("dimension").from(dimensions.keySet().toArray(String[]::new));
            setDefaultExecutor((sender, context) ->
                    sender.sendMessage(Component.text("Usage: /dimension <" + String.join("|", dimensions.keySet()) + ">")));
            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var name = context.get(dimension);
                var destination = dimensions.get(name);
                if (player.getInstance() == destination.instance()) {
                    player.sendMessage(Component.text("You are already in the " + name));
                    return;
                }
                player.setInstance(destination.instance(), destination.spawn())
                        .thenRun(() -> player.sendMessage(Component.text("Teleported to the " + name)));
            }, dimension);
        }
    }

    /**
     * Minimal /execute supporting only the F3+C clipboard shape
     * {@code execute in <dimension> run tp @s <x> <y> <z> [<yaw> <pitch>]},
     * so coordinates copied in game paste-and-run on the demo server.
     */
    private static final class ExecuteCommand extends Command {
        private static final Map<String, String> DIMENSION_IDS = Map.of(
                "minecraft:overworld", "overworld",
                "minecraft:the_nether", "nether",
                "minecraft:the_end", "end");

        ExecuteCommand(Map<String, Dimension> dimensions) {
            super("execute");
            var inLiteral = ArgumentType.Literal("in");
            var dimensionId = ArgumentType.ResourceLocation("dimension");
            dimensionId.setSuggestionCallback((sender, context, suggestion) ->
                    suggestResourceIds(suggestion, DIMENSION_IDS.keySet().stream().sorted().toList()));
            var runLiteral = ArgumentType.Literal("run");
            var teleportLiteral = ArgumentType.Literal("tp");
            var selfLiteral = ArgumentType.Literal("@s");
            var position = ArgumentType.RelativeVec3("position");
            var yaw = ArgumentType.Float("yaw");
            var pitch = ArgumentType.Float("pitch");
            setDefaultExecutor((sender, context) -> sender.sendMessage(Component.text(
                    "Usage: /execute in <dimension> run tp @s <x> <y> <z> [<yaw> <pitch>]")));

            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var destination = resolveDimension(sender, dimensions, context.get(dimensionId).asString());
                if (destination == null) {
                    return;
                }
                var current = player.getPosition();
                execute(player, destination, context.get(position).from(player), current.yaw(), current.pitch());
            }, inLiteral, dimensionId, runLiteral, teleportLiteral, selfLiteral, position);

            addSyntax((sender, context) -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Only players can use this command"));
                    return;
                }
                var destination = resolveDimension(sender, dimensions, context.get(dimensionId).asString());
                if (destination == null) {
                    return;
                }
                execute(player, destination, context.get(position).from(player), context.get(yaw), context.get(pitch));
            }, inLiteral, dimensionId, runLiteral, teleportLiteral, selfLiteral, position, yaw, pitch);
        }

        private static Dimension resolveDimension(
                CommandSender sender, Map<String, Dimension> dimensions, String id) {
            var name = DIMENSION_IDS.get(id);
            var destination = name == null ? null : dimensions.get(name);
            if (destination == null) {
                sender.sendMessage(Component.text("Unknown dimension: " + id, NamedTextColor.RED));
            }
            return destination;
        }

        private static void execute(
                Player player, Dimension destination, Vec point, float yaw, float pitch) {
            var target = new Pos(point.x(), point.y(), point.z(), yaw, pitch);
            if (player.getInstance() != destination.instance()) {
                player.setInstance(destination.instance(), target);
            } else {
                player.teleport(target);
            }
            player.sendMessage(Component.text("Teleported " + player.getUsername() + " to "
                    + String.format(Locale.ROOT, "%f, %f, %f", point.x(), point.y(), point.z())));
        }
    }
}
