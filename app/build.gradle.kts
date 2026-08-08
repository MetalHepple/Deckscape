plugins {
    id("com.android.application")
}

android {
    namespace = "uk.darkbyte.deckscape"
    compileSdk = 36

    defaultConfig {
        applicationId = "uk.darkbyte.deckscape"
        minSdk = 28
        targetSdk = 36
        versionCode = 5
        versionName = "1.3.0"
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        disable += listOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
        )
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
