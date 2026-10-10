plugins { id("com.android.application"); kotlin("android") }
android {
    buildFeatures { buildConfig=true }
    namespace="com.duorecord.app";compileSdk=35
    defaultConfig { applicationId="com.duorecord.app";minSdk=30;targetSdk=35;versionCode=40;versionName="1.3.34-AppRecord-35";manifestPlaceholders["appLabel"]="AppRecord 35" }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    testOptions { unitTests.isIncludeAndroidResources=true }
    buildTypes {
        getByName("release") { isMinifyEnabled=false }
        getByName("debug") { applicationIdSuffix=".preview";versionNameSuffix="-preview";manifestPlaceholders["appLabel"]="AppRecord 35" }
    }
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation(project(":contracts"));implementation(project(":ui-components"))
    listOf("data","capture","player","editor").forEach { name -> if(findProject(":$name")!=null)implementation(project(":$name")) }
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
