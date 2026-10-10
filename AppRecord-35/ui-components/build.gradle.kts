plugins { id("com.android.library"); kotlin("android") }
android { namespace="com.duorecord.components"; compileSdk=35; defaultConfig { minSdk=30 }; compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 } }
kotlin { jvmToolchain(17) }
dependencies { api(project(":domain")) }
