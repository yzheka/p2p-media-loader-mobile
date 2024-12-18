plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    id("org.jlleitschuh.gradle.ktlint")
    id("maven-publish")
}

android {
    namespace = "com.novage.p2pml"
    compileSdk = 35

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    compileOnly(libs.androidx.media3.exoplayer.hls)
    compileOnly(libs.androidx.media3.exoplayer)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.cors)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.core.ktx)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId = "com.novage.p2pml"
                artifactId = "p2pml"
                version = "0.0.1-SNAPSHOT"

                pom {
                    name.set("p2pml")
                    description.set("A sample Kotlin/Android library for P2P media streaming")
                    url.set("https://github.com/DimaDemchenko/p2pml-kotlin")
                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }
                    scm {
                        connection.set("scm:git:git://github.com/DimaDemchenko/p2pml-kotlin.git")
                        developerConnection.set("scm:git:ssh://github.com:DimaDemchenko/p2pml-kotlin.git")
                        url.set("https://github.com/DimaDemchenko/p2pml-kotlin")
                    }
                    developers {
                        developer {
                            id.set("DimaDemchenko")
                            name.set("Dmytro Demchenko")
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/DimaDemchenko/p2pml-kotlin")
                credentials {
                    username = System.getenv("GITHUB_ACTOR") ?: ""
                    password = System.getenv("GITHUB_TOKEN") ?: ""
                }
            }
        }
    }
}
