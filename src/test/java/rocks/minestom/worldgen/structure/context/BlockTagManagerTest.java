package rocks.minestom.worldgen.structure.context;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.kyori.adventure.key.Key;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlockTagManagerTest {

    @TempDir
    Path tempDir;

    private BlockTagManager manager;

    @BeforeEach
    public void setup() throws IOException {
        Path dataDir = tempDir.resolve("data/minecraft/tags/block");
        Files.createDirectories(dataDir);

        // Create a common deep chain
        createTagFile(dataDir, "leaf", "minecraft:stone");
        for (int i = 0; i < 20; i++) {
            createTagFile(dataDir, "deep_" + i, "#minecraft:deep_" + (i + 1));
        }
        createTagFile(dataDir, "deep_20", "minecraft:dirt");
        createTagFile(dataDir, "common", "#minecraft:deep_0", "minecraft:bedrock");

        // Create branching structure pointing to common
        // root -> branch_0..49 -> sub_0..49 -> common
        JsonArray rootValues = new JsonArray();
        for (int i = 0; i < 50; i++) {
            String branchName = "branch_" + i;
            JsonArray branchValues = new JsonArray();
            for (int j = 0; j < 50; j++) {
                String subName = "sub_" + i + "_" + j;
                createTagFile(dataDir, subName, "#minecraft:common");
                branchValues.add("#minecraft:" + subName);
            }
            createTagFile(dataDir, branchName, branchValues);
            rootValues.add("#minecraft:" + branchName);
        }
        createTagFile(dataDir, "root", rootValues);

        manager = new BlockTagManager(tempDir);
    }

    private void createTagFile(Path dir, String name, String... values) throws IOException {
        JsonArray array = new JsonArray();
        for (String v : values) {
            array.add(v);
        }
        createTagFile(dir, name, array);
    }

    private void createTagFile(Path dir, String name, JsonArray values) throws IOException {
        JsonObject json = new JsonObject();
        json.add("values", values);
        Files.writeString(dir.resolve(name + ".json"), new Gson().toJson(json));
    }

    @Test
    public void testRecursiveResolution() {
        Set<Key> blocks = manager.blocks(Key.key("minecraft:root"));
        // assertTrue(blocks.contains(Key.key("minecraft:stone"))); // This was wrong
        assertTrue(blocks.contains(Key.key("minecraft:dirt")));
        assertTrue(blocks.contains(Key.key("minecraft:bedrock")));
    }

}
