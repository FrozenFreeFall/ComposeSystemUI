pluginManagement {
    repositories {
        // 阿里云镜像
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        // 华为开源镜像
        maven("https://repo.huaweicloud.com/repository/maven/")
        // JitPack远程镜像
        maven("https://www.jitpack.io")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        // 华为开源镜像
        maven("https://repo.huaweicloud.com/repository/maven/")
        // JitPack远程镜像
        maven("https://www.jitpack.io")
        google()
        mavenCentral()
    }
}
rootProject.name = "SimpleSystemUI"
include(":app")
include(":hidden-api-stubs")
