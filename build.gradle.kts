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

// useGradleVersions=true (set by release workflows) pins dependencies to the
// versions in gradle.properties; otherwise dev builds track the latest master snapshot.
val useGradleVersions = findProperty("useGradleVersions") == "true"
val libreforgeVersion = if (useGradleVersions) findProperty("libreforge-version") else "dev-SNAPSHOT"
val ecoVersion = if (useGradleVersions) findProperty("eco-version") else "dev-SNAPSHOT"
val proguardVersion = findProperty("proguard-version") ?: "7.9.1"
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

val obfuscator by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = true
}

val obfuscationLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

base {
    archivesName.set(project.name)
}

dependencies {
    implementation(project(":eco-core:core-plugin"))
    implementation(project(":eco-core:core-nms:v1_21_8", "reobf"))
    implementation(project(":eco-core:core-nms:v1_21_10", "reobf"))
    implementation(project(":eco-core:core-nms:v1_21_11", "reobf"))
    implementation(project(":eco-core:core-nms:v26_1_1", "shadow"))
    implementation(project(":eco-core:core-nms:v26_1_2", "shadow"))
    implementation(project(":eco-core:core-nms:v26_2", "shadow"))

    embeddedLibreforge("com.willfp:libreforge:${libreforgeVersion!!}:shadow@jar")
    decompiler("org.vineflower:vineflower:$vineflowerVersion")
    obfuscator("com.guardsquare:proguard-base:$proguardVersion")

    obfuscationLibraries(fileTree("lib") {
        include("*.jar")
    })
    obfuscationLibraries("com.willfp:eco:$ecoVersion")
    obfuscationLibraries("com.willfp:libreforge:${libreforgeVersion!!}:shadow@jar")
    obfuscationLibraries("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    obfuscationLibraries("net.essentialsx:EssentialsX:2.19.7") {
        exclude("*", "*")
    }
    obfuscationLibraries("org.jetbrains:annotations:26.0.2")
    obfuscationLibraries("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    obfuscationLibraries("com.github.ben-manes.caffeine:caffeine:3.2.3")
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
    val proguardRules = layout.projectDirectory.file("proguard-rules.pro")
    val proguardConfig = layout.buildDirectory.file("tmp/proguard/ecoenchants.pro")
    val obfuscatedPluginJar = layout.buildDirectory.file("libs/${base.archivesName.get()}-${project.version}-obfuscated.jar")

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

    val obfuscatePlugin by registering(JavaExec::class) {
        group = "obfuscation"
        description = "Obfuscates the final plugin jar into build/libs without rewriting source files."

        dependsOn(shadowJar)

        classpath = obfuscator
        mainClass.set("proguard.ProGuard")
        jvmArgs("-Xmx2g")

        inputs.file(shadowJar.flatMap { it.archiveFile })
        inputs.file(proguardRules)
        inputs.files(obfuscationLibraries)
        outputs.file(obfuscatedPluginJar)

        doFirst {
            fun File.proguardPath(): String = "'${absolutePath.replace("\\", "/")}'"

            val inputJar = shadowJar.get().archiveFile.get().asFile
            val outputJar = obfuscatedPluginJar.get().asFile
            val configFile = proguardConfig.get().asFile
            val nativeFilter = nativeServerClassGlobs.joinToString(",") { "!$it" }
            val jmods = File(System.getProperty("java.home"), "jmods")
            val javaLibraries = jmods
                .listFiles { file -> file.extension == "jmod" }
                ?.sortedBy { it.name }
                .orEmpty()
                .joinToString(System.lineSeparator()) {
                    "-libraryjars ${it.proguardPath()}(!**.jar;!module-info.class;!classes/module-info.class)"
                }
            val dependencyLibraries = obfuscationLibraries.files
                .filter { it.isFile }
                .distinctBy { it.absolutePath }
                .sortedBy { it.name }
                .joinToString(System.lineSeparator()) {
                    "-libraryjars ${it.proguardPath()}(!META-INF/versions/**;!module-info.class)"
                }

            delete(outputJar)
            outputJar.parentFile.mkdirs()
            configFile.parentFile.mkdirs()
            configFile.writeText(
                """
                -injars ${inputJar.proguardPath()}($nativeFilter)
                -outjars ${outputJar.proguardPath()}
                $javaLibraries
                $dependencyLibraries
                -include ${proguardRules.asFile.proguardPath()}
                """.trimIndent()
            )

            setArgs(listOf("@${configFile.absolutePath}"))
        }

        doLast {
            val nativeClasses = zipTree(obfuscatedPluginJar.get().asFile).matching {
                include(nativeServerClassGlobs)
            }.files

            check(nativeClasses.isEmpty()) {
                "Native server classes were copied into the obfuscated plugin jar."
            }
        }
    }

    build {
        dependsOn(obfuscatePlugin)
    }
}

java {
    withJavadocJar()
}

publishing {
    publications {
        // maven-private: only the shaded jar
        create<MavenPublication>("private") {
            artifactId = rootProject.name
        }
        // maven-releases + GitHub: full set (none, all, sources, javadoc)
        create<MavenPublication>("release") {
            artifactId = rootProject.name
            from(components["java"])
        }
    }
    repositories {
        maven {
            name = "Auxilor"
            url = uri("https://repo.auxilor.io/repository/maven-private/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
        maven {
            name = "AuxilorReleases"
            url = uri("https://repo.auxilor.io/repository/maven-releases/")
            credentials {
                username = System.getenv("MAVEN_USERNAME")
                password = System.getenv("MAVEN_PASSWORD")
            }
        }
    }
}

afterEvaluate {
    publishing.publications.named<MavenPublication>("private") {
        artifact(tasks.named("libreforgeJar"))
    }
}

tasks.matching { it.name.startsWith("generatePomFileFor") }.configureEach {
    mustRunAfter(tasks.named("clean"))
}
tasks.register("publishToAuxilor") {
    dependsOn(
        "publishPrivatePublicationToAuxilorRepository",
        "publishReleasePublicationToAuxilorReleasesRepository",
    )
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
