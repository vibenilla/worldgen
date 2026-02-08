package rocks.minestom.worldgen.biome;

import net.kyori.adventure.key.Key;

import java.util.List;
import java.util.function.Consumer;

public final class OverworldBiomeBuilder {
   private static final float VALLEY_SIZE = 0.05F;
   private static final float LOW_START = 0.26666668F;
   public static final float HIGH_START = 0.4F;
   private static final float HIGH_END = 0.93333334F;
   private static final float PEAK_SIZE = 0.1F;
   public static final float PEAK_START = 0.56666666F;
   private static final float PEAK_END = 0.7666667F;
   public static final float NEAR_INLAND_START = -0.11F;
   public static final float MID_INLAND_START = 0.03F;
   public static final float FAR_INLAND_START = 0.3F;
   public static final float EROSION_INDEX_1_START = -0.78F;
   public static final float EROSION_INDEX_2_START = -0.375F;
   private static final float EROSION_DEEP_DARK_DRYNESS_THRESHOLD = -0.225F;
   private static final float DEPTH_DEEP_DARK_DRYNESS_THRESHOLD = 0.9F;
   private final Climate.Parameter FULL_RANGE = Climate.Parameter.span(-1.0F, 1.0F);
   private final Climate.Parameter[] temperatures = new Climate.Parameter[]{
      Climate.Parameter.span(-1.0F, -0.45F),
      Climate.Parameter.span(-0.45F, -0.15F),
      Climate.Parameter.span(-0.15F, 0.2F),
      Climate.Parameter.span(0.2F, 0.55F),
      Climate.Parameter.span(0.55F, 1.0F)
   };
   private final Climate.Parameter[] humidities = new Climate.Parameter[]{
      Climate.Parameter.span(-1.0F, -0.35F),
      Climate.Parameter.span(-0.35F, -0.1F),
      Climate.Parameter.span(-0.1F, 0.1F),
      Climate.Parameter.span(0.1F, 0.3F),
      Climate.Parameter.span(0.3F, 1.0F)
   };
   private final Climate.Parameter[] erosions = new Climate.Parameter[]{
      Climate.Parameter.span(-1.0F, -0.78F),
      Climate.Parameter.span(-0.78F, -0.375F),
      Climate.Parameter.span(-0.375F, -0.2225F),
      Climate.Parameter.span(-0.2225F, 0.05F),
      Climate.Parameter.span(0.05F, 0.45F),
      Climate.Parameter.span(0.45F, 0.55F),
      Climate.Parameter.span(0.55F, 1.0F)
   };
   private final Climate.Parameter FROZEN_RANGE = this.temperatures[0];
   private final Climate.Parameter UNFROZEN_RANGE = Climate.Parameter.span(this.temperatures[1], this.temperatures[4]);
   private final Climate.Parameter mushroomFieldsContinentalness = Climate.Parameter.span(-1.2F, -1.05F);
   private final Climate.Parameter deepOceanContinentalness = Climate.Parameter.span(-1.05F, -0.455F);
   private final Climate.Parameter oceanContinentalness = Climate.Parameter.span(-0.455F, -0.19F);
   private final Climate.Parameter coastContinentalness = Climate.Parameter.span(-0.19F, -0.11F);
   private final Climate.Parameter inlandContinentalness = Climate.Parameter.span(-0.11F, 0.55F);
   private final Climate.Parameter nearInlandContinentalness = Climate.Parameter.span(-0.11F, 0.03F);
   private final Climate.Parameter midInlandContinentalness = Climate.Parameter.span(0.03F, 0.3F);
   private final Climate.Parameter farInlandContinentalness = Climate.Parameter.span(0.3F, 1.0F);
   private final Key[][] OCEANS = new Key[][]{
      {Key.key("minecraft:deep_frozen_ocean"), Key.key("minecraft:deep_cold_ocean"), Key.key("minecraft:deep_ocean"), Key.key("minecraft:deep_lukewarm_ocean"), Key.key("minecraft:warm_ocean")},
      {Key.key("minecraft:frozen_ocean"), Key.key("minecraft:cold_ocean"), Key.key("minecraft:ocean"), Key.key("minecraft:lukewarm_ocean"), Key.key("minecraft:warm_ocean")}
   };
   private final Key[][] MIDDLE_BIOMES = new Key[][]{
      {Key.key("minecraft:snowy_plains"), Key.key("minecraft:snowy_plains"), Key.key("minecraft:snowy_plains"), Key.key("minecraft:snowy_taiga"), Key.key("minecraft:taiga")},
      {Key.key("minecraft:plains"), Key.key("minecraft:plains"), Key.key("minecraft:forest"), Key.key("minecraft:taiga"), Key.key("minecraft:old_growth_spruce_taiga")},
      {Key.key("minecraft:flower_forest"), Key.key("minecraft:plains"), Key.key("minecraft:forest"), Key.key("minecraft:birch_forest"), Key.key("minecraft:dark_forest")},
      {Key.key("minecraft:savanna"), Key.key("minecraft:savanna"), Key.key("minecraft:forest"), Key.key("minecraft:jungle"), Key.key("minecraft:jungle")},
      {Key.key("minecraft:desert"), Key.key("minecraft:desert"), Key.key("minecraft:desert"), Key.key("minecraft:desert"), Key.key("minecraft:desert")}
   };
   private final Key[][] MIDDLE_BIOMES_VARIANT = new Key[][]{
      {Key.key("minecraft:ice_spikes"), null, Key.key("minecraft:snowy_taiga"), null, null},
      {null, null, null, null, Key.key("minecraft:old_growth_pine_taiga")},
      {Key.key("minecraft:sunflower_plains"), null, null, Key.key("minecraft:old_growth_birch_forest"), null},
      {null, null, Key.key("minecraft:plains"), Key.key("minecraft:sparse_jungle"), Key.key("minecraft:bamboo_jungle")},
      {null, null, null, null, null}
   };
   private final Key[][] PLATEAU_BIOMES = new Key[][]{
      {Key.key("minecraft:snowy_plains"), Key.key("minecraft:snowy_plains"), Key.key("minecraft:snowy_plains"), Key.key("minecraft:snowy_taiga"), Key.key("minecraft:snowy_taiga")},
      {Key.key("minecraft:meadow"), Key.key("minecraft:meadow"), Key.key("minecraft:forest"), Key.key("minecraft:taiga"), Key.key("minecraft:old_growth_spruce_taiga")},
      {Key.key("minecraft:meadow"), Key.key("minecraft:meadow"), Key.key("minecraft:meadow"), Key.key("minecraft:meadow"), Key.key("minecraft:pale_garden")},
      {Key.key("minecraft:savanna_plateau"), Key.key("minecraft:savanna_plateau"), Key.key("minecraft:forest"), Key.key("minecraft:forest"), Key.key("minecraft:jungle")},
      {Key.key("minecraft:badlands"), Key.key("minecraft:badlands"), Key.key("minecraft:badlands"), Key.key("minecraft:wooded_badlands"), Key.key("minecraft:wooded_badlands")}
   };
   private final Key[][] PLATEAU_BIOMES_VARIANT = new Key[][]{
      {Key.key("minecraft:ice_spikes"), null, null, null, null},
      {Key.key("minecraft:cherry_grove"), null, Key.key("minecraft:meadow"), Key.key("minecraft:meadow"), Key.key("minecraft:old_growth_pine_taiga")},
      {Key.key("minecraft:cherry_grove"), Key.key("minecraft:cherry_grove"), Key.key("minecraft:forest"), Key.key("minecraft:birch_forest"), null},
      {null, null, null, null, null},
      {Key.key("minecraft:eroded_badlands"), Key.key("minecraft:eroded_badlands"), null, null, null}
   };
   private final Key[][] SHATTERED_BIOMES = new Key[][]{
      {Key.key("minecraft:windswept_gravelly_hills"), Key.key("minecraft:windswept_gravelly_hills"), Key.key("minecraft:windswept_hills"), Key.key("minecraft:windswept_forest"), Key.key("minecraft:windswept_forest")},
      {Key.key("minecraft:windswept_gravelly_hills"), Key.key("minecraft:windswept_gravelly_hills"), Key.key("minecraft:windswept_hills"), Key.key("minecraft:windswept_forest"), Key.key("minecraft:windswept_forest")},
      {Key.key("minecraft:windswept_hills"), Key.key("minecraft:windswept_hills"), Key.key("minecraft:windswept_hills"), Key.key("minecraft:windswept_forest"), Key.key("minecraft:windswept_forest")},
      {null, null, null, null, null},
      {null, null, null, null, null}
   };

