plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("jacoco")
}

jacoco {
    toolVersion = "0.8.12"
}

// Make all unit tests produce JaCoCo data reliably
tasks.withType<Test>().configureEach {
    extensions.configure(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class) {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.withType<Test>())

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    classDirectories.setFrom(
        files(
            fileTree("$buildDir/tmp/kotlin-classes/debug") {
                exclude("**/R.class","**/R$*.class","**/BuildConfig.*","**/Manifest*.*","**/*Test*.*","androidx/**")
            },
            fileTree("$buildDir/intermediates/javac/debug/classes") {
                exclude("**/R.class","**/R$*.class","**/BuildConfig.*","**/Manifest*.*","**/*Test*.*","androidx/**")
            }
        )
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

    executionData.setFrom(
        fileTree(buildDir) { include("**/*.exec", "**/*.ec") }
    )
}

android {
    val minSdkVersion: String by rootProject
    val compileSdkVersion: String by rootProject
    val targetSdkVersion: String by rootProject
    val playerKey: String by rootProject

    namespace = "com.bitmovin.player.integration.nielsen.mediametrie"
    compileSdk = compileSdkVersion.toInt()

    defaultConfig {
        applicationId = "com.bitmovin.player.integration.nielsen.nielsensample"
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Expose playerKey from gradle.properties
        buildConfigField("String", "PLAYER_KEY", "\"$playerKey\"")
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
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
}

dependencies {
    implementation(libs.bitmovin.player)
    implementation("com.google.ads.interactivemedia.v3:interactivemedia:3.35.1")
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(project(":nielsen-mediametrie-sdk"))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation("org.json:json:20230227")
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    testImplementation("org.robolectric:robolectric:4.12.2")
}