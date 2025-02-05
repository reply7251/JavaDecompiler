plugins {
    kotlin("jvm") version "2.0.0"
}

group = "me.hellrevenger.javadecompiler"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "me.hellrevenger.javadecompiler.MainKt"
    }
}