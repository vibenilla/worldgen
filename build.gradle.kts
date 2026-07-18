plugins {
    `java-library`
    id("com.vanniktech.maven.publish") version "0.36.0"
}

description = "A library for Minestom worldgen"
group = "rocks.minestom"
version = "0.2.0"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

mavenPublishing {
    coordinates(group.toString(), project.name, version.toString())
    publishToMavenCentral()

    if (!gradle.startParameter.taskNames.any { it.contains("publishToMavenLocal") }) {
        signAllPublications()
    }

    pom {
        name = project.name
        description = project.description
        url = "https://github.com/vibenilla/worldgen"

        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
            }
        }

        developers {
            developer {
                name = "mudkip"
                id = "mudkipdev"
                email = "mudkip@mudkip.dev"
                url = "https://mudkip.dev"
            }
        }

        scm {
            url = "https://github.com/vibenilla/worldgen"
            connection = "scm:git:git://github.com/vibenilla/worldgen.git"
            developerConnection = "scm:git:ssh://git@github.com/vibenilla/worldgen.git"
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.minestom:minestom:2026.07.12-26.2")
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    testImplementation("net.minestom:minestom:2026.07.12-26.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    // Unobfuscated vanilla server for side-by-side parity tests (gitignored, see data/setup_mc.sh)
    testCompileOnly(files("data/mc/26.2/server-stripped.jar"))
    testCompileOnly(fileTree("data/mc/26.2/libraries") { include("*.jar") })
    testRuntimeOnly(files("data/mc/26.2/server.jar"))
    testRuntimeOnly(fileTree("data/mc/26.2/libraries") { include("*.jar") })
}

tasks.test {
    useJUnitPlatform()
}

val mcVersion = "26.2"

tasks.register("setupVanillaServer") {
    group = "verification"
    description = "Downloads the vanilla server and prepares the jars and datapack used by the parity tests"
    doLast {
        val mcDirectory = layout.projectDirectory.dir("data/mc").asFile
        val versionDirectory = mcDirectory.resolve(mcVersion)
        versionDirectory.mkdirs()

        val versionJson = versionDirectory.resolve("version.json")
        if (!versionJson.exists()) {
            val manifest = groovy.json.JsonSlurper()
                .parse(uri("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").toURL()) as Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val versionUrl = (manifest["versions"] as List<Map<*, *>>)
                .first { it["id"] == mcVersion }["url"] as String
            versionJson.writeBytes(uri(versionUrl).toURL().readBytes())
        }

        val bundlerJar = versionDirectory.resolve("server-bundler.jar")
        if (!bundlerJar.exists()) {
            val versionInfo = groovy.json.JsonSlurper().parse(versionJson) as Map<*, *>
            val serverUrl = ((versionInfo["downloads"] as Map<*, *>)["server"] as Map<*, *>)["url"] as String
            bundlerJar.writeBytes(uri(serverUrl).toURL().readBytes())
        }

        val serverJar = versionDirectory.resolve("server.jar")
        if (!serverJar.exists()) {
            copy {
                from(zipTree(bundlerJar))
                include("META-INF/versions/*/server-*.jar")
                eachFile { path = name }
                includeEmptyDirs = false
                into(versionDirectory)
            }
            check(versionDirectory.resolve("server-$mcVersion.jar").renameTo(serverJar)) {
                "server-$mcVersion.jar not found in bundler"
            }
        }

        val librariesDirectory = versionDirectory.resolve("libraries")
        if (!librariesDirectory.isDirectory) {
            copy {
                from(zipTree(bundlerJar))
                include("META-INF/libraries/**/*.jar")
                eachFile { path = name }
                includeEmptyDirs = false
                into(librariesDirectory)
            }
        }

        val strippedJar = versionDirectory.resolve("server-stripped.jar")
        if (!strippedJar.exists()) {
            val strip = ProcessBuilder(
                "java", file("scripts/StripTypeAnnotations.java").absolutePath,
                serverJar.absolutePath, strippedJar.absolutePath)
                .inheritIO().start()
            check(strip.waitFor() == 0) { "StripTypeAnnotations failed" }
        }

        val datapackDirectory = mcDirectory.resolve("datapack")
        if (!datapackDirectory.resolve("data").isDirectory) {
            copy {
                from(zipTree(serverJar))
                include("data/**")
                into(datapackDirectory)
            }
        }
    }
}

tasks.compileTestJava {
    dependsOn("setupVanillaServer")
}

tasks.register<JavaExec>("demoServer") {
    group = "application"
    description = "Runs a demo server with all dimensions and vanilla-style commands"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.demo.DemoServer"
    jvmArgs("-Xmx4G", "-Dminestom.chunk-view-distance=32")
    args(
        providers.gradleProperty("port").getOrElse("25565"),
        providers.gradleProperty("seed").getOrElse("123456789")
    )
}

tasks.register<JavaExec>("compareVanilla") {
    group = "verification"
    description = "Compares generated chunks against a vanilla world"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.VanillaComparison"
    jvmArgs(providers.gradleProperty("heap").getOrElse("-Xmx4G"))
    if (providers.gradleProperty("jfr").isPresent) {
        jvmArgs("-XX:StartFlightRecording=duration=120s,filename=build/gen.jfr")
    }
    systemProperty("compare.sequential", providers.gradleProperty("sequential").getOrElse("false"))
    systemProperty("compare.diffblock", providers.gradleProperty("diffblock").getOrElse(""))
    systemProperty("compare.biomediffpos", providers.gradleProperty("biomediffpos").getOrElse("false"))
    systemProperty("compare.dimension", providers.gradleProperty("dimension").getOrElse("overworld"))
    systemProperty("compare.pregenRadius", providers.gradleProperty("pregenRadius").getOrElse("16"))
    providers.gradleProperty("debugchunk").orNull?.let { systemProperty("worldgen.debugchunk", it) }
    providers.gradleProperty("decorTrace").orNull?.let { systemProperty("worldgen.decorTrace", it) }
    providers.gradleProperty("decorTraceFeature").orNull?.let { systemProperty("worldgen.decorTraceFeature", it) }
    providers.gradleProperty("decorTraceMinY").orNull?.let { systemProperty("worldgen.decorTraceMinY", it) }
    providers.gradleProperty("decorTraceMaxY").orNull?.let { systemProperty("worldgen.decorTraceMaxY", it) }
    providers.gradleProperty("decorTraceReach").orNull?.let { systemProperty("worldgen.decorTraceReach", it) }
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-world/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("radius").getOrElse("8")
    )
}

