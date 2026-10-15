plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}
kotlin { jvmToolchain(17); explicitApi() }
dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
tasks.test { useJUnit() }
