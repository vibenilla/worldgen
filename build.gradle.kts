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

