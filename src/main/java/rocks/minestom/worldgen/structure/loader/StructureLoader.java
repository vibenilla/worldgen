package rocks.minestom.worldgen.structure.loader;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.structure.Structure;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.context.BiomeTagManager;
import rocks.minestom.worldgen.structure.context.BlockTagManager;
import rocks.minestom.worldgen.structure.pool.TemplatePool;
import rocks.minestom.worldgen.structure.pool.TemplatePools;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.processor.StructureProcessors;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StructureLoader {
    private final DataPack dataPack;
    private final BlockTagManager blockTags;
    private final BiomeTagManager biomeTags;
    private final Map<Key, Structure> structureCache;
    private final Map<Key, StructureSet> structureSetCache;
    private final Map<Key, TemplatePool> templatePoolCache;
    private final Map<Key, StructureTemplate> templateCache;
    private final Map<Key, StructureProcessorList> processorListCache;

    public StructureLoader(DataPack dataPack) {
        this.dataPack = dataPack;
        this.blockTags = new BlockTagManager(dataPack.rootPath());
        this.biomeTags = new BiomeTagManager(dataPack.rootPath());
        this.structureCache = new ConcurrentHashMap<>();
        this.structureSetCache = new ConcurrentHashMap<>();
        this.templatePoolCache = new ConcurrentHashMap<>();
        this.templateCache = new ConcurrentHashMap<>();
        this.processorListCache = new ConcurrentHashMap<>();
    }

    public BlockTagManager blockTags() {
        return this.blockTags;
    }

    public BiomeTagManager biomeTags() {
        return this.biomeTags;
    }

    public Structure getStructure(Key id) {
        return this.structureCache.computeIfAbsent(id, this::loadStructure);
    }

    public StructureSet getStructureSet(Key id) {
        return this.structureSetCache.computeIfAbsent(id, this::loadStructureSet);
    }

    public TemplatePool getTemplatePool(Key id) {
        return this.templatePoolCache.computeIfAbsent(id, this::loadTemplatePool);
    }

    public StructureTemplate getTemplate(Key id) {
        return this.templateCache.computeIfAbsent(id, this::loadTemplate);
    }

    public StructureProcessorList getProcessorList(Key id) {
        return this.processorListCache.computeIfAbsent(id, this::loadProcessorList);
    }

    public StructureProcessorList resolveProcessors(com.google.gson.JsonElement json) {
        if (json == null) {
            return StructureProcessorList.EMPTY;
        }

        if (json.isJsonPrimitive()) {
            var processorKey = Key.key(json.getAsString());
            return this.getProcessorList(processorKey);
        }

        if (json.isJsonObject()) {
            return StructureProcessors.parseProcessorList(json);
        }

        return StructureProcessorList.EMPTY;
    }

    private Structure loadStructure(Key id) {
        try {
            var json = this.dataPack.readStructure(id);
            return Structures.parseStructure(json);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * The decoration step ordinal of the structure (its {@code step} field),
     * matching vanilla {@code GenerationStep.Decoration}; defaults to
     * surface_structures when unreadable.
     */
    public int structureStep(Key id) {
        return this.structureStepCache.computeIfAbsent(id, key -> {
            try {
                var json = this.dataPack.readStructure(key);
                var step = json.getAsJsonObject().get("step").getAsString();
                return switch (step.replace("minecraft:", "")) {
                    case "raw_generation" -> 0;
                    case "lakes" -> 1;
                    case "local_modifications" -> 2;
                    case "underground_structures" -> 3;
                    case "surface_structures" -> 4;
                    case "strongholds" -> 5;
                    case "underground_ores" -> 6;
                    case "underground_decoration" -> 7;
                    case "fluid_springs" -> 8;
                    case "vegetal_decoration" -> 9;
                    case "top_layer_modification" -> 10;
                    default -> 4;
                };
            } catch (Exception exception) {
                return 4;
            }
        });
    }

    private final Map<Key, Integer> structureStepCache = new ConcurrentHashMap<>();
    private final Map<String, java.util.List<Key>> stepStructuresCache = new ConcurrentHashMap<>();

    /**
     * The structures of the given decoration step in the order vanilla's
     * {@code ChunkGenerator.applyBiomeDecoration} sees them: the structure
     * registry order (resource path, then namespace), filtered by the
     * structure's {@code step}. The position in this list is the
     * {@code setFeatureSeed} index a structure's placement random is seeded
     * with (verified against an instrumented server: the runtime order is
     * alphabetical by structure path, NOT structure-set flattening order).
     */
    public java.util.List<Key> structuresAtStep(String step) {
        return this.stepStructuresCache.computeIfAbsent(step, stepName -> {
            var keys = new java.util.ArrayList<Key>();
            var dataRoot = this.dataPack.rootPath().resolve("data");
            try (var namespaces = java.nio.file.Files.list(dataRoot)) {
                for (var namespaceDir : namespaces.filter(java.nio.file.Files::isDirectory).toList()) {
                    var structureDir = namespaceDir.resolve("worldgen").resolve("structure");
                    if (!java.nio.file.Files.isDirectory(structureDir)) {
                        continue;
                    }
                    try (var files = java.nio.file.Files.list(structureDir)) {
                        for (var file : files.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                            var name = file.getFileName().toString();
                            keys.add(Key.key(namespaceDir.getFileName().toString(),
                                    name.substring(0, name.length() - ".json".length())));
                        }
                    }
                }
            } catch (Exception exception) {
                return java.util.List.of();
            }

            keys.sort((left, right) -> {
                var byNamespace = left.namespace().compareTo(right.namespace());
                return byNamespace != 0 ? byNamespace : left.value().compareTo(right.value());
            });

            var stepOrdinal = stepOrdinal(stepName);
            var result = new java.util.ArrayList<Key>();
            for (var key : keys) {
                if (this.structureStep(key) == stepOrdinal) {
                    result.add(key);
                }
            }
            return java.util.List.copyOf(result);
        });
    }

    private static int stepOrdinal(String step) {
        return switch (step.replace("minecraft:", "")) {
            case "raw_generation" -> 0;
            case "lakes" -> 1;
            case "local_modifications" -> 2;
            case "underground_structures" -> 3;
            case "surface_structures" -> 4;
            case "strongholds" -> 5;
            case "underground_ores" -> 6;
            case "underground_decoration" -> 7;
            case "fluid_springs" -> 8;
            case "vegetal_decoration" -> 9;
            case "top_layer_modification" -> 10;
            default -> 4;
        };
    }

    private StructureSet loadStructureSet(Key id) {
        try {
            var json = this.dataPack.readStructureSet(id);
            return StructureSets.parseStructureSet(json);
        } catch (Exception exception) {
            return null;
        }
    }

    private TemplatePool loadTemplatePool(Key id) {
        try {
            var json = this.dataPack.readTemplatePool(id);
            return TemplatePools.parseTemplatePool(json, this);
        } catch (Exception exception) {
            return null;
        }
    }

    private StructureTemplate loadTemplate(Key id) {
        var path = this.resolveTemplatePath(id);
        return StructureTemplate.load(path);
    }

    private StructureProcessorList loadProcessorList(Key id) {
        try {
            var json = this.dataPack.readProcessorList(id);
            return StructureProcessors.parseProcessorList(json);
        } catch (Exception exception) {
            return StructureProcessorList.EMPTY;
        }
    }

    private Path resolveTemplatePath(Key id) {
        return this.dataPack.rootPath()
                .resolve("data")
                .resolve(id.namespace())
                .resolve("structure")
                .resolve(id.value() + ".nbt");
    }
}