tasks.register<JavaExec>("treeDiff") {
    group = "verification"
    description = "Prints tree-related block mismatches against the vanilla world"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.TreeDiff"
    jvmArgs("-Xmx4G")
    systemProperty("treediff.all", providers.gradleProperty("all").getOrElse("false"))
    systemProperty("treediff.reverse", providers.gradleProperty("reverse").getOrElse("false"))
    systemProperty("treediff.trunks", providers.gradleProperty("trunks").getOrElse("false"))
    systemProperty("treediff.dist7", providers.gradleProperty("dist7").getOrElse("false"))
    systemProperty("treediff.stacked", providers.gradleProperty("stacked").getOrElse("false"))
    systemProperty("worldgen.treeTrace", providers.gradleProperty("trace").getOrElse(""))
    providers.gradleProperty("traceBox").orNull?.let { systemProperty("worldgen.treeTraceBox", it) }
    providers.gradleProperty("traceReach").orNull?.let { systemProperty("worldgen.treeTraceReach", it) }
    providers.gradleProperty("debugInSquare").orNull?.let { systemProperty("worldgen.debugInSquare", it) }
    providers.gradleProperty("column").orNull?.let { systemProperty("treediff.column", it) }
    providers.gradleProperty("columnMinY").orNull?.let { systemProperty("treediff.columnMinY", it) }
    providers.gradleProperty("columnMaxY").orNull?.let { systemProperty("treediff.columnMaxY", it) }
    providers.gradleProperty("vanillacolumn").orNull?.let { systemProperty("treediff.vanillacolumn", it) }
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-world/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("radius").getOrElse("3")
    )
}

tasks.register<JavaExec>("litterReplay") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.LitterReplay"
    jvmArgs("-Xmx2G")
    providers.gradleProperty("offsets").orNull?.let { systemProperty("replay.offsets", it) }
    providers.gradleProperty("vlitter").orNull?.let { systemProperty("replay.vlitter", it) }
    args(
        providers.gradleProperty("traceFile").getOrElse("/tmp/trace.txt"),
        providers.gradleProperty("featureJson").getOrElse("data/mc/datapack/data/minecraft/worldgen/configured_feature/oak_bees_0002_leaf_litter.json"),
        providers.gradleProperty("treeKey").getOrElse("45:65:-30")
    )
}


tasks.register<JavaExec>("treeReplay") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.TreeReplay"
    jvmArgs("-Xmx2G")
    systemProperty("replay.frozenHeightmap", providers.gradleProperty("frozen").getOrElse("false"))
    args(
        providers.gradleProperty("traceFile").getOrElse("/tmp/trace.txt"),
        providers.gradleProperty("featureJson").getOrElse("data/mc/datapack/data/minecraft/worldgen/configured_feature/oak_bees_0002_leaf_litter.json"),
        providers.gradleProperty("treeKey").getOrElse("pre:40:69:-32"),
        providers.gradleProperty("ox").getOrElse("40"),
        providers.gradleProperty("oy").getOrElse("69"),
        providers.gradleProperty("oz").getOrElse("-32")
    )
}

