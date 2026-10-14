import java.util.Properties

plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val localProperties = Properties().apply {
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) localFile.inputStream().use { load(it) }
}
val sdkDirectory = localProperties.getProperty("sdk.dir")
    ?: System.getenv("ANDROID_HOME")
    ?: System.getenv("ANDROID_SDK_ROOT")
    ?: error("Set sdk.dir in local.properties or ANDROID_HOME/ANDROID_SDK_ROOT")

dependencies {
    compileOnly(files("$sdkDirectory/platforms/android-36/android.jar"))
}
