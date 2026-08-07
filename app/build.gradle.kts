plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.santiagorodriguez.countaway"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.santiagorodriguez.countaway"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    androidResources {
        generateLocaleConfig = true
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
