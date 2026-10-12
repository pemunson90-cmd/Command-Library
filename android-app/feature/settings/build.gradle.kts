plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.architectpsg.commandlibrary.feature.settings"
    compileSdk = 37

    defaultConfig {
        minSdk = 23
    }

    lint {
        fatal += "NewApi"
    }

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
    api(project(":core:common"))
    api(libs.coroutines.core)
    implementation(libs.datastore.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
