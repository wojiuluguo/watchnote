plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

rootProject.layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("out/root"))
subprojects {
    layout.buildDirectory.set(rootProject.layout.projectDirectory.dir("out/" + name))
}
