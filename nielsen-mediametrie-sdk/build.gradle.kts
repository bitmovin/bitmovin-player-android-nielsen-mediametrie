plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id("jacoco")
    id("maven-publish")
}

// Make all unit tests produce JaCoCo data reliably
tasks.withType<Test>().configureEach {
    extensions.configure(org.gradle.testing.jacoco.plugins.JacocoTaskExtension::class) {
        version = "0.8.12"
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
    namespace = "com.bitmovin.player.integration.nielsen.mediametrie"

    val minSdkVersion: String by rootProject
    val compileSdkVersion: String by rootProject
    val targetSdkVersion: String by rootProject

    compileSdk = compileSdkVersion.toInt()

    defaultConfig {
        minSdk = minSdkVersion.toInt()
        targetSdk = targetSdkVersion.toInt()

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
}

dependencies {
    // library's logic
    implementation(libs.bitmovin.player)
    implementation("com.google.ads.interactivemedia.v3:interactivemedia:3.35.1")
    implementation("com.google.android.gms:play-services-ads-identifier:18.2.0")
    api("com.nielsenappsdk:global:10.0.0.0")
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.junit.ktx)

    // Test dependencies
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.junit4)
    testImplementation("org.json:json:20230227")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("org.robolectric:shadows-framework:4.12.2")
    testImplementation("org.junit.platform:junit-platform-runner")
}


// Maven publishing configuration
afterEvaluate {

    extensions.configure<PublishingExtension>("publishing") {
        publications {
            create<MavenPublication>("release") {
                artifact(file("build/outputs/aar/nielsen-mediametrie-sdk-release.aar")) {
                }

                groupId = rootProject.extra["groupId"].toString()
                artifactId = "nielsen-mediametrie-sdk"
                version = rootProject.extra["versionName"].toString()
            }
        }

        repositories {
            maven {
                name = "Bitmovin"
                url = uri("https://bitmovin.jfrog.io/bitmovin/libs-release-local")
                credentials {
                    username = rootProject.findProperty("mavenUsername").toString()
                    password = rootProject.findProperty("mavenPassword").toString()
                }
            }

            // This is for local testing and development.
            maven {
                name = "localRepo"
                url = uri("${rootProject.buildDir}/maven_repo")
            }
        }
    }
}