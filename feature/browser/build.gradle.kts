plugins {
    alias(libs.plugins.taixu.android.feature)
}

android {
    namespace = "top.wkbin.taixu.feature.browser"
    resourcePrefix = "fbrowser_"
}

dependencies {
    implementation(project(":plugin:ui"))
    implementation(project(":core:browser"))
    implementation(project(":core:datastore"))
    implementation(project(":runtime:browser"))
    implementation(project(":feature:theme"))
    // 浏览器内复用 chat 的悬浮小窗（工具回执 + 思考过程都在这窗里）
    implementation(project(":feature:chat"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.bundles.coil)
}
