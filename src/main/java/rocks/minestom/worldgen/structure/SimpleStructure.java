package rocks.minestom.worldgen.structure;

import net.kyori.adventure.key.Key;
import rocks.minestom.worldgen.structure.context.StructurePlaceContext;
import rocks.minestom.worldgen.structure.processor.StructureProcessorList;
import rocks.minestom.worldgen.structure.template.Rotation;
import rocks.minestom.worldgen.structure.template.StructureTemplate;

import java.util.List;

/**
 * A structure placed directly from a single NBT template.
 *
 * <p>Simple structures are used for features like igloos, shipwrecks, and ruined portals
 * that don't require jigsaw assembly. A random template is selected from the available
 * options and placed with a random rotation.
 *
 * <p>Unlike {@link JigsawStructure}, simple structures:
 * <ul>
 *   <li>Don't connect multiple pieces
 *   <li>Don't use jigsaw blocks
 *   <li>Are typically smaller and self-contained
 * </ul>
 *
 * @see StructureTemplate for the template format
 */
public record SimpleStructure(Key type, StructureBiomes biomes, List<Key> templates) implements Structure {
    @Override
    public void place(StructurePlaceContext context) {
        if (this.templates.isEmpty()) {
            return;
        }

        var random = context.randomFactory().fromHashOf(context.start().blockX() + ":" + context.start().blockZ());
        var templateKey = this.templates.get(random.nextInt(this.templates.size()));
        var template = context.structureLoader().getTemplate(templateKey);

        if (template == null) {
            return;
        }

        var rotation = Rotation.values()[random.nextInt(Rotation.values().length)];

        template.place(
                context.level(),
                context.start(),
                rotation,
                StructureProcessorList.EMPTY,
                context.randomFactory(),
                context.structureLoader().blockTags());
    }
}
