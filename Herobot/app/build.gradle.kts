plugins {
    id("com.android.application")
}

android {
    namespace = "com.herobot"
    compileSdk = 34
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        warningsAsErrors = false
    }
    defaultConfig {
        applicationId = "com.herobot"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    
signingConfigs {
    create("release") {
        storeFile = file("herobot-release.keystore")
        storePassword = "774954hbkckpbgt"
        keyAlias = "herobot"
        keyPassword = "774954hbkckpbgt"
    }
}

buildTypes {
    getByName("release") {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("release")

        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro"
        )
    }
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("org.apache.commons:commons-math3:3.6.1")
    implementation("org.json:json:20240303")
}
}