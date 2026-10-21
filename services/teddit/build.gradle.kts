plugins {
    kotlin("jvm")
    id("com.google.devtools.ksp")
}

sourceSets.main {
    java.srcDirs("build/generated/ksp/main/kotlin")
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":services:base"))
    implementation(project(":services:reddit"))

    implementation("io.ktor:ktor-client-core:${Dependencies.Versions.ktor}")
    implementation("io.ktor:ktor-client-content-negotiation:${Dependencies.Versions.ktor}")
    implementation("io.ktor:ktor-client-okhttp:${Dependencies.Versions.ktor}")

    implementation("io.insert-koin:koin-ktor:${Dependencies.Versions.koin}")
    implementation("io.insert-koin:koin-annotations:${Dependencies.Versions.koinKsp}")
    ksp("io.insert-koin:koin-ksp-compiler:${Dependencies.Versions.koinKsp}")

    implementation("com.squareup.moshi:moshi:${Dependencies.Versions.moshi}")
    implementation("com.squareup.moshi:moshi-adapters:${Dependencies.Versions.moshi}")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:${Dependencies.Versions.moshi}")

    implementation("org.jsoup:jsoup:${Dependencies.Versions.jsoup}")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:${Dependencies.Versions.kotlin}")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Dependencies.Versions.coroutines}")
}
