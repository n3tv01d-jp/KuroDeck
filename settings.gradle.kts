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
    }
}

rootProject.name = "KuroDeck"

include(":app")
include(":flags")
include(":stubs")
listOf("iconloader", "animation", "msdl", "usertype", "dynamiccolors", "plugincore",
    "widgetpicker", "appfunctions", "protos", "stockres").forEach {
    include(":libs:$it")
}
