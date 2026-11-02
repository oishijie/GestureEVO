pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        // jitpack 上的构件（BRV / StateLayout）在镜像站只有元数据、缺 AAR，
        // 这里对 com.github.* 做排他解析，强制回到 jitpack 取包。
        exclusiveContent {
            forRepository { maven(url = "https://jitpack.io") }
            filter {
                includeGroup("com.github.liangjingkanji")
                includeGroup("com.github.thegrizzlylabs")
            }
        }
    }
}

rootProject.name = "GestureEVO"
include(":app")
