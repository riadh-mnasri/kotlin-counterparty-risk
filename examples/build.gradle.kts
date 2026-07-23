plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
    application
}

dependencies {
    implementation(project(":"))
}

application {
    mainClass.set("io.github.riadhmnasri.counterpartyrisk.examples.RunSampleRiskReportKt")
}

kotlin {
    jvmToolchain(17)
}
