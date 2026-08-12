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
        versionCode = 10
        versionName = "1.7.1"
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
            "OldTargetApi",
        )
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    // Android's org.json classes are stubs in local JVM tests; use the reference
    // implementation only on the test classpath to exercise release parsing.
    testImplementation("org.json:json:20260522")
}
