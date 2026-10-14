plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.architectpsg.commandlibrary.feature.catalog"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    lint { fatal += "NewApi" }
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

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.sqlite.bundled.jvm)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(project(":data:storage"))
    androidTestImplementation(project(":feature:settings"))
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
