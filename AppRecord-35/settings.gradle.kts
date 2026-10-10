pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "DuoRecord"
include(":app", ":domain", ":contracts", ":ui-components")
val disabledFeatures=providers.gradleProperty("disabledFeatures").orNull.orEmpty().split(',').map { it.trim() }.toSet()
listOf("data", "capture", "player", "editor").filter { file(it).isDirectory && it !in disabledFeatures }.forEach { include(":$it") }
