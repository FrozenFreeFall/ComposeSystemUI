import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.example.simplesystemui"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.simplesystemui"
        minSdk = 26
        targetSdk = 36
        versionCode = 20000
        versionName = "2.0.0"
    }
    // Only the system flavor may use the device's platform certificate.
    val platformFile = rootProject.file("platform-signing.properties")
    val platformSigning = if (platformFile.exists()) {
        val values = Properties().apply { platformFile.inputStream().use { load(it) } }
        signingConfigs.create("platform") {
            storeFile = rootProject.file(values.getProperty("storeFile"))
            storePassword = values.getProperty("storePassword")
            keyAlias = values.getProperty("keyAlias")
            keyPassword = values.getProperty("keyPassword")
        }
    } else {
        null
    }
    // Build-type signing takes priority over flavor signing. Disable the default
    // here so an unsigned system build never accidentally uses a debug key.
    buildTypes.getByName("debug").signingConfig = null
    flavorDimensions += "runtime"
    productFlavors {
        create("system") {
            dimension = "runtime"
            minSdk = 36
            versionNameSuffix = "-system"
            buildConfigField("boolean", "SYSTEM_UI", "true")
            signingConfig = platformSigning
        }
        create("demo") {
            dimension = "runtime"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("boolean", "SYSTEM_UI", "false")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

}
dependencies {
    // Compile signatures only: the system flavor uses the real platform classes at runtime.
    add("systemCompileOnly", project(":hidden-api-stubs"))
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
