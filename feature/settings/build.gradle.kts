plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.settings"
}

// Native Compose render fixtures own separate frame clocks and modal windows.
tasks.withType<Test>().configureEach { forkEvery = 1 }

dependencies {
    implementation(project(":plugin:ui"))
    implementation(project(":feature:theme"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(project(":runtime"))
    // 古龙 · Termux 通道（设置页读写 TermuxRuntime / TermuxClient）
    implementation(project(":gulong:termux"))
    implementation(project(":tools"))
    implementation(project(":harness"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coil)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.test.robolectric)
}
