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
        versionCode = 15
        versionName = "1.9.1"
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
    // Authenticated, same-device transport for the fixed BYD wallpaper repair.
    implementation("dev.mobile:dadb:1.2.8") {
        // This release accidentally publishes its native-test harness as a runtime
        // dependency; dadb's Android transport does not use it.
        exclude(group = "org.graalvm.buildtools", module = "junit-platform-native")
    }
    testImplementation("junit:junit:4.13.2")
    // Android's org.json classes are stubs in local JVM tests; use the reference
    // implementation only on the test classpath to exercise release parsing.
    testImplementation("org.json:json:20260814")
}
