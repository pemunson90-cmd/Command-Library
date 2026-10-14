plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

kotlin {
    jvmToolchain(17)
    explicitApi()
}

dependencies {
    api(project(":core:model"))
    api(libs.coroutines.core)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.kotlin.csv.compatibility)
}

tasks.test { useJUnit() }