tasks.register<JavaExec>("jigsawPieceDiff") {
    group = "verification"
    description = "Compares assembled jigsaw piece lists against a vanilla world save"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.JigsawPieceDiff"
    jvmArgs("-Xmx4G")
    systemProperty("piecediff.all", providers.gradleProperty("all").getOrElse("false"))
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-world/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("chunkX").getOrElse("0"),
        providers.gradleProperty("chunkZ").getOrElse("0"),
        providers.gradleProperty("structure").getOrElse("minecraft:trial_chambers")
    )
}

tasks.register<JavaExec>("structureStartScan") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.StructureStartScan"
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-world/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("radius").getOrElse("10")
    )
}

tasks.register<JavaExec>("boxDiff") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.BoxDiff"
    jvmArgs("-Xmx4G")
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-world/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("minX").getOrElse("0"),
        providers.gradleProperty("minY").getOrElse("-64"),
        providers.gradleProperty("minZ").getOrElse("0"),
        providers.gradleProperty("maxX").getOrElse("0"),
        providers.gradleProperty("maxY").getOrElse("319"),
        providers.gradleProperty("maxZ").getOrElse("0")
    )
}

tasks.register<JavaExec>("vanillaPeek") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.VanillaPeek"
    args((providers.gradleProperty("peekArgs").getOrElse("")).split(" "))
}

tasks.register<JavaExec>("ourPeek") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.CaveDivergenceOurPeek"
    jvmArgs("-Xmx4G")
    args((providers.gradleProperty("peekArgs").getOrElse("")).split(" "))
}

tasks.register<JavaExec>("terrainPeek") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.CaveDivergenceTerrainPeek"
    jvmArgs("-Xmx4G")
    args((providers.gradleProperty("peekArgs").getOrElse("")).split(" "))
}

tasks.register<JavaExec>("biomePeek") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.CaveDivergenceBiomePeek"
    jvmArgs("-Xmx4G")
    args((providers.gradleProperty("peekArgs").getOrElse("")).split(" "))
}

tasks.register<JavaExec>("chunkVegReplay") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.ChunkVegetationReplay"
    jvmArgs("-Xmx2G")
    systemProperty("replay.logHeights", providers.gradleProperty("logHeights").getOrElse("false"))
    systemProperty("replay.traceReads", providers.gradleProperty("traceReads").getOrElse("false"))
    systemProperty("replay.debugDraws", providers.gradleProperty("debugDraws").getOrElse("0"))
    args(
        providers.gradleProperty("traceFile").getOrElse("/tmp/trace.txt"),
        providers.gradleProperty("featureJson").getOrElse("data/mc/datapack/data/minecraft/worldgen/placed_feature/dark_forest_vegetation.json"),
        providers.gradleProperty("treeKey").getOrElse("pre:-265:74:-263"),
        providers.gradleProperty("sx").getOrElse("-272"),
        providers.gradleProperty("sz").getOrElse("-272"),
        providers.gradleProperty("rewind").getOrElse("5"),
        providers.gradleProperty("biome").getOrElse("minecraft:dark_forest"),
        providers.gradleProperty("seed").getOrElse("")
    )
}

tasks.register<JavaExec>("locateOnly") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.LocateOnly"
    jvmArgs("-Xmx4G")
    args(
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("structure").getOrElse("minecraft:monument"),
        providers.gradleProperty("centerX").getOrElse("0"),
        providers.gradleProperty("centerZ").getOrElse("0"),
        providers.gradleProperty("radiusChunks").getOrElse("100")
    )
}

tasks.register<JavaExec>("monumentVerify") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.StructureVerify"
    jvmArgs("-Xmx4G")
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-monument/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("minChunkX").getOrElse("-10"),
        providers.gradleProperty("minChunkZ").getOrElse("-10"),
        providers.gradleProperty("maxChunkX").getOrElse("10"),
        providers.gradleProperty("maxChunkZ").getOrElse("10"),
        providers.gradleProperty("nameFilter").getOrElse("Monument"),
        providers.gradleProperty("locateKey").getOrElse("minecraft:monument")
    )
}

tasks.register<JavaExec>("strongholdVerify") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.StructureVerify"
    jvmArgs("-Xmx4G")
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-stronghold/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("minChunkX").getOrElse("-10"),
        providers.gradleProperty("minChunkZ").getOrElse("-10"),
        providers.gradleProperty("maxChunkX").getOrElse("10"),
        providers.gradleProperty("maxChunkZ").getOrElse("10"),
        providers.gradleProperty("nameFilter").getOrElse("Stronghold"),
        providers.gradleProperty("locateKey").getOrElse("minecraft:stronghold")
    )
}

