import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    `java-library`
    `maven-publish`
}

description = "World generation for Minestom"
group = "rocks.minestom"

val minestomVersion = "2026.08.28-26.2"
val mcVersion = minestomVersion.substringAfter("-")
val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
version = "$date-$mcVersion"

java.toolchain.languageVersion = JavaLanguageVersion.of(25)

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

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
                        id = "mudkjp"
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
    }

    repositories {
        maven {
            name = "skylite"
            url = uri("https://maven.skylite.gg/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.minestom:minestom:$minestomVersion")
    compileOnly("org.slf4j:slf4j-api:2.0.17")

    testImplementation("net.minestom:minestom:$minestomVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
}

tasks.test {
    useJUnitPlatform()
}
