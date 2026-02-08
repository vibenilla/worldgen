package rocks.minestom.worldgen.structure.loader;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Transcoder;
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

    public StructureProcessorList resolveProcessors(Codec.RawValue processors) {
        var json = processors.convertTo(Transcoder.JSON).orElseThrow();
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
