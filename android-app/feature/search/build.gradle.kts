plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}
kotlin { jvmToolchain(17); explicitApi() }
dependencies {
    api(project(":core:model"))
    testImplementation(libs.junit)
}
tasks.test { useJUnit() }