tasks.register<JavaExec>("strongholdPieceDump") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.StrongholdPieceDump"
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-stronghold/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("chunkX").getOrElse("-70"),
        providers.gradleProperty("chunkZ").getOrElse("-100")
    )
}

tasks.register<JavaExec>("strongholdOurPieceDump") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.StrongholdOurPieceDump"
    args(
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("chunkX").getOrElse("-70"),
        providers.gradleProperty("chunkZ").getOrElse("-100")
    )
}

tasks.register<JavaExec>("mansionVerify") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.MansionVerify"
    jvmArgs("-Xmx4G")
    systemProperty("mansion.diffFilter", providers.gradleProperty("diffFilter").getOrElse(""))
    systemProperty("mansion.diffLimit", providers.gradleProperty("diffLimit").getOrElse("60"))
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-mansion/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("minChunkX").getOrElse("25"),
        providers.gradleProperty("minChunkZ").getOrElse("8"),
        providers.gradleProperty("maxChunkX").getOrElse("55"),
        providers.gradleProperty("maxChunkZ").getOrElse("38")
    )
}

tasks.register<JavaExec>("mansionPieceDump") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.structure.mansion.MansionPieceDump"
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-mansion/world/dimensions/minecraft/overworld"),
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("chunkX").getOrElse("39"),
        providers.gradleProperty("chunkZ").getOrElse("21")
    )
}

tasks.register<JavaExec>("endCityScan") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.EndCityScan"
    jvmArgs("-Xmx4G")
    args(
        providers.gradleProperty("vanillaWorld").getOrElse("data/vanilla-r64/end/world/dimensions/minecraft/the_end"),
        providers.gradleProperty("minChunkX").getOrElse("-10"),
        providers.gradleProperty("minChunkZ").getOrElse("-10"),
        providers.gradleProperty("maxChunkX").getOrElse("10"),
        providers.gradleProperty("maxChunkZ").getOrElse("10"),
        providers.gradleProperty("nameFilter").getOrElse("end_city"),
        providers.gradleProperty("dumpChildrenFor").getOrElse("")
    )
}

tasks.register<JavaExec>("endCityOurPieceDump") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.EndCityOurPieceDump"
    args(
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("chunkX").getOrElse("63"),
        providers.gradleProperty("chunkZ").getOrElse("-14")
    )
}

tasks.register<JavaExec>("blockBoxPeek") {
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.BlockBoxPeek"
    jvmArgs("-Xmx4G")
    systemProperty("stronghold.debug", providers.gradleProperty("strongholdDebug").getOrElse(""))
    systemProperty("blockbox.perPosition", providers.gradleProperty("perPosition").getOrElse("false"))
    args(
        providers.gradleProperty("datapack").getOrElse("data/mc/datapack"),
        providers.gradleProperty("seed").getOrElse("123456789"),
        providers.gradleProperty("minX").getOrElse("0"),
        providers.gradleProperty("minY").getOrElse("0"),
        providers.gradleProperty("minZ").getOrElse("0"),
        providers.gradleProperty("maxX").getOrElse("0"),
        providers.gradleProperty("maxY").getOrElse("0"),
        providers.gradleProperty("maxZ").getOrElse("0")
    )
}

tasks.register<JavaExec>("structureCensus") {
    group = "verification"
    description = "Runs the structure census harness's locate or verify phase (set with -Pmode=locate|verify)"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "rocks.minestom.worldgen.verify.StructureCensus"
    jvmArgs("-Xmx8G")

    val mode = providers.gradleProperty("mode").getOrElse("locate")
    val datapack = providers.gradleProperty("datapack").getOrElse("data/mc/datapack")
    val seed = providers.gradleProperty("seed").getOrElse("123456789")
    val types = providers.gradleProperty("types").getOrElse("all")

    if (mode == "verify") {
        args(
            "verify",
            datapack,
            seed,
            providers.gradleProperty("manifest").getOrElse("data/census/manifest.json"),
            providers.gradleProperty("vanillaOverworld").getOrElse("data/vanilla-census/world/dimensions/minecraft/overworld"),
            providers.gradleProperty("vanillaNether").getOrElse("data/vanilla-census/world/dimensions/minecraft/the_nether"),
            providers.gradleProperty("vanillaEnd").getOrElse("data/vanilla-census/world/dimensions/minecraft/the_end")
        )
    } else {
        args(
            "locate",
            datapack,
            seed,
            providers.gradleProperty("plan").getOrElse("data/census/plan.txt"),
            providers.gradleProperty("manifest").getOrElse("data/census/manifest.json"),
            providers.gradleProperty("instances").getOrElse("2"),
            types
        )
    }
}
