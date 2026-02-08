package rocks.minestom.worldgen.preset;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

import java.util.Map;

public record WorldPreset(Map<Key, Dimension> dimensions) {
    public static final Codec<WorldPreset> CODEC = StructCodec.struct(
            "dimensions", Codec.KEY.mapValue(Dimension.CODEC), WorldPreset::dimensions,
            WorldPreset::new
    );

    public record Dimension(Key type, Generator generator) {
        public static final Codec<Dimension> CODEC = StructCodec.struct(
                "type", Codec.KEY, Dimension::type,
                "generator", Generator.CODEC, Dimension::generator,
                Dimension::new
        );
    }

    public record Generator(Key type, BiomeSourceSettings biomeSource, Key settings) {
        public static final Codec<Generator> CODEC = StructCodec.struct(
                "type", Codec.KEY, Generator::type,
                "biome_source", BiomeSourceSettings.CODEC, Generator::biomeSource,
                "settings", Codec.KEY, Generator::settings,
                Generator::new
        );
    }
}