   public List<Climate.ParameterPoint> spawnTarget() {
       var parameter = Climate.Parameter.point(0.0F);
       var f = 0.16F;
      return List.of(
         new Climate.ParameterPoint(
            this.FULL_RANGE,
            this.FULL_RANGE,
            Climate.Parameter.span(this.inlandContinentalness, this.FULL_RANGE),
            this.FULL_RANGE,
            parameter,
            Climate.Parameter.span(-1.0F, -0.16F),
            0L
         ),
         new Climate.ParameterPoint(
            this.FULL_RANGE,
            this.FULL_RANGE,
            Climate.Parameter.span(this.inlandContinentalness, this.FULL_RANGE),
            this.FULL_RANGE,
            parameter,
            Climate.Parameter.span(0.16F, 1.0F),
            0L
         )
      );
   }

   protected void addBiomes(Consumer<Pair<Climate.ParameterPoint, Key>> consumer) {
      this.addOffCoastBiomes(consumer);
      this.addInlandBiomes(consumer);
      this.addUndergroundBiomes(consumer);
   }

   private void addOffCoastBiomes(Consumer<Pair<Climate.ParameterPoint, Key>> consumer) {
      this.addSurfaceBiome(
         consumer, this.FULL_RANGE, this.FULL_RANGE, this.mushroomFieldsContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, Key.key("minecraft:mushroom_fields")
      );

      for (var temperatureIndex = 0; temperatureIndex < this.temperatures.length; temperatureIndex++) {
         var temperature = this.temperatures[temperatureIndex];
         this.addSurfaceBiome(consumer, temperature, this.FULL_RANGE, this.deepOceanContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[0][temperatureIndex]);
         this.addSurfaceBiome(consumer, temperature, this.FULL_RANGE, this.oceanContinentalness, this.FULL_RANGE, this.FULL_RANGE, 0.0F, this.OCEANS[1][temperatureIndex]);
      }
   }

