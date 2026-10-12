plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.architectpsg.commandlibrary.feature.catalog"
    compileSdk = 37
    defaultConfig { minSdk = 23 }
    lint { fatal += "NewApi" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    implementation(libs.gson)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(project(":data:storage"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
