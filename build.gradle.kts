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
    implementation("org.bitbucket.mstrobel:procyon-core:0.6.0")
    implementation("org.bitbucket.mstrobel:procyon-compilertools:0.6.0")
    implementation("com.formdev:flatlaf:3.5.4")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.withType<Jar> {
    configurations.implementation.get().isCanBeResolved = true
    from(configurations.implementation.get().files.map {
        if(it.isDirectory()) it else zipTree(it)
    }) {}
    manifest {
        attributes["Main-Class"] = "me.hellrevenger.javadecompiler.MainKt"
    }
}