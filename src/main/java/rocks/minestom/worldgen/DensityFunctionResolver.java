package rocks.minestom.worldgen;

import net.kyori.adventure.key.Key;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;
import rocks.minestom.worldgen.datapack.DataPack;
import rocks.minestom.worldgen.density.DensityFunction;
import rocks.minestom.worldgen.density.DensityFunctions;
import rocks.minestom.worldgen.noise.BlendedNoise;
import rocks.minestom.worldgen.noise.SimplexNoise;
import rocks.minestom.worldgen.random.LegacyRandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class DensityFunctionResolver {
    private final DataPack dataPack;
    private final RandomState randomState;
    private final Map<Key, DensityFunction> densityFunctionCache;
    private final Codec<DensityFunction> densityCodec;
    private final ThreadLocal<ArrayList<Key>> resolutionStack;

    public DensityFunctionResolver(DataPack dataPack, RandomState randomState) {
        this.dataPack = dataPack;
        this.randomState = randomState;
        this.densityFunctionCache = new HashMap<>();
        this.densityCodec = this.createCodec();
        this.resolutionStack = ThreadLocal.withInitial(ArrayList::new);
    }

    public Codec<DensityFunction> codec() {
        return this.densityCodec;
    }

    private Codec<DensityFunction> createCodec() {
        return Codec.Recursive(self -> {
            var constantCodec = Codec.DOUBLE.transform(
                    value -> (DensityFunction) new DensityFunctions.Constant(value),
                    function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    }
            );

            var referenceCodec = Codec.KEY.transform(
                    this::resolveReference,
                    function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    }
            );

            Codec<DensityFunction> unionCodec = Codec.KEY.<DensityFunction>unionType(
                    "type",
                    type -> this.structForType(type, self),
                    function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    }
            );

            return new Codec<>() {
                @Override
                public <D> Result<DensityFunction> decode(Transcoder<D> coder, D value) {
                    var mapResult = coder.getMap(value);

                    if (mapResult instanceof Result.Ok) {
                        return unionCodec.decode(coder, value);
                    }

                    var doubleResult = coder.getDouble(value);

                    if (doubleResult instanceof Result.Ok) {
                        return constantCodec.decode(coder, value);
                    }

                    var stringResult = coder.getString(value);

                    if (stringResult instanceof Result.Ok) {
                        return referenceCodec.decode(coder, value);
                    }

                    return new Result.Error<>("Unsupported density function value: " + value);
                }

                @Override
                public <D> Result<D> encode(Transcoder<D> coder, DensityFunction value) {
                    throw new UnsupportedOperationException("Encoding is not supported");
                }
            };
        });
    }

    private DensityFunction resolveReference(Key key) {
        var cached = this.densityFunctionCache.get(key);
        if (cached != null) {
            return cached;
        }

        var stack = this.resolutionStack.get();
        if (stack.contains(key)) {
            return this.throwRecursiveReference(key, stack);
        }

        stack.add(key);
        try {
            var densityFunction = this.readDensityFunction(key);
            this.densityFunctionCache.put(key, densityFunction);
            return densityFunction;
        } finally {
            stack.removeLast();
        }
    }

    private DensityFunction throwRecursiveReference(Key key, ArrayList<Key> stack) {
        var chain = new StringBuilder();
        for (var index = 0; index < stack.size(); index++) {
            if (index != 0) {
                chain.append(" -> ");
            }
            chain.append(stack.get(index).asString());
        }
        if (!chain.isEmpty()) {
            chain.append(" -> ");
        }
        chain.append(key.asString());
        throw new IllegalStateException("Recursive density function reference: " + chain);
    }

    private DensityFunction readDensityFunction(Key key) {
        var json = this.dataPack.readDensityFunction(key);
        return this.densityCodec.decode(Transcoder.JSON, json).orElseThrow();
    }

    private StructCodec<? extends DensityFunction> structForType(Key type, Codec<DensityFunction> self) {
        return switch (type.asString()) {
            case "minecraft:cache_once" -> StructCodec.struct(
                    "argument", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.CacheOnce::new
            );
            case "minecraft:cache_2d" -> StructCodec.struct(
                    "argument", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.Cache2D::new
            );
            case "minecraft:flat_cache" -> StructCodec.struct(
                    "argument", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.FlatCache::new
            );
            case "minecraft:cache_all_in_cell" -> StructCodec.struct(
                    "argument", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.CacheAllInCell::new
            );
            case "minecraft:add" -> StructCodec.struct(
                    "argument1", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "argument2", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.Add::new
            );
            case "minecraft:mul" -> StructCodec.struct(
                    "argument1", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "argument2", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.Mul::new
            );
            case "minecraft:min" -> StructCodec.struct(
                    "argument1", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "argument2", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.Min::new
            );
            case "minecraft:max" -> StructCodec.struct(
                    "argument1", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "argument2", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.Max::new
            );
            case "minecraft:clamp" -> StructCodec.struct(
                    "input", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "min", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "max", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.Clamp::new
            );
            case "minecraft:abs" -> unaryMapped(self, DensityFunctions.Mapped.Type.ABS);
            case "minecraft:square" -> unaryMapped(self, DensityFunctions.Mapped.Type.SQUARE);
            case "minecraft:cube" -> unaryMapped(self, DensityFunctions.Mapped.Type.CUBE);
            case "minecraft:half_negative" -> unaryMapped(self, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
            case "minecraft:quarter_negative" -> unaryMapped(self, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
            case "minecraft:squeeze" -> unaryMapped(self, DensityFunctions.Mapped.Type.SQUEEZE);
            case "minecraft:y_clamped_gradient" -> StructCodec.struct(
                    "from_y", Codec.INT, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "to_y", Codec.INT, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "from_value", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "to_value", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.YClampedGradient::new
            );
            case "minecraft:range_choice" -> StructCodec.struct(
                    "input", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "min_inclusive", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "max_exclusive", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "when_in_range", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "when_out_of_range", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.RangeChoice::new
            );
            case "minecraft:noise" -> StructCodec.struct(
                    "noise", Codec.KEY, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "xz_scale", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "y_scale", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    (noiseKey, xzScale, yScale) -> new DensityFunctions.Noise(this.randomState.getOrCreateNoise(noiseKey), xzScale, yScale)
            );
            case "minecraft:shifted_noise" -> StructCodec.struct(
                    "shift_x", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "shift_y", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "shift_z", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "xz_scale", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "y_scale", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "noise", Codec.KEY, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    (shiftX, shiftY, shiftZ, xzScale, yScale, noiseKey) -> new DensityFunctions.ShiftedNoise(
                            shiftX,
                            shiftY,
                            shiftZ,
                            xzScale,
                            yScale,
                            this.randomState.getOrCreateNoise(noiseKey)
                    )
            );
            case "minecraft:shift_a" -> StructCodec.struct(
                    "argument", Codec.KEY, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    key -> new DensityFunctions.ShiftA(this.randomState.getOrCreateNoise(key))
            );
            case "minecraft:shift_b" -> StructCodec.struct(
                    "argument", Codec.KEY, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    key -> new DensityFunctions.ShiftB(this.randomState.getOrCreateNoise(key))
            );
            case "minecraft:blend_alpha" -> StructCodec.struct(DensityFunctions.BlendAlpha::new);
            case "minecraft:blend_offset" -> StructCodec.struct(DensityFunctions.BlendOffset::new);
            case "minecraft:blend_density" -> StructCodec.struct(
                    "argument", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.BlendDensity::new
            );
            case "minecraft:interpolated" -> StructCodec.struct(
                    "argument", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    DensityFunctions.Interpolated::new
            );
            case "minecraft:weird_scaled_sampler" -> StructCodec.struct(
                    "input", self, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "noise", Codec.KEY, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "rarity_value_mapper", Codec.STRING.transform(
                            value -> switch (value) {
                                case "type_1" -> DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE_1;
                                case "type_2" -> DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE_2;
                                default -> throw new IllegalStateException("Unsupported rarity_value_mapper: " + value);
                            },
                            mapper -> {
                                throw new UnsupportedOperationException("Encoding is not supported");
                            }
                    ), function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    (input, noiseKey, mapper) -> new DensityFunctions.WeirdScaledSampler(input, this.randomState.getOrCreateNoise(noiseKey), mapper)
            );
            case "minecraft:old_blended_noise" -> StructCodec.struct(
                    "xz_scale", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "y_scale", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "xz_factor", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "y_factor", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "smear_scale_multiplier", Codec.DOUBLE, function -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    (xzScale, yScale, xzFactor, yFactor, smearScaleMultiplier) -> new DensityFunctions.OldBlendedNoise(
                            new BlendedNoise(this.randomState.terrainRandom(), xzScale, yScale, xzFactor, yFactor, smearScaleMultiplier)
                    )
            );
            case "minecraft:end_islands" -> StructCodec.struct(() -> {
                var randomSource = new LegacyRandomSource(this.randomState.seed());
                randomSource.consumeCount(17292);
                return new DensityFunctions.EndIslands(new SimplexNoise(randomSource));
            });
            case "minecraft:spline" -> splineStruct(self);
            default -> throw new IllegalStateException("Unsupported density function type: " + type.asString());
        };
    }

    private static StructCodec<DensityFunction> unaryMapped(Codec<DensityFunction> self, DensityFunctions.Mapped.Type type) {
        return StructCodec.struct(
                "argument", self, function -> {
                    throw new UnsupportedOperationException("Encoding is not supported");
                },
                argument -> new DensityFunctions.Mapped(type, argument)
        );
    }

    private static StructCodec<DensityFunction> splineStruct(Codec<DensityFunction> self) {
        Codec<DensityFunctions.SplineNode> nodeCodec = Codec.Recursive(nodeSelf -> {
            var constant = Codec.FLOAT.transform(
                    value -> (DensityFunctions.SplineNode) new DensityFunctions.SplineConstant(value),
                    node -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    }
            );

            var pointCodec = StructCodec.struct(
                    "location", Codec.FLOAT, point -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "value", nodeSelf, point -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "derivative", Codec.FLOAT, point -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    SplinePoint::new
            );

            StructCodec<DensityFunctions.SplineNode> multipoint = StructCodec.struct(
                    "coordinate", self, node -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    "points", pointCodec.list(), node -> {
                        throw new UnsupportedOperationException("Encoding is not supported");
                    },
                    (coordinate, points) -> {
                        var locations = new float[points.size()];
                        var values = new ArrayList<DensityFunctions.SplineNode>(points.size());
                        var derivatives = new float[points.size()];
                        for (var index = 0; index < points.size(); index++) {
                            var point = points.get(index);
                            locations[index] = point.location();
                            values.add(point.value());
                            derivatives[index] = point.derivative();
                        }
                        return new DensityFunctions.SplineMultipoint(coordinate, locations, values, derivatives);
                    }
            );

            return new Codec<>() {
                @Override
                public <D> Result<DensityFunctions.SplineNode> decode(Transcoder<D> coder, D value) {
                    var mapResult = coder.getMap(value);
                    if (mapResult instanceof Result.Ok) {
                        return multipoint.decode(coder, value);
                    }

                    var floatResult = coder.getFloat(value);
                    if (floatResult instanceof Result.Ok) {
                        return constant.decode(coder, value);
                    }

                    return new Result.Error<>("Unsupported spline node value: " + value);
                }

                @Override
                public <D> Result<D> encode(Transcoder<D> coder, DensityFunctions.SplineNode value) {
                    throw new UnsupportedOperationException("Encoding is not supported");
                }
            };
        });

        return StructCodec.struct(
                "spline", nodeCodec, function -> {
                    throw new UnsupportedOperationException("Encoding is not supported");
                },
                DensityFunctions.Spline::new
        );
    }

    private record SplinePoint(float location, DensityFunctions.SplineNode value, float derivative) {
    }
}