   private void addInlandBiomes(Consumer<Pair<Climate.ParameterPoint, Key>> consumer) {
      this.addMidSlice(consumer, Climate.Parameter.span(-1.0F, -0.93333334F));
      this.addHighSlice(consumer, Climate.Parameter.span(-0.93333334F, -0.7666667F));
      this.addPeaks(consumer, Climate.Parameter.span(-0.7666667F, -0.56666666F));
      this.addHighSlice(consumer, Climate.Parameter.span(-0.56666666F, -0.4F));
      this.addMidSlice(consumer, Climate.Parameter.span(-0.4F, -0.26666668F));
      this.addLowSlice(consumer, Climate.Parameter.span(-0.26666668F, -0.05F));
      this.addValleys(consumer, Climate.Parameter.span(-0.05F, 0.05F));
      this.addLowSlice(consumer, Climate.Parameter.span(0.05F, 0.26666668F));
      this.addMidSlice(consumer, Climate.Parameter.span(0.26666668F, 0.4F));
      this.addHighSlice(consumer, Climate.Parameter.span(0.4F, 0.56666666F));
      this.addPeaks(consumer, Climate.Parameter.span(0.56666666F, 0.7666667F));
      this.addHighSlice(consumer, Climate.Parameter.span(0.7666667F, 0.93333334F));
      this.addMidSlice(consumer, Climate.Parameter.span(0.93333334F, 1.0F));
   }

