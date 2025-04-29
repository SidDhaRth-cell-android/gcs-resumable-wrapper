import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("maven-publish")
}

android {
    namespace = "com.flutteroid.gcsresummableuploadwrapper"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        buildConfigField("String", "BASE_URL", "\"https://www.googleapis.com/\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

val localProperties = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Retrofit
    api(libs.retrofit)
    api(libs.converter.gson)

    // OkHttps
    api(libs.logging.interceptor)
    api(libs.okhttp)
}

publishing {
    publications {
        create<MavenPublication>("bar") {
            groupId = "com.flutteroid"
            artifactId = "gcsresummableuploadwrapper"
            version = "1.0.0"
            artifact("${buildDir}/outputs/aar/GCSResummableUploadWrapper-release.aar")

            pom.withXml {
                val dependenciesNode = asNode().appendNode("dependencies")
                configurations.getByName("api").dependencies.forEach { dependency ->
                    if (dependency.group != null && dependency.version != null) {
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", dependency.group)
                        dependencyNode.appendNode("artifactId", dependency.name)
                        dependencyNode.appendNode("version", dependency.version)
                        dependencyNode.appendNode("scope", "compile") // use "compile" or "runtime" instead of "aar"
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "GithubPackages"
            url = uri("https://maven.pkg.github.com/SidDhaRth-cell-android/gcs-resumable-wrapper")
            credentials {
                username = localProperties.getProperty("lpr.user") ?: System.getenv("GITHUB_USER")
                password = localProperties.getProperty("lpr.key") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}