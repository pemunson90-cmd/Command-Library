plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.architectpsg.commandlibrary.data.vault"
    compileSdk = 37
    defaultConfig {
        minSdk = 23
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    lint { fatal += "NewApi" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { jvmToolchain(17); explicitApi() }
dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
