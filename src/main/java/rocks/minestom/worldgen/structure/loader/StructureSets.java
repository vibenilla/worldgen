package rocks.minestom.worldgen.structure.loader;

import com.google.gson.JsonElement;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.structure.StructureSet;
import rocks.minestom.worldgen.structure.placement.StructurePlacements;

import java.util.List;

public final class StructureSets {
    private static final Codec<StructureSetData> STRUCTURE_SET_CODEC = StructCodec.struct(
            "structures", StructureSet.StructureSelection.CODEC.list(), StructureSetData::structures,
            "placement", Codec.RAW_VALUE, StructureSetData::placement,
            StructureSetData::new
    );

    private StructureSets() {
    }

    public static StructureSet parseStructureSet(JsonElement json) {
        var decoded = STRUCTURE_SET_CODEC.decode(Transcoder.JSON, json).orElseThrow();
        var placementJson = decoded.placement().convertTo(Transcoder.JSON).orElseThrow();
        var placement = StructurePlacements.parsePlacement(placementJson);
        if (placement == null) {
            return null;
        }
        return new StructureSet(decoded.structures(), placement);
    }

    private record StructureSetData(List<StructureSet.StructureSelection> structures, Codec.RawValue placement) {
    }
}
