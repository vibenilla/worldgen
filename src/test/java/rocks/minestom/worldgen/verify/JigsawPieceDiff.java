package rocks.minestom.worldgen.verify;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.minestom.server.MinecraftServer;
import rocks.minestom.worldgen.WorldGenerators;
import rocks.minestom.worldgen.structure.JigsawStructure;
import rocks.minestom.worldgen.structure.assembly.JigsawAssembler;
import rocks.minestom.worldgen.structure.pool.FeaturePoolElement;
import rocks.minestom.worldgen.structure.pool.ListPoolElement;
import rocks.minestom.worldgen.structure.pool.SinglePoolElement;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compares the assembler's piece list for a jigsaw structure start against
 * the pieces stored in a vanilla world save.
 *
 * <p>Usage: JigsawPieceDiff &lt;vanillaWorldDir&gt; &lt;datapackDir&gt; &lt;seed&gt; &lt;chunkX&gt; &lt;chunkZ&gt; &lt;structureId&gt;
 */
public final class JigsawPieceDiff {
    public static void main(String[] args) throws Exception {
        var worldDir = Path.of(args[0]);
        var datapackDir = Path.of(args[1]);
        var seed = Long.parseLong(args[2]);
        var chunkX = Integer.parseInt(args[3]);
        var chunkZ = Integer.parseInt(args[4]);
        var structureId = args[5];

        // Vanilla pieces from the save
        var regionPath = worldDir.resolve("region")
                .resolve("r." + Math.floorDiv(chunkX, 32) + "." + Math.floorDiv(chunkZ, 32) + ".mca");
        var region = new RegionFile(regionPath);
        var chunkTag = region.readChunk(chunkX, chunkZ);
        var start = chunkTag.getCompound("structures").getCompound("starts").getCompound(structureId);
        var vanillaPieces = new ArrayList<String>();
        for (var childTag : start.getList("Children")) {
            var child = (CompoundBinaryTag) childTag;
            vanillaPieces.add(describeVanilla(child));
        }

        // Our pieces
        MinecraftServer.init();
        var generators = new WorldGenerators(datapackDir, seed);
        var structure = (JigsawStructure) generators.structureLoader().getStructure(Key.key(structureId));
        var assembler = new JigsawAssembler(generators.structureLoader(), generators.overworldSettings());
        var ourPieces = new ArrayList<String>();
        for (var piece : assembler.assemble(structure, chunkX, chunkZ)) {
            ourPieces.add(describeOurs(piece));
        }

        System.out.println("=== vanilla: " + vanillaPieces.size() + " pieces, ours: " + ourPieces.size() + " ===");
        var max = Math.max(vanillaPieces.size(), ourPieces.size());
        var matches = 0;
        for (var index = 0; index < max; index++) {
            var vanilla = index < vanillaPieces.size() ? vanillaPieces.get(index) : "<none>";
            var ours = index < ourPieces.size() ? ourPieces.get(index) : "<none>";
            var same = vanilla.equals(ours);
            if (same) {
                matches++;
            }
            if (!same || Boolean.getBoolean("piecediff.all")) {
                System.out.printf("%s #%d%n  vanilla: %s%n  ours:    %s%n", same ? "OK " : "DIFF", index, vanilla,
                        ours);
            }
        }
        System.out.println("=== matching in order: " + matches + "/" + max + " ===");
        System.exit(0);
    }

    private static String describeVanilla(CompoundBinaryTag piece) {
        var element = piece.getCompound("pool_element");
        var location = element.getString("location", element.getString("feature", "?"));
        if (element.getString("element_type").equals("minecraft:list_pool_element")) {
            var elements = element.getList("elements");
            if (elements.size() > 0 && elements.get(0) instanceof CompoundBinaryTag firstElement) {
                location = "list:" + firstElement.getString("location", "?");
            }
        }
        var bb = piece.getIntArray("BB");
        var bbText = bb.length == 6
                ? bb[0] + "," + bb[1] + "," + bb[2] + ".." + bb[3] + "," + bb[4] + "," + bb[5]
                : "?";
        return location
                + " pos=" + piece.getInt("PosX") + "," + piece.getInt("PosY") + "," + piece.getInt("PosZ")
                + " rot=" + piece.getString("rotation")
                + " bb=" + bbText
                + " gld=" + piece.getInt("ground_level_delta");
    }

    private static String describeOurs(JigsawAssembler.PlacedPiece piece) {
        var location = switch (piece.element()) {
            case SinglePoolElement single -> single.location().asString();
            case ListPoolElement list -> "list:"
                    + (list.elements().getFirst() instanceof SinglePoolElement single
                            ? single.location().asString()
                            : "?");
            case FeaturePoolElement feature -> feature.feature().asString();
            default -> "?";
        };
        var bounds = piece.bounds();
        return location
                + " pos=" + piece.position().blockX() + "," + piece.position().blockY() + ","
                + piece.position().blockZ()
                + " rot=" + rotationName(piece.rotation())
                + " bb=" + bounds.minX() + "," + bounds.minY() + "," + bounds.minZ()
                + ".." + bounds.maxX() + "," + bounds.maxY() + "," + bounds.maxZ()
                + " gld=" + piece.groundLevelDelta();
    }

    private static String rotationName(rocks.minestom.worldgen.structure.template.Rotation rotation) {
        return switch (rotation) {
            case NONE -> "NONE";
            case CLOCKWISE_90 -> "CLOCKWISE_90";
            case CLOCKWISE_180 -> "CLOCKWISE_180";
            case COUNTERCLOCKWISE_90 -> "COUNTERCLOCKWISE_90";
        };
    }
}
