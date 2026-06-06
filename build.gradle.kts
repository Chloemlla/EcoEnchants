import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.0"
    id("java")
    id("java-library")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.3.1"
    id("com.willfp.libreforge-gradle-plugin") version "2.0.0"
}

group = "com.willfp"
version = findProperty("version")!!
val libreforgeVersion = findProperty("libreforge-version")
val ecoVersion = findProperty("eco-version")
val vineflowerVersion = findProperty("vineflower-version") ?: "1.12.0"

val embeddedLibreforge by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val decompiler by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = true
}

base {
    archivesName.set(project.name)
}

dependencies {
    implementation(project(":eco-core:core-plugin"))
    implementation(project(":eco-core:core-nms:v1_21_8", configuration = "reobf"))
    implementation(project(":eco-core:core-nms:v1_21_10", configuration = "reobf"))
    implementation(project(":eco-core:core-nms:v1_21_11", configuration = "reobf"))
    implementation(project(":eco-core:core-nms:v26_1_1", configuration = "shadow"))
    implementation(project(":eco-core:core-nms:v26_1_2", configuration = "shadow"))

    embeddedLibreforge("com.willfp:libreforge:${libreforgeVersion!!}:shadow@jar")
    decompiler("org.vineflower:vineflower:$vineflowerVersion")
}

tasks {
    shadowJar {
        from(embeddedLibreforge) {
            rename { "libreforge-$libreforgeVersion-shadow.jar" }
        }
    }

    val nativeServerClassGlobs = listOf(
        "com/destroystokyo/**",
        "com/mojang/**",
        "io/papermc/**",
        "net/minecraft/**",
        "org/bukkit/**",
        "org/spigotmc/**"
    )

    val pluginDecompileClasses = layout.buildDirectory.dir("decompile/input/plugin-classes")

    val preparePluginDecompileClasses by registering(Sync::class) {
        group = "decompilation"
        description = "Copies only EcoEnchants plugin classes from the shaded jar for isolated decompilation."

        dependsOn(shadowJar)

        from(shadowJar.flatMap { it.archiveFile }.map { zipTree(it) }) {
            include("com/willfp/ecoenchants/**")
            exclude("com/willfp/ecoenchants/libreforge/loader/**")
            exclude(nativeServerClassGlobs)
            includeEmptyDirs = false
        }

        into(pluginDecompileClasses)

        doLast {
            val nativeClasses = fileTree(pluginDecompileClasses.get().asFile).matching {
                include(nativeServerClassGlobs)
            }.files

            check(nativeClasses.isEmpty()) {
                "Native server classes were copied into the decompile input."
            }
        }
    }

    register<JavaExec>("decompilePlugin") {
        group = "decompilation"
        description = "Decompiles EcoEnchants plugin classes into build/decompiled/plugin without touching source files."

        dependsOn(preparePluginDecompileClasses)

        classpath = decompiler
        mainClass.set("org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler")
        jvmArgs("-Xmx1g")

        val outputDir = layout.buildDirectory.dir("decompiled/plugin")

        inputs.dir(pluginDecompileClasses)
        outputs.dir(outputDir)

        doFirst {
            delete(outputDir)
            outputDir.get().asFile.mkdirs()
            args(
                "-dgs=1",
                "-asc=1",
                "-rsy=1",
                "-log=WARN",
                pluginDecompileClasses.get().asFile.absolutePath,
                outputDir.get().asFile.absolutePath
            )
        }
    }
}

allprojects {
    apply(plugin = "java")
    apply(plugin = "kotlin")
    apply(plugin = "maven-publish")
    apply(plugin = "com.gradleup.shadow")

    repositories {
        mavenLocal()
        mavenCentral()

        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.auxilor.io/repository/maven-public/")
        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
        maven("https://repo.codemc.org/repository/nms/")
        maven("https://repo.essentialsx.net/releases/")
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\..*") }
        }
    }

    dependencies {
        compileOnly("com.willfp:eco:$ecoVersion")
        compileOnly("org.jetbrains:annotations:26.0.2")
        compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
        compileOnly("com.github.ben-manes.caffeine:caffeine:3.2.3")
    }

    tasks {
        shadowJar {
            exclude("META-INF/**")
            relocate("com.willfp.libreforge.loader", "com.willfp.ecoenchants.libreforge.loader")
            relocate("kotlin", "com.willfp.eco.libs.kotlin")
            relocate("kotlin.jvm", "com.willfp.eco.libs.kotlin.jvm")
            relocate("kotlin.coroutines", "com.willfp.eco.libs.kotlin.coroutines")
            relocate("kotlin.reflect", "com.willfp.eco.libs.kotlin.reflect")
        }

        compileKotlin {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
            }
        }

        compileJava {
            options.isDeprecation = true
            options.encoding = "UTF-8"

            dependsOn(clean)
        }

        processResources {
            filesMatching(listOf("**plugin.yml", "**eco.yml")) {
                expand(
                    "version" to project.version,
                    "libreforgeVersion" to libreforgeVersion!!,
                    "pluginName" to rootProject.name
                )
            }
        }

        build {
            dependsOn(shadowJar)
        }

        withType<JavaCompile>().configureEach {
            options.release = 21
        }
    }

    java {
        withSourcesJar()
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}
