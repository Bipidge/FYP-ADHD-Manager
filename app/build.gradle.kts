import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.gms.google.services)
}

// Function to load local properties safely
fun getApiKey(propertyKey: String, projectRootDir: File): String {
    val propertiesFile = File(projectRootDir, "local.properties")
    if (propertiesFile.exists()) {
        try {
            val properties = Properties()
            properties.load(FileInputStream(propertiesFile))
            return properties.getProperty(propertyKey, "") // Return empty if not found
        } catch (e: Exception) {
            println("Warning: Could not read local.properties: ${e.message}")
        }
    } else {
        println("Warning: local.properties file not found.")
    }
    return "" // Return empty if file not found or error
}

android {
    namespace = "com.example.fyp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.fyp"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Make API key available in BuildConfig
        val googleAiApiKey = getApiKey("GOOGLE_AI_API_KEY", rootDir) // Use new property name
        if (googleAiApiKey.isEmpty()) {
            println("Warning: GOOGLE_AI_API_KEY not found. AI features will fail.")
            // Optionally throw error: throw GradleException(...)
        }
        // Use a distinct BuildConfig field name
        buildConfigField("String", "GOOGLE_AI_API_KEY", "\"$googleAiApiKey\"")
    }

    buildFeatures { // Required for BuildConfig
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.firebase.auth)


    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:33.0.0"))
    implementation("com.google.android.gms:play-services-auth:21.1.1")
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")

    // Volley for network requests
    implementation("com.android.volley:volley:1.2.1")
}