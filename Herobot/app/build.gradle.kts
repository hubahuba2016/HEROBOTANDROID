plugins {
    id("com.android.application")
}

android {
    namespace = "com.herobot"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.herobot"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
    checkReleaseBuilds = false
    abortOnError = false
}
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.json:json:20240303")
}