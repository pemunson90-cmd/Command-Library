import java.util.Locale

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "com.architectpsg.commandlibrary.data.storage"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("test").kotlin.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/kotlin")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

// Android's driver loads sqliteJni by name. JVM unit tests need the host binary, not the APK's ABI.
val hostSqliteNative = configurations.create("hostSqliteNative") {
    isCanBeConsumed = false
    isTransitive = false
}
val hostSqliteDirectory = layout.buildDirectory.dir("host-sqlite-native")
val prepareHostSqliteNative = tasks.register<Sync>("prepareHostSqliteNative") {
    val hostOs = System.getProperty("os.name").lowercase(Locale.ROOT)
    val hostArch = System.getProperty("os.arch").lowercase(Locale.ROOT)
    val platform = when {
        hostOs.contains("linux") && hostArch in setOf("amd64", "x86_64") -> "linux_x64"
        hostOs.contains("linux") && hostArch in setOf("aarch64", "arm64") -> "linux_arm64"
        hostOs.contains("mac") && hostArch in setOf("aarch64", "arm64") -> "osx_arm64"
        hostOs.contains("windows") && hostArch in setOf("amd64", "x86_64") -> "windows_x64"
        else -> error("Bundled SQLite has no JVM native for $hostOs/$hostArch")
    }
    from(hostSqliteNative.map { zipTree(it) }) {
        include("natives/$platform/*")
        eachFile { relativePath = org.gradle.api.file.RelativePath(true, name) }
        includeEmptyDirs = false
    }
    into(hostSqliteDirectory)
}
tasks.withType<Test>().configureEach {
    dependsOn(prepareHostSqliteNative)
    systemProperty("java.library.path", hostSqliteDirectory.get().asFile.absolutePath)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    implementation(libs.room3.runtime)
    implementation(libs.sqlite.bundled)
    ksp(libs.room3.compiler)

    // Integration tests consume the pure JVM repository; production registry/DAG is unchanged.
    testImplementation(project(":feature:ai"))
    testImplementation(project(":feature:decks"))
    testImplementation(project(":feature:collection"))
    testImplementation(project(":feature:life"))
    androidTestImplementation(project(":feature:life"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    add(hostSqliteNative.name, libs.sqlite.bundled.jvm)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.test)
}
