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
