plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.architectpsg.commandlibrary.data.network"
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
kotlin { jvmToolchain(17); explicitApi() }
dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okio)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(project(":feature:catalog"))
    androidTestImplementation(project(":feature:settings"))
    androidTestImplementation(project(":data:storage"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