   private void addPeaks(Consumer<Pair<Climate.ParameterPoint, Key>> consumer, Climate.Parameter parameter) {
      for (var temperatureIndex = 0; temperatureIndex < this.temperatures.length; temperatureIndex++) {
         var temperature = this.temperatures[temperatureIndex];

         for (var humidityIndex = 0; humidityIndex < this.humidities.length; humidityIndex++) {
            var humidity = this.humidities[humidityIndex];
            var middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlands = this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlandsOrSlope = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, parameter);
            var plateauBiome = this.pickPlateauBiome(temperatureIndex, humidityIndex, parameter);
            var shatteredBiome = this.pickShatteredBiome(temperatureIndex, humidityIndex, parameter);
            var maybeWindsweptSavanna = this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, parameter, shatteredBiome);
            var peakBiome = this.pickPeakBiome(temperatureIndex, humidityIndex, parameter);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
               this.erosions[0],
               parameter,
               0.0F,
               peakBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
               this.erosions[1],
               parameter,
               0.0F,
               middleOrBadlandsOrSlope
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[1],
               parameter,
               0.0F,
               peakBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
               Climate.Parameter.span(this.erosions[2], this.erosions[3]),
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[2],
               parameter,
               0.0F,
               plateauBiome
            );
            this.addSurfaceBiome(consumer, temperature, humidity, this.midInlandContinentalness, this.erosions[3], parameter, 0.0F, middleOrBadlands);
            this.addSurfaceBiome(consumer, temperature, humidity, this.farInlandContinentalness, this.erosions[3], parameter, 0.0F, plateauBiome);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
               this.erosions[4],
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
               this.erosions[5],
               parameter,
               0.0F,
               maybeWindsweptSavanna
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[5],
               parameter,
               0.0F,
               shatteredBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
               this.erosions[6],
               parameter,
               0.0F,
               middleBiome
            );
         }
      }
   }

   private void addHighSlice(Consumer<Pair<Climate.ParameterPoint, Key>> consumer, Climate.Parameter parameter) {
      for (var temperatureIndex = 0; temperatureIndex < this.temperatures.length; temperatureIndex++) {
         var temperature = this.temperatures[temperatureIndex];

         for (var humidityIndex = 0; humidityIndex < this.humidities.length; humidityIndex++) {
            var humidity = this.humidities[humidityIndex];
            var middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlands = this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlandsOrSlope = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, parameter);
            var plateauBiome = this.pickPlateauBiome(temperatureIndex, humidityIndex, parameter);
            var shatteredBiome = this.pickShatteredBiome(temperatureIndex, humidityIndex, parameter);
            var maybeWindsweptSavanna = this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, parameter, middleBiome);
            var slopeBiome = this.pickSlopeBiome(temperatureIndex, humidityIndex, parameter);
            var peakBiome = this.pickPeakBiome(temperatureIndex, humidityIndex, parameter);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               this.coastContinentalness,
               Climate.Parameter.span(this.erosions[0], this.erosions[1]),
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(consumer, temperature, humidity, this.nearInlandContinentalness, this.erosions[0], parameter, 0.0F, slopeBiome);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[0],
               parameter,
               0.0F,
               peakBiome
            );
            this.addSurfaceBiome(consumer, temperature, humidity, this.nearInlandContinentalness, this.erosions[1], parameter, 0.0F, middleOrBadlandsOrSlope);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[1],
               parameter,
               0.0F,
               slopeBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
               Climate.Parameter.span(this.erosions[2], this.erosions[3]),
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[2],
               parameter,
               0.0F,
               plateauBiome
            );
            this.addSurfaceBiome(consumer, temperature, humidity, this.midInlandContinentalness, this.erosions[3], parameter, 0.0F, middleOrBadlands);
            this.addSurfaceBiome(consumer, temperature, humidity, this.farInlandContinentalness, this.erosions[3], parameter, 0.0F, plateauBiome);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
               this.erosions[4],
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
               this.erosions[5],
               parameter,
               0.0F,
               maybeWindsweptSavanna
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[5],
               parameter,
               0.0F,
               shatteredBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
               this.erosions[6],
               parameter,
               0.0F,
               middleBiome
            );
         }
      }
   }

   private void addMidSlice(Consumer<Pair<Climate.ParameterPoint, Key>> consumer, Climate.Parameter parameter) {
      this.addSurfaceBiome(
         consumer,
         this.FULL_RANGE,
         this.FULL_RANGE,
         this.coastContinentalness,
         Climate.Parameter.span(this.erosions[0], this.erosions[2]),
         parameter,
         0.0F,
         Key.key("minecraft:stony_shore")
      );
      this.addSurfaceBiome(
         consumer,
         Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
         this.FULL_RANGE,
         Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
         this.erosions[6],
         parameter,
         0.0F,
         Key.key("minecraft:swamp")
      );
      this.addSurfaceBiome(
         consumer,
         Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
         this.FULL_RANGE,
         Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
         this.erosions[6],
         parameter,
         0.0F,
         Key.key("minecraft:mangrove_swamp")
      );

      for (var temperatureIndex = 0; temperatureIndex < this.temperatures.length; temperatureIndex++) {
         var temperature = this.temperatures[temperatureIndex];

         for (var humidityIndex = 0; humidityIndex < this.humidities.length; humidityIndex++) {
            var humidity = this.humidities[humidityIndex];
            var middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlands = this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlandsOrSlope = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, parameter);
            var shatteredBiome = this.pickShatteredBiome(temperatureIndex, humidityIndex, parameter);
            var plateauBiome = this.pickPlateauBiome(temperatureIndex, humidityIndex, parameter);
            var beachBiome = this.pickBeachBiome(temperatureIndex, humidityIndex);
            var maybeWindsweptSavanna = this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, parameter, middleBiome);
            var shatteredCoastBiome = this.pickShatteredCoastBiome(temperatureIndex, humidityIndex, parameter);
            var slopeBiome = this.pickSlopeBiome(temperatureIndex, humidityIndex, parameter);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
               this.erosions[0],
               parameter,
               0.0F,
               slopeBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.nearInlandContinentalness, this.midInlandContinentalness),
               this.erosions[1],
               parameter,
               0.0F,
               middleOrBadlandsOrSlope
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               this.farInlandContinentalness,
               this.erosions[1],
               parameter,
               0.0F,
               temperatureIndex == 0 ? slopeBiome : plateauBiome
            );
            this.addSurfaceBiome(consumer, temperature, humidity, this.nearInlandContinentalness, this.erosions[2], parameter, 0.0F, middleBiome);
            this.addSurfaceBiome(consumer, temperature, humidity, this.midInlandContinentalness, this.erosions[2], parameter, 0.0F, middleOrBadlands);
            this.addSurfaceBiome(consumer, temperature, humidity, this.farInlandContinentalness, this.erosions[2], parameter, 0.0F, plateauBiome);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.coastContinentalness, this.nearInlandContinentalness),
               this.erosions[3],
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[3],
               parameter,
               0.0F,
               middleOrBadlands
            );
            if (parameter.max() < 0L) {
               this.addSurfaceBiome(consumer, temperature, humidity, this.coastContinentalness, this.erosions[4], parameter, 0.0F, beachBiome);
               this.addSurfaceBiome(
                  consumer,
                  temperature,
                  humidity,
                  Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                  this.erosions[4],
                  parameter,
                  0.0F,
                  middleBiome
               );
            } else {
               this.addSurfaceBiome(
                  consumer,
                  temperature,
                  humidity,
                  Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
                  this.erosions[4],
                  parameter,
                  0.0F,
                  middleBiome
               );
            }

            this.addSurfaceBiome(consumer, temperature, humidity, this.coastContinentalness, this.erosions[5], parameter, 0.0F, shatteredCoastBiome);
            this.addSurfaceBiome(consumer, temperature, humidity, this.nearInlandContinentalness, this.erosions[5], parameter, 0.0F, maybeWindsweptSavanna);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[5],
               parameter,
               0.0F,
               shatteredBiome
            );
            if (parameter.max() < 0L) {
               this.addSurfaceBiome(consumer, temperature, humidity, this.coastContinentalness, this.erosions[6], parameter, 0.0F, beachBiome);
            } else {
               this.addSurfaceBiome(consumer, temperature, humidity, this.coastContinentalness, this.erosions[6], parameter, 0.0F, middleBiome);
            }

            if (temperatureIndex == 0) {
               this.addSurfaceBiome(
                  consumer,
                  temperature,
                  humidity,
                  Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                  this.erosions[6],
                  parameter,
                  0.0F,
                  middleBiome
               );
            }
         }
      }
   }

   private void addLowSlice(Consumer<Pair<Climate.ParameterPoint, Key>> consumer, Climate.Parameter parameter) {
      this.addSurfaceBiome(
         consumer,
         this.FULL_RANGE,
         this.FULL_RANGE,
         this.coastContinentalness,
         Climate.Parameter.span(this.erosions[0], this.erosions[2]),
         parameter,
         0.0F,
         Key.key("minecraft:stony_shore")
      );
      this.addSurfaceBiome(
         consumer,
         Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
         this.FULL_RANGE,
         Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
         this.erosions[6],
         parameter,
         0.0F,
         Key.key("minecraft:swamp")
      );
      this.addSurfaceBiome(
         consumer,
         Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
         this.FULL_RANGE,
         Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
         this.erosions[6],
         parameter,
         0.0F,
         Key.key("minecraft:mangrove_swamp")
      );

      for (var temperatureIndex = 0; temperatureIndex < this.temperatures.length; temperatureIndex++) {
         var temperature = this.temperatures[temperatureIndex];

         for (var humidityIndex = 0; humidityIndex < this.humidities.length; humidityIndex++) {
            var humidity = this.humidities[humidityIndex];
            var middleBiome = this.pickMiddleBiome(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlands = this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, parameter);
            var middleOrBadlandsOrSlope = this.pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(temperatureIndex, humidityIndex, parameter);
            var beachBiome = this.pickBeachBiome(temperatureIndex, humidityIndex);
            var maybeWindsweptSavanna = this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, parameter, middleBiome);
            var shatteredCoastBiome = this.pickShatteredCoastBiome(temperatureIndex, humidityIndex, parameter);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               this.nearInlandContinentalness,
               Climate.Parameter.span(this.erosions[0], this.erosions[1]),
               parameter,
               0.0F,
               middleOrBadlands
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               Climate.Parameter.span(this.erosions[0], this.erosions[1]),
               parameter,
               0.0F,
               middleOrBadlandsOrSlope
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               this.nearInlandContinentalness,
               Climate.Parameter.span(this.erosions[2], this.erosions[3]),
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               Climate.Parameter.span(this.erosions[2], this.erosions[3]),
               parameter,
               0.0F,
               middleOrBadlands
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               this.coastContinentalness,
               Climate.Parameter.span(this.erosions[3], this.erosions[4]),
               parameter,
               0.0F,
               beachBiome
            );
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
               this.erosions[4],
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(consumer, temperature, humidity, this.coastContinentalness, this.erosions[5], parameter, 0.0F, shatteredCoastBiome);
            this.addSurfaceBiome(consumer, temperature, humidity, this.nearInlandContinentalness, this.erosions[5], parameter, 0.0F, maybeWindsweptSavanna);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               this.erosions[5],
               parameter,
               0.0F,
               middleBiome
            );
            this.addSurfaceBiome(consumer, temperature, humidity, this.coastContinentalness, this.erosions[6], parameter, 0.0F, beachBiome);
            if (temperatureIndex == 0) {
               this.addSurfaceBiome(
                  consumer,
                  temperature,
                  humidity,
                  Climate.Parameter.span(this.nearInlandContinentalness, this.farInlandContinentalness),
                  this.erosions[6],
                  parameter,
                  0.0F,
                  middleBiome
               );
            }
         }
      }
   }

   private void addValleys(Consumer<Pair<Climate.ParameterPoint, Key>> consumer, Climate.Parameter parameter) {
      this.addSurfaceBiome(
         consumer,
         this.FROZEN_RANGE,
         this.FULL_RANGE,
         this.coastContinentalness,
         Climate.Parameter.span(this.erosions[0], this.erosions[1]),
         parameter,
         0.0F,
         parameter.max() < 0L ? Key.key("minecraft:stony_shore") : Key.key("minecraft:frozen_river")
      );
      this.addSurfaceBiome(
         consumer,
         this.UNFROZEN_RANGE,
         this.FULL_RANGE,
         this.coastContinentalness,
         Climate.Parameter.span(this.erosions[0], this.erosions[1]),
         parameter,
         0.0F,
         parameter.max() < 0L ? Key.key("minecraft:stony_shore") : Key.key("minecraft:river")
      );
      this.addSurfaceBiome(
         consumer,
         this.FROZEN_RANGE,
         this.FULL_RANGE,
         this.nearInlandContinentalness,
         Climate.Parameter.span(this.erosions[0], this.erosions[1]),
         parameter,
         0.0F,
         Key.key("minecraft:frozen_river")
      );
      this.addSurfaceBiome(
         consumer,
         this.UNFROZEN_RANGE,
         this.FULL_RANGE,
         this.nearInlandContinentalness,
         Climate.Parameter.span(this.erosions[0], this.erosions[1]),
         parameter,
         0.0F,
         Key.key("minecraft:river")
      );
      this.addSurfaceBiome(
         consumer,
         this.FROZEN_RANGE,
         this.FULL_RANGE,
         Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
         Climate.Parameter.span(this.erosions[2], this.erosions[5]),
         parameter,
         0.0F,
         Key.key("minecraft:frozen_river")
      );
      this.addSurfaceBiome(
         consumer,
         this.UNFROZEN_RANGE,
         this.FULL_RANGE,
         Climate.Parameter.span(this.coastContinentalness, this.farInlandContinentalness),
         Climate.Parameter.span(this.erosions[2], this.erosions[5]),
         parameter,
         0.0F,
         Key.key("minecraft:river")
      );
      this.addSurfaceBiome(consumer, this.FROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, this.erosions[6], parameter, 0.0F, Key.key("minecraft:frozen_river"));
      this.addSurfaceBiome(consumer, this.UNFROZEN_RANGE, this.FULL_RANGE, this.coastContinentalness, this.erosions[6], parameter, 0.0F, Key.key("minecraft:river"));
      this.addSurfaceBiome(
         consumer,
         Climate.Parameter.span(this.temperatures[1], this.temperatures[2]),
         this.FULL_RANGE,
         Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
         this.erosions[6],
         parameter,
         0.0F,
         Key.key("minecraft:swamp")
      );
      this.addSurfaceBiome(
         consumer,
         Climate.Parameter.span(this.temperatures[3], this.temperatures[4]),
         this.FULL_RANGE,
         Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
         this.erosions[6],
         parameter,
         0.0F,
         Key.key("minecraft:mangrove_swamp")
      );
      this.addSurfaceBiome(
         consumer,
         this.FROZEN_RANGE,
         this.FULL_RANGE,
         Climate.Parameter.span(this.inlandContinentalness, this.farInlandContinentalness),
         this.erosions[6],
         parameter,
         0.0F,
         Key.key("minecraft:frozen_river")
      );

      for (var temperatureIndex = 0; temperatureIndex < this.temperatures.length; temperatureIndex++) {
         var temperature = this.temperatures[temperatureIndex];

         for (var humidityIndex = 0; humidityIndex < this.humidities.length; humidityIndex++) {
            var humidity = this.humidities[humidityIndex];
            var middleOrBadlands = this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, parameter);
            this.addSurfaceBiome(
               consumer,
               temperature,
               humidity,
               Climate.Parameter.span(this.midInlandContinentalness, this.farInlandContinentalness),
               Climate.Parameter.span(this.erosions[0], this.erosions[1]),
               parameter,
               0.0F,
               middleOrBadlands
            );
         }
      }
   }

   private void addUndergroundBiomes(Consumer<Pair<Climate.ParameterPoint, Key>> consumer) {
      this.addUndergroundBiome(
         consumer, this.FULL_RANGE, this.FULL_RANGE, Climate.Parameter.span(0.8F, 1.0F), this.FULL_RANGE, this.FULL_RANGE, 0.0F, Key.key("minecraft:dripstone_caves")
      );
      this.addUndergroundBiome(
         consumer, this.FULL_RANGE, Climate.Parameter.span(0.7F, 1.0F), this.FULL_RANGE, this.FULL_RANGE, this.FULL_RANGE, 0.0F, Key.key("minecraft:lush_caves")
      );
      this.addBottomBiome(
         consumer,
         this.FULL_RANGE,
         this.FULL_RANGE,
         this.FULL_RANGE,
         Climate.Parameter.span(this.erosions[0], this.erosions[1]),
         this.FULL_RANGE,
         0.0F,
         Key.key("minecraft:deep_dark")
      );
   }

   private Key pickMiddleBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      if (weirdness.max() < 0L) {
         return this.MIDDLE_BIOMES[temperatureIndex][humidityIndex];
      } else {
         var variant = this.MIDDLE_BIOMES_VARIANT[temperatureIndex][humidityIndex];
         return variant == null ? this.MIDDLE_BIOMES[temperatureIndex][humidityIndex] : variant;
      }
   }

   private Key pickMiddleBiomeOrBadlandsIfHot(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      return temperatureIndex == 4 ? this.pickBadlandsBiome(humidityIndex, weirdness) : this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness);
   }

   private Key pickMiddleBiomeOrBadlandsIfHotOrSlopeIfCold(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      return temperatureIndex == 0
            ? this.pickSlopeBiome(temperatureIndex, humidityIndex, weirdness)
            : this.pickMiddleBiomeOrBadlandsIfHot(temperatureIndex, humidityIndex, weirdness);
   }

   private Key maybePickWindsweptSavannaBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness, Key baseBiome) {
      return temperatureIndex > 1 && humidityIndex < 4 && weirdness.max() >= 0L ? Key.key("minecraft:windswept_savanna") : baseBiome;
   }

   private Key pickShatteredCoastBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      var baseBiome = weirdness.max() >= 0L
            ? this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness)
            : this.pickBeachBiome(temperatureIndex, humidityIndex);
      return this.maybePickWindsweptSavannaBiome(temperatureIndex, humidityIndex, weirdness, baseBiome);
   }

   private Key pickBeachBiome(int temperatureIndex, int humidityIndex) {
      if (temperatureIndex == 0) {
         return Key.key("minecraft:snowy_beach");
      } else {
         return temperatureIndex == 4 ? Key.key("minecraft:desert") : Key.key("minecraft:beach");
      }
   }

   private Key pickBadlandsBiome(int humidityIndex, Climate.Parameter weirdness) {
      if (humidityIndex < 2) {
         return weirdness.max() < 0L ? Key.key("minecraft:badlands") : Key.key("minecraft:eroded_badlands");
      } else {
         return humidityIndex < 3 ? Key.key("minecraft:badlands") : Key.key("minecraft:wooded_badlands");
      }
   }

   private Key pickPlateauBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      if (weirdness.max() >= 0L) {
         var variant = this.PLATEAU_BIOMES_VARIANT[temperatureIndex][humidityIndex];
         if (variant != null) {
            return variant;
         }
      }

      return this.PLATEAU_BIOMES[temperatureIndex][humidityIndex];
   }

   private Key pickPeakBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      if (temperatureIndex <= 2) {
         return weirdness.max() < 0L ? Key.key("minecraft:jagged_peaks") : Key.key("minecraft:frozen_peaks");
      } else {
         return temperatureIndex == 3 ? Key.key("minecraft:stony_peaks") : this.pickBadlandsBiome(humidityIndex, weirdness);
      }
   }

   private Key pickSlopeBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      if (temperatureIndex >= 3) {
         return this.pickPlateauBiome(temperatureIndex, humidityIndex, weirdness);
      } else {
         return humidityIndex <= 1 ? Key.key("minecraft:snowy_slopes") : Key.key("minecraft:grove");
      }
   }

   private Key pickShatteredBiome(int temperatureIndex, int humidityIndex, Climate.Parameter weirdness) {
      var shattered = this.SHATTERED_BIOMES[temperatureIndex][humidityIndex];
      return shattered == null ? this.pickMiddleBiome(temperatureIndex, humidityIndex, weirdness) : shattered;
   }

   private void addSurfaceBiome(
      Consumer<Pair<Climate.ParameterPoint, Key>> consumer,
      Climate.Parameter temperature,
      Climate.Parameter humidity,
      Climate.Parameter continentalness,
      Climate.Parameter erosion,
      Climate.Parameter weirdness,
      float offset,
      Key biome
   ) {
      consumer.accept(new Pair<>(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(0.0F), weirdness, offset), biome));
      consumer.accept(new Pair<>(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.0F), weirdness, offset), biome));
   }

   private void addUndergroundBiome(
      Consumer<Pair<Climate.ParameterPoint, Key>> consumer,
      Climate.Parameter temperature,
      Climate.Parameter humidity,
      Climate.Parameter continentalness,
      Climate.Parameter erosion,
      Climate.Parameter weirdness,
      float offset,
      Key biome
   ) {
      consumer.accept(
         new Pair<>(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.span(0.2F, 0.9F), weirdness, offset), biome)
      );
   }

   private void addBottomBiome(
      Consumer<Pair<Climate.ParameterPoint, Key>> consumer,
      Climate.Parameter temperature,
      Climate.Parameter humidity,
      Climate.Parameter continentalness,
      Climate.Parameter erosion,
      Climate.Parameter weirdness,
      float offset,
      Key biome
   ) {
      consumer.accept(new Pair<>(Climate.parameters(temperature, humidity, continentalness, erosion, Climate.Parameter.point(1.1F), weirdness, offset), biome));
   }

   // Debug helpers removed; generator uses only parameter list construction.
}
