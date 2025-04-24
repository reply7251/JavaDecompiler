import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.0"
}

group = "me.hellrevenger.javadecompiler"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.bitbucket.mstrobel:procyon-core:0.6.0")
    implementation("org.bitbucket.mstrobel:procyon-compilertools:0.6.0")
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("org.ow2.asm:asm:9.7.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<Jar> {
    configurations.implementation.get().isCanBeResolved = true
    from(configurations.implementation.get().files.map {
        if(it.isDirectory()) it else zipTree(it)
    }) {}
    manifest {
        attributes["Main-Class"] = "me.hellrevenger.javadecompiler.MainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}