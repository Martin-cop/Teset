plugins { id("com.android.library"); kotlin("android") }
android { namespace="com.duorecord.editor"; compileSdk=35; defaultConfig { minSdk=30 }; compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 } }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":contracts")); implementation("androidx.media3:media3-transformer:1.5.1") }
