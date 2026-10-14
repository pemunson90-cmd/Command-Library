plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.architectpsg.commandlibrary"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.architectpsg.commandlibrary"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-k2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        fatal += "NewApi"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":data:storage"))
    implementation(project(":feature:catalog"))
    implementation(project(":feature:rules"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:shell"))
    implementation(libs.coroutines.core)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(project(":feature:life"))
    androidTestImplementation(project(":core:common"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.coroutines.test)

    debugImplementation(libs.compose.ui.test.manifest)
}
