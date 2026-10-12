plugins {
    alias(libs.plugins.android.library)
}
android {
    namespace = "com.architectpsg.commandlibrary.data.network"
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    lint { fatal += "NewApi" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17); explicitApi() }
dependencies {
    api(project(":core:model"))
    implementation(libs.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okio)
    implementation(libs.okhttp)
    implementation(libs.okio)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
