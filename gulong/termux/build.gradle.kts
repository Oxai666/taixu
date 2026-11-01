plugins {
    alias(libs.plugins.taixu.android.library)
}

android {
    namespace = "top.wkbin.taixu.gulong.termux"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
